#include "core_bridge.hpp"
#include "applet_loader.hpp"

#include <chrono>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace fs = std::filesystem;

namespace {

#ifdef _WIN32
std::wstring utf8_to_wide(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), -1, nullptr, 0);
    if (n <= 0) return L"";
    std::wstring out(n - 1, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), -1, out.data(), n);
    return out;
}

std::string exe_dir_utf8() {
    wchar_t buf[MAX_PATH]{};
    DWORD len = GetModuleFileNameW(nullptr, buf, MAX_PATH);
    if (len == 0) return ".";
    fs::path p(buf);
    return p.parent_path().u8string().empty() ? "." : std::string(reinterpret_cast<const char*>(p.parent_path().u8string().c_str()));
}
#endif

template <typename Fn>
bool load_sym(void* lib, const char* name, Fn& out) {
#ifdef _WIN32
    FARPROC proc = GetProcAddress(reinterpret_cast<HMODULE>(lib), name);
    out = reinterpret_cast<Fn>(proc);
#else
    void* proc = dlsym(lib, name);
    out = reinterpret_cast<Fn>(proc);
#endif
    return out != nullptr;
}

} // namespace

CoreBridge::CoreBridge() = default;

CoreBridge::~CoreBridge() {
    destroy_session();
    if (lib_handle_) {
#ifdef _WIN32
        FreeLibrary(reinterpret_cast<HMODULE>(lib_handle_));
#else
        dlclose(lib_handle_);
#endif
        lib_handle_ = nullptr;
    }
}

bool CoreBridge::load_library(const std::string& explicit_path) {
    if (lib_handle_) return true;

#ifdef _WIN32
    const char* default_name = "aisuper_core.dll";
    std::vector<std::wstring> candidates;
    if (!explicit_path.empty()) {
        candidates.push_back(utf8_to_wide(explicit_path));
    }
    candidates.push_back((fs::path(utf8_to_wide(exe_dir_utf8())) / L"aisuper_core.dll").wstring());
    candidates.push_back(utf8_to_wide(default_name));

    HMODULE mod = nullptr;
    for (const auto& cand : candidates) {
        if (cand.empty()) continue;
        mod = LoadLibraryW(cand.c_str());
        if (mod) break;
    }
    if (!mod) {
        DWORD err = GetLastError();
        load_error_ = "Failed to load aisuper_core.dll (Win32 error " + std::to_string(err) + ")";
        return false;
    }
    lib_handle_ = reinterpret_cast<void*>(mod);
#else
#ifdef __APPLE__
    const char* default_name = "libaisuper_core.dylib";
#else
    const char* default_name = "libaisuper_core.so";
#endif
    void* mod = dlopen(explicit_path.empty() ? default_name : explicit_path.c_str(), RTLD_LAZY);
    if (!mod) {
        const char* dlerr = dlerror();
        load_error_ = dlerr ? dlerr : "Failed to load libaisuper_core";
        return false;
    }
    lib_handle_ = mod;
#endif

    bool ok =
        load_sym(lib_handle_, "aisuper_session_create", fn_create_) &&
        load_sym(lib_handle_, "aisuper_session_load_applet", fn_load_applet_) &&
        load_sym(lib_handle_, "aisuper_session_send_action", fn_send_action_) &&
        load_sym(lib_handle_, "aisuper_session_set_value", fn_set_value_) &&
        load_sym(lib_handle_, "aisuper_session_module_command", fn_module_command_) &&
        load_sym(lib_handle_, "aisuper_session_respond_modal", fn_respond_modal_) &&
        load_sym(lib_handle_, "aisuper_session_destroy", fn_destroy_);

    if (!ok) {
        load_error_ = "aisuper_core library is missing required aisuper_session_* exports";
        return false;
    }

    return true;
}

void CoreBridge::set_native_window(void* native_hwnd) {
    native_window_.store(native_hwnd);
}

bool CoreBridge::start_session(AppletLoader* loader) {
    if (!lib_handle_ && !load_library()) return false;
    destroy_session();

    loader_ = loader;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        latest_snapshot_json_.clear();
        latest_error_.clear();
        snapshot_seq_.store(0);
    }

    session_ = fn_create_(
        &CoreBridge::on_snapshot_trampoline,
        &CoreBridge::on_error_trampoline,
        &CoreBridge::read_file_trampoline,
        &CoreBridge::free_buf_trampoline,
        this
    );
    return session_ != nullptr;
}

void CoreBridge::destroy_session() {
    if (session_ && fn_destroy_) {
        fn_destroy_(session_);
        session_ = nullptr;
    }
    loader_ = nullptr;
}

