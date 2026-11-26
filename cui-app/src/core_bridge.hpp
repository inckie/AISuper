#pragma once

#include "aisuper_core.h"

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <mutex>
#include <string>

class AppletLoader;

class CoreBridge {
public:
    CoreBridge();
    ~CoreBridge();

    CoreBridge(const CoreBridge&) = delete;
    CoreBridge& operator=(const CoreBridge&) = delete;

    bool load_library(const std::string& explicit_path = "");
    bool is_loaded() const { return lib_handle_ != nullptr; }
    const std::string& load_error() const { return load_error_; }

    void set_native_window(void* native_hwnd);

    bool start_session(AppletLoader* loader);
    void destroy_session();

    void load_applet(const std::string& manifest_path);
    void send_action(const std::string& action, const std::string& args_json = "[]");
    void set_value(const std::string& id, const std::string& value_json);
    void module_command(const std::string& module_type, const std::string& target,
                        const std::string& command, const std::string& args_json = "[]");
    void respond_modal(const std::string& modal_id, bool confirmed);

    // Polls latest pending snapshot/error if sequence changed. Returns true if updated.
    bool poll_updates(std::string* out_snapshot_json, std::string* out_error, uint64_t* in_out_seq);

    // Blocks until snapshot sequence > after_seq or timeout_ms elapses.
    bool wait_for_snapshot(uint64_t after_seq, int timeout_ms, std::string* out_snapshot_json, uint64_t* out_seq);

    uint64_t current_seq() const { return snapshot_seq_.load(); }
    std::string last_error() const;

    void wake_ui();

private:
    static void on_snapshot_trampoline(void* user_data, const char* json_utf8) noexcept;
    static void on_error_trampoline(void* user_data, const char* message_utf8) noexcept;
    static int  read_file_trampoline(void* user_data, const char* rel_path, uint8_t** out_data, size_t* out_len) noexcept;
    static void free_buf_trampoline(void* user_data, uint8_t* data) noexcept;

    void* lib_handle_ = nullptr;
    std::string load_error_;

    aisuper_session_create_fn fn_create_ = nullptr;
    aisuper_session_load_applet_fn fn_load_applet_ = nullptr;
    aisuper_session_send_action_fn fn_send_action_ = nullptr;
    aisuper_session_set_value_fn fn_set_value_ = nullptr;
    aisuper_session_module_command_fn fn_module_command_ = nullptr;
    aisuper_session_respond_modal_fn fn_respond_modal_ = nullptr;
    aisuper_session_destroy_fn fn_destroy_ = nullptr;

    aisuper_session_t session_ = nullptr;
    AppletLoader* loader_ = nullptr;
    std::atomic<void*> native_window_{nullptr};

    mutable std::mutex mutex_;
    std::condition_variable cv_;
    std::string latest_snapshot_json_;
    std::string latest_error_;
    std::atomic<uint64_t> snapshot_seq_{0};
    uint64_t error_seq_ = 0;
    uint64_t last_seen_error_seq_ = 0;
};
