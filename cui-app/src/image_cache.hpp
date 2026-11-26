#pragma once

#include <cui/cui.h>

#include <cstdint>
#include <functional>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

class AppletLoader;

struct CachedImage {
    cui_texture texture = 0;
    int width = 0;
    int height = 0;
    bool ready = false;
    bool failed = false;
};

class ImageCache {
public:
    explicit ImageCache(std::function<void()> wake_cb);
    ~ImageCache();

    ImageCache(const ImageCache&) = delete;
    ImageCache& operator=(const ImageCache&) = delete;

    void set_loader(AppletLoader* loader) { loader_ = loader; }
    void clear(cui_ctx* ui);

    // Must be called from UI thread inside frame. Uploads ready decoded buffers to GPU/SW textures.
    CachedImage resolve(cui_ctx* ui,
                        const std::string& source,
                        std::optional<cui_color> current_color = std::nullopt,
                        int target_max_dim = 256);

private:
    struct PendingDecode {
        std::vector<uint8_t> rgba;
        int width = 0;
        int height = 0;
        bool failed = false;
    };

    void start_async_fetch(const std::string& cache_key,
                           const std::string& url,
                           std::optional<cui_color> current_color,
                           int target_max_dim);

    std::function<void()> wake_cb_;
    AppletLoader* loader_ = nullptr;

    std::unordered_map<std::string, CachedImage> ui_cache_;
    std::mutex pending_mutex_;
    std::unordered_map<std::string, PendingDecode> pending_decoded_;
    std::unordered_map<std::string, bool> in_flight_;
};
