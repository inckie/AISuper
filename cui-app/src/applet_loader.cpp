#include "applet_loader.hpp"

#include <miniz.h>

#include <algorithm>
#include <cctype>
#include <cstring>
#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;

namespace {

std::string to_forward_slashes(std::string s) {
    std::replace(s.begin(), s.end(), '\\', '/');
    return s;
}

bool ends_with_ci(const std::string& s, const std::string& suffix) {
    if (s.size() < suffix.size()) return false;
    size_t off = s.size() - suffix.size();
    for (size_t i = 0; i < suffix.size(); ++i) {
        char a = static_cast<char>(std::tolower(static_cast<unsigned char>(s[off + i])));
        char b = static_cast<char>(std::tolower(static_cast<unsigned char>(suffix[i])));
        if (a != b) return false;
    }
    return true;
}

fs::path u8_to_path(const std::string& utf8) {
    return fs::path(std::u8string(reinterpret_cast<const char8_t*>(utf8.data()),
                                   reinterpret_cast<const char8_t*>(utf8.data() + utf8.size())));
}

std::string path_to_u8(const fs::path& p) {
    auto u8 = p.u8string();
    return std::string(reinterpret_cast<const char*>(u8.data()), u8.size());
}

} // namespace

AppletLoader::~AppletLoader() {
    close();
}

void AppletLoader::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (zip_archive_) {
        auto* zip = static_cast<mz_zip_archive*>(zip_archive_);
        mz_zip_reader_end(zip);
        delete zip;
        zip_archive_ = nullptr;
    }
    mode_ = Mode::None;
    base_dir_.clear();
    zip_prefix_.clear();
    manifest_entry_ = "applet.json";
    source_path_.clear();
}

std::string AppletLoader::normalize_rel_path(const std::string& p) {
    std::string s = to_forward_slashes(p);
    while (s.size() >= 2 && s[0] == '.' && s[1] == '/') {
        s.erase(0, 2);
    }
    while (!s.empty() && s.front() == '/') {
        s.erase(0, 1);
    }
    if (s.find("..") != std::string::npos) {
        return "";
    }
    return s;
}

bool AppletLoader::open(const std::string& input_path, std::string& out_error) {
    close();
    std::lock_guard<std::mutex> lock(mutex_);

    if (input_path.empty()) {
        out_error = "Empty applet path";
        return false;
    }

    fs::path p = u8_to_path(input_path);
    std::error_code ec;
    if (!fs::exists(p, ec)) {
        out_error = "Path does not exist: " + input_path;
        return false;
    }

    source_path_ = path_to_u8(p);

    if (fs::is_directory(p, ec)) {
        fs::path manifest = p / "applet.json";
        if (!fs::is_regular_file(manifest, ec)) {
            out_error = "No applet.json found in directory: " + input_path;
            return false;
        }
        mode_ = Mode::Directory;
        base_dir_ = path_to_u8(p);
        manifest_entry_ = "applet.json";
        return true;
    }

    if (ends_with_ci(source_path_, ".zip")) {
        auto* zip = new mz_zip_archive{};
        if (!mz_zip_reader_init_file(zip, source_path_.c_str(), 0)) {
            delete zip;
            out_error = "Failed to open ZIP archive: " + input_path;
            return false;
        }

        // Find applet.json at root or shallowest subfolder
        int best_idx = -1;
        std::string best_prefix;
        size_t best_depth = SIZE_MAX;

        mz_uint num_files = mz_zip_reader_get_num_files(zip);
        for (mz_uint i = 0; i < num_files; ++i) {
            mz_zip_archive_file_stat st{};
            if (!mz_zip_reader_file_stat(zip, i, &st)) continue;
            if (mz_zip_reader_is_file_a_directory(zip, i)) continue;

            std::string fname = to_forward_slashes(st.m_filename);
            if (fname == "applet.json" || ends_with_ci(fname, "/applet.json")) {
                size_t depth = static_cast<size_t>(std::count(fname.begin(), fname.end(), '/'));
                if (depth < best_depth) {
                    best_depth = depth;
                    best_idx = static_cast<int>(i);
                    size_t slash = fname.rfind('/');
                    best_prefix = (slash == std::string::npos) ? "" : fname.substr(0, slash + 1);
                }
            }
        }

        if (best_idx < 0) {
            mz_zip_reader_end(zip);
            delete zip;
            out_error = "No applet.json found inside ZIP: " + input_path;
            return false;
        }

        mode_ = Mode::Zip;
        zip_archive_ = zip;
        zip_prefix_ = best_prefix;
        manifest_entry_ = "applet.json";
        return true;
    }

    // Direct JSON manifest file
    if (!fs::is_regular_file(p, ec)) {
        out_error = "Unsupported applet file: " + input_path;
        return false;
    }
    mode_ = Mode::Directory;
    base_dir_ = path_to_u8(p.parent_path());
    manifest_entry_ = path_to_u8(p.filename());
    return true;
}

bool AppletLoader::read_bytes(const std::string& rel_path, std::vector<uint8_t>& out_bytes) {
    std::lock_guard<std::mutex> lock(mutex_);
    std::string clean = normalize_rel_path(rel_path);
    if (clean.empty()) return false;

    if (mode_ == Mode::Directory) {
        fs::path full = u8_to_path(base_dir_) / u8_to_path(clean);
        std::ifstream ifs(full, std::ios::binary | std::ios::ate);
        if (!ifs) return false;
        std::streamsize sz = ifs.tellg();
        if (sz < 0) return false;
        out_bytes.resize(static_cast<size_t>(sz));
        ifs.seekg(0, std::ios::beg);
        if (sz > 0 && !ifs.read(reinterpret_cast<char*>(out_bytes.data()), sz)) {
            out_bytes.clear();
            return false;
        }
        return true;
    }

    if (mode_ == Mode::Zip && zip_archive_) {
        auto* zip = static_cast<mz_zip_archive*>(zip_archive_);
        std::string entry = zip_prefix_ + clean;
        int idx = mz_zip_reader_locate_file(zip, entry.c_str(), nullptr, 0);
        if (idx < 0) {
            idx = mz_zip_reader_locate_file(zip, clean.c_str(), nullptr, 0);
        }
        if (idx < 0) return false;

        size_t uncomp_size = 0;
        void* p = mz_zip_reader_extract_to_heap(zip, static_cast<mz_uint>(idx), &uncomp_size, 0);
        if (!p) return false;
        out_bytes.resize(uncomp_size);
        if (uncomp_size > 0) {
            std::memcpy(out_bytes.data(), p, uncomp_size);
        }
        mz_free(p);
        return true;
    }

    return false;
}