void CoreBridge::load_applet(const std::string& manifest_path) {
    if (session_ && fn_load_applet_) {
        fn_load_applet_(session_, manifest_path.c_str());
    }
}

void CoreBridge::send_action(const std::string& action, const std::string& args_json) {
    if (session_ && fn_send_action_) {
        fn_send_action_(session_, action.c_str(), args_json.c_str());
    }
}

void CoreBridge::set_value(const std::string& id, const std::string& value_json) {
    if (session_ && fn_set_value_) {
        fn_set_value_(session_, id.c_str(), value_json.c_str());
    }
}

void CoreBridge::module_command(const std::string& module_type, const std::string& target,
                                const std::string& command, const std::string& args_json) {
    if (session_ && fn_module_command_) {
        fn_module_command_(session_, module_type.c_str(), target.c_str(), command.c_str(), args_json.c_str());
    }
}

void CoreBridge::respond_modal(const std::string& modal_id, bool confirmed) {
    if (session_ && fn_respond_modal_) {
        fn_respond_modal_(session_, modal_id.c_str(), confirmed);
    }
}

bool CoreBridge::poll_updates(std::string* out_snapshot_json, std::string* out_error, uint64_t* in_out_seq) {
    std::lock_guard<std::mutex> lock(mutex_);
    bool updated = false;
    uint64_t cur = snapshot_seq_.load();
    if (in_out_seq && cur != *in_out_seq && !latest_snapshot_json_.empty()) {
        *in_out_seq = cur;
        if (out_snapshot_json) *out_snapshot_json = latest_snapshot_json_;
        updated = true;
    }
    if (error_seq_ != last_seen_error_seq_) {
        last_seen_error_seq_ = error_seq_;
        if (out_error) *out_error = latest_error_;
        updated = true;
    }
    return updated;
}

bool CoreBridge::wait_for_snapshot(uint64_t after_seq, int timeout_ms, std::string* out_snapshot_json, uint64_t* out_seq) {
    std::unique_lock<std::mutex> lock(mutex_);
    bool ok = cv_.wait_for(lock, std::chrono::milliseconds(timeout_ms), [&] {
        return snapshot_seq_.load() > after_seq && !latest_snapshot_json_.empty();
    });
    if (!ok) return false;
    if (out_snapshot_json) *out_snapshot_json = latest_snapshot_json_;
    if (out_seq) *out_seq = snapshot_seq_.load();
    return true;
}

std::string CoreBridge::last_error() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return latest_error_;
}

void CoreBridge::wake_ui() {
#ifdef _WIN32
    void* hwnd = native_window_.load();
    if (hwnd) {
        PostMessageW(reinterpret_cast<HWND>(hwnd), WM_NULL, 0, 0);
    }
#endif
}

void CoreBridge::on_snapshot_trampoline(void* user_data, const char* json_utf8) noexcept {
    if (!user_data || !json_utf8) return;
    try {
        auto* self = static_cast<CoreBridge*>(user_data);
        {
            std::lock_guard<std::mutex> lock(self->mutex_);
            self->latest_snapshot_json_.assign(json_utf8);
            self->snapshot_seq_.fetch_add(1);
        }
        self->cv_.notify_all();
        self->wake_ui();
    } catch (...) {
    }
}

void CoreBridge::on_error_trampoline(void* user_data, const char* message_utf8) noexcept {
    if (!user_data || !message_utf8) return;
    try {
        auto* self = static_cast<CoreBridge*>(user_data);
        {
            std::lock_guard<std::mutex> lock(self->mutex_);
            self->latest_error_.assign(message_utf8);
            self->error_seq_++;
        }
        self->cv_.notify_all();
        self->wake_ui();
    } catch (...) {
    }
}

int CoreBridge::read_file_trampoline(void* user_data, const char* rel_path, uint8_t** out_data, size_t* out_len) noexcept {
    if (!user_data || !rel_path || !out_data || !out_len) return -1;
    *out_data = nullptr;
    *out_len = 0;
    try {
        auto* self = static_cast<CoreBridge*>(user_data);
        if (!self->loader_) return -2;
        std::vector<uint8_t> bytes;
        if (!self->loader_->read_bytes(rel_path, bytes)) {
            return -3;
        }
        size_t sz = bytes.size();
        uint8_t* buf = static_cast<uint8_t*>(std::malloc(sz > 0 ? sz : 1));
        if (!buf) return -4;
        if (sz > 0) {
            std::memcpy(buf, bytes.data(), sz);
        }
        *out_data = buf;
        *out_len = sz;
        return 0;
    } catch (...) {
        return -5;
    }
}

void CoreBridge::free_buf_trampoline(void* /*user_data*/, uint8_t* data) noexcept {
    if (data) {
        std::free(data);
    }
}
