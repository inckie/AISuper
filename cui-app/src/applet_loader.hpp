#pragma once

#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

class AppletLoader {
public:
    AppletLoader() = default;
    ~AppletLoader();

    AppletLoader(const AppletLoader&) = delete;
    AppletLoader& operator=(const AppletLoader&) = delete;

    // Resolves a folder, direct .json manifest, or .zip archive.
    bool open(const std::string& input_path, std::string& out_error);
    void close();

    const std::string& manifest_entry() const { return manifest_entry_; }
    const std::string& source_path() const { return source_path_; }

    // Thread-safe read of a relative applet resource path.
    bool read_bytes(const std::string& rel_path, std::vector<uint8_t>& out_bytes);

private:
    enum class Mode { None, Directory, Zip };

    static std::string normalize_rel_path(const std::string& p);

    std::mutex mutex_;
    Mode mode_ = Mode::None;
    std::string source_path_;
    std::string base_dir_;
    std::string manifest_entry_ = "applet.json";
    std::string zip_prefix_;
    void* zip_archive_ = nullptr; // mz_zip_archive*
};
