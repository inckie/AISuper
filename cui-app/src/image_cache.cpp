#include "image_cache.hpp"
#include "applet_loader.hpp"

#include <lunasvg.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <thread>

#ifdef _WIN32
#include <windows.h>
#include <winhttp.h>
#endif

extern "C" {
uint8_t* aisuper_decode_image_rgba(const uint8_t* data, int len, int* w, int* h);
void aisuper_free_image_rgba(uint8_t* px);
}

namespace {

bool starts_with_ci(const std::string& s, const std::string& prefix) {
    if (s.size() < prefix.size()) return false;
    for (size_t i = 0; i < prefix.size(); ++i) {
        if (std::tolower(static_cast<unsigned char>(s[i])) !=
            std::tolower(static_cast<unsigned char>(prefix[i]))) {
            return false;
        }
    }
    return true;
}

bool ends_with_ci(const std::string& s, const std::string& suffix) {
    if (s.size() < suffix.size()) return false;
    size_t off = s.size() - suffix.size();
    for (size_t i = 0; i < suffix.size(); ++i) {
        if (std::tolower(static_cast<unsigned char>(s[off + i])) !=
            std::tolower(static_cast<unsigned char>(suffix[i]))) {
            return false;
        }
    }
    return true;
}

std::string color_hex_rgb(cui_color c) {
    char buf[16];
    std::snprintf(buf, sizeof(buf), "#%02x%02x%02x", c.r, c.g, c.b);
    return std::string(buf);
}

std::vector<uint8_t> decode_base64(const std::string& in) {
    static const int T[256] = {
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,62,-1,-1,-1,63,
        52,53,54,55,56,57,58,59,60,61,-1,-1,-1,-1,-1,-1,
        -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,
        15,16,17,18,19,20,21,22,23,24,25,-1,-1,-1,-1,-1,
        -1,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,
        41,42,43,44,45,46,47,48,49,50,51,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
    };

    std::vector<uint8_t> out;
    int val = 0, valb = -8;
    for (unsigned char c : in) {
        if (c == '=') break;
        int d = T[c];
        if (d == -1) continue;
        val = (val << 6) + d;
        valb += 6;
        if (valb >= 0) {
            out.push_back(static_cast<uint8_t>((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return out;
}

std::string url_decode(const std::string& in) {
    auto hex_val = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
        if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
        return -1;
    };
    std::string out;
    out.reserve(in.size());
    for (size_t i = 0; i < in.size(); ++i) {
        if (in[i] == '%' && i + 2 < in.size()) {
            int hi = hex_val(in[i + 1]);
            int lo = hex_val(in[i + 2]);
            if (hi >= 0 && lo >= 0) {
                out.push_back(static_cast<char>((hi << 4) | lo));
                i += 2;
                continue;
            }
        }
        out.push_back(in[i]);
    }
    return out;
}

bool is_svg_data_uri(const std::string& source) {
    return starts_with_ci(source, "data:image/svg+xml");
}

bool is_svg_bytes(const uint8_t* data, size_t len) {
    if (!data || len < 4) return false;
    size_t i = 0;
    if (len >= 3 && data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF) {
        i = 3;
    }
    while (i < len && (data[i] == ' ' || data[i] == '\t' || data[i] == '\r' || data[i] == '\n')) {
        ++i;
    }
    if (i >= len) return false;
    std::string head(reinterpret_cast<const char*>(data + i), std::min<size_t>(len - i, 512));
    if (starts_with_ci(head, "<svg")) return true;
    if (starts_with_ci(head, "<?xml") || starts_with_ci(head, "<!doctype") || starts_with_ci(head, "<!--")) {
        std::string lower = head;
        std::transform(lower.begin(), lower.end(), lower.begin(),
                       [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
        return lower.find("<svg") != std::string::npos;
    }
    return false;
}

bool decode_svg_to_rgba(const char* svg_data,
                        size_t svg_len,
                        std::optional<cui_color> current_color,
                        int target_max_dim,
                        std::vector<uint8_t>& out_rgba,
                        int& out_w,
                        int& out_h) {
    if (!svg_data || svg_len == 0) return false;

    auto doc = lunasvg::Document::loadFromData(svg_data, svg_len);
    if (!doc) return false;

    if (current_color.has_value()) {
        doc->applyStyleSheet("svg { color: " + color_hex_rgb(*current_color) + "; }");
    }

    float iw = doc->width();
    float ih = doc->height();
    if (iw <= 0.0f || ih <= 0.0f) {
        auto bb = doc->boundingBox();
        iw = bb.w > 0.0f ? bb.w : 64.0f;
        ih = bb.h > 0.0f ? bb.h : 64.0f;
    }

    int max_dim = std::max(16, target_max_dim);
    float max_intrinsic = std::max(iw, ih);
    float scale = (max_intrinsic > 0.0f) ? (static_cast<float>(max_dim) / max_intrinsic) : 1.0f;

    int w = std::max(1, static_cast<int>(std::lround(iw * scale)));
    int h = std::max(1, static_cast<int>(std::lround(ih * scale)));

    auto bitmap = doc->renderToBitmap(w, h, 0x00000000);
    if (bitmap.isNull() || bitmap.width() <= 0 || bitmap.height() <= 0) {
        return false;
    }
    bitmap.convertToRGBA();

    out_w = bitmap.width();
    out_h = bitmap.height();
    size_t row_bytes = static_cast<size_t>(out_w) * 4u;
    out_rgba.resize(row_bytes * static_cast<size_t>(out_h));
    const uint8_t* src = bitmap.data();
    int stride = bitmap.stride();
    for (int y = 0; y < out_h; ++y) {
        std::memcpy(out_rgba.data() + static_cast<size_t>(y) * row_bytes,
                    src + static_cast<size_t>(y) * static_cast<size_t>(stride),
                    row_bytes);
    }
    return true;
}

bool decode_any_to_rgba(const uint8_t* bytes,
                        size_t len,
                        bool hint_svg,
                        std::optional<cui_color> current_color,
                        int target_max_dim,
                        std::vector<uint8_t>& out_rgba,
                        int& out_w,
                        int& out_h) {
    if (!bytes || len == 0) return false;
    if (hint_svg || is_svg_bytes(bytes, len)) {
        return decode_svg_to_rgba(reinterpret_cast<const char*>(bytes), len,
                                  current_color, target_max_dim, out_rgba, out_w, out_h);
    }

    int w = 0, h = 0;
    uint8_t* px = aisuper_decode_image_rgba(bytes, static_cast<int>(len), &w, &h);
    if (!px || w <= 0 || h <= 0) {
        if (px) aisuper_free_image_rgba(px);
        return false;
    }
    size_t byte_count = static_cast<size_t>(w) * static_cast<size_t>(h) * 4u;
    out_rgba.assign(px, px + byte_count);
    out_w = w;
    out_h = h;
    aisuper_free_image_rgba(px);
    return true;
}

bool http_get_bytes(const std::string& url, std::vector<uint8_t>& out_bytes) {
#ifdef _WIN32
    int wlen = MultiByteToWideChar(CP_UTF8, 0, url.c_str(), -1, nullptr, 0);
    if (wlen <= 0) return false;
    std::wstring wurl(wlen - 1, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, url.c_str(), -1, wurl.data(), wlen);

    URL_COMPONENTS uc{};
    uc.dwStructSize = sizeof(uc);
    wchar_t host[256]{};
    wchar_t path[2048]{};
    uc.lpszHostName = host;
    uc.dwHostNameLength = 256;
    uc.lpszUrlPath = path;
    uc.dwUrlPathLength = 2048;

    if (!WinHttpCrackUrl(wurl.c_str(), 0, 0, &uc)) return false;

    HINTERNET hSession = WinHttpOpen(L"AISuperNative/1.0",
                                     WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                                     WINHTTP_NO_PROXY_NAME,
                                     WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) return false;

    HINTERNET hConnect = WinHttpConnect(hSession, host, uc.nPort, 0);
    if (!hConnect) {
        WinHttpCloseHandle(hSession);
        return false;
    }

    DWORD flags = (uc.nScheme == INTERNET_SCHEME_HTTPS) ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET hRequest = WinHttpOpenRequest(hConnect, L"GET", path, nullptr,
                                            WINHTTP_NO_REFERER,
                                            WINHTTP_DEFAULT_ACCEPT_TYPES, flags);
    if (!hRequest) {
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return false;
    }

    bool ok = WinHttpSendRequest(hRequest, WINHTTP_NO_ADDITIONAL_HEADERS, 0,
                                 WINHTTP_NO_REQUEST_DATA, 0, 0, 0) &&
              WinHttpReceiveResponse(hRequest, nullptr);

    if (ok) {
        DWORD status = 0;
        DWORD sz = sizeof(status);
        WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                            WINHTTP_HEADER_NAME_BY_INDEX, &status, &sz, WINHTTP_NO_HEADER_INDEX);
        if (status >= 200 && status < 300) {
            DWORD avail = 0;
            while (WinHttpQueryDataAvailable(hRequest, &avail) && avail > 0) {
                size_t old_sz = out_bytes.size();
                out_bytes.resize(old_sz + avail);
                DWORD downloaded = 0;
                if (!WinHttpReadData(hRequest, out_bytes.data() + old_sz, avail, &downloaded)) {
                    ok = false;
                    break;
                }
                out_bytes.resize(old_sz + downloaded);
            }
        } else {
            ok = false;
        }
    }

    WinHttpCloseHandle(hRequest);
    WinHttpCloseHandle(hConnect);
    WinHttpCloseHandle(hSession);
    return ok && !out_bytes.empty();
#else
    (void)url;
    (void)out_bytes;
    return false;
#endif
}

} // namespace

ImageCache::ImageCache(std::function<void()> wake_cb)
    : wake_cb_(std::move(wake_cb)) {}

ImageCache::~ImageCache() = default;

void ImageCache::clear(cui_ctx* ui) {
    if (ui) {
        for (auto& [_, entry] : ui_cache_) {
            if (entry.texture) {
                cui_image_destroy(ui, entry.texture);
            }
        }
    }
    ui_cache_.clear();
    std::lock_guard<std::mutex> lock(pending_mutex_);
    pending_decoded_.clear();
    in_flight_.clear();
}

CachedImage ImageCache::resolve(cui_ctx* ui,
                                const std::string& source,
                                std::optional<cui_color> current_color,
                                int target_max_dim) {
    if (!ui || source.empty()) return {};

    bool maybe_svg = is_svg_data_uri(source) ||
                     ends_with_ci(source, ".svg") ||
                     is_svg_bytes(reinterpret_cast<const uint8_t*>(source.data()), source.size());
    std::string cache_key = source;
    if (maybe_svg && current_color.has_value()) {
        cache_key += "|fg=" + color_hex_rgb(*current_color);
    }
    if (maybe_svg) {
        cache_key += "|max=" + std::to_string(target_max_dim);
    }

    auto it = ui_cache_.find(cache_key);
    if (it != ui_cache_.end()) {
        return it->second;
    }

    // Check if background thread finished decoding
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        auto pit = pending_decoded_.find(cache_key);
        if (pit != pending_decoded_.end()) {
            PendingDecode pd = std::move(pit->second);
            pending_decoded_.erase(pit);
            in_flight_.erase(cache_key);

            CachedImage ci{};
            if (!pd.failed && !pd.rgba.empty() && pd.width > 0 && pd.height > 0) {
                ci.texture = cui_image_create(ui, pd.width, pd.height, pd.rgba.data());
                ci.width = pd.width;
                ci.height = pd.height;
                ci.ready = (ci.texture != 0);
                ci.failed = !ci.ready;
            } else {
                ci.failed = true;
            }
            ui_cache_[cache_key] = ci;
            return ci;
        }
    }

    bool is_http = starts_with_ci(source, "http://") || starts_with_ci(source, "https://");
    if (!is_http) {
        std::vector<uint8_t> raw_bytes;
        bool hint_svg = false;

        if (is_svg_bytes(reinterpret_cast<const uint8_t*>(source.data()), source.size())) {
            raw_bytes.assign(source.begin(), source.end());
            hint_svg = true;
        } else if (starts_with_ci(source, "data:")) {
            auto comma = source.find(',');
            if (comma != std::string::npos) {
                std::string meta = source.substr(0, comma);
                std::string payload = source.substr(comma + 1);
                hint_svg = is_svg_data_uri(meta);
                if (meta.find(";base64") != std::string::npos || meta.find(";BASE64") != std::string::npos) {
                    raw_bytes = decode_base64(payload);
                } else {
                    std::string decoded = url_decode(payload);
                    raw_bytes.assign(decoded.begin(), decoded.end());
                }
            }
        } else if (loader_ && loader_->read_bytes(source, raw_bytes)) {
            hint_svg = ends_with_ci(source, ".svg");
        } else {
            raw_bytes = decode_base64(source);
        }

        CachedImage ci{};
        std::vector<uint8_t> rgba;
        int w = 0, h = 0;
        if (decode_any_to_rgba(raw_bytes.data(), raw_bytes.size(), hint_svg,
                               current_color, target_max_dim, rgba, w, h)) {
            ci.texture = cui_image_create(ui, w, h, rgba.data());
            ci.width = w;
            ci.height = h;
            ci.ready = (ci.texture != 0);
            ci.failed = !ci.ready;
        } else {
            ci.failed = true;
        }
        ui_cache_[cache_key] = ci;
        return ci;
    }

    start_async_fetch(cache_key, source, current_color, target_max_dim);
    return {};
}

void ImageCache::start_async_fetch(const std::string& cache_key,
                                   const std::string& url,
                                   std::optional<cui_color> current_color,
                                   int target_max_dim) {
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        if (in_flight_[cache_key]) return;
        in_flight_[cache_key] = true;
    }

    std::thread([this, cache_key, url, current_color, target_max_dim]() {
        PendingDecode pd{};
        std::vector<uint8_t> raw;
        if (http_get_bytes(url, raw)) {
            bool hint_svg = ends_with_ci(url, ".svg");
            if (!decode_any_to_rgba(raw.data(), raw.size(), hint_svg,
                                    current_color, target_max_dim,
                                    pd.rgba, pd.width, pd.height)) {
                pd.failed = true;
            }
        } else {
            pd.failed = true;
        }

        {
            std::lock_guard<std::mutex> lock(pending_mutex_);
            pending_decoded_[cache_key] = std::move(pd);
        }
        if (wake_cb_) wake_cb_();
    }).detach();
}
