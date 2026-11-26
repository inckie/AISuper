#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* aisuper_session_t;

typedef void (*aisuper_snapshot_cb)(void* user_data, const char* snapshot_json_utf8);
typedef void (*aisuper_error_cb)(void* user_data, const char* message_utf8);
typedef int  (*aisuper_read_file_cb)(void* user_data, const char* rel_path, uint8_t** out_data, size_t* out_len);
typedef void (*aisuper_free_buf_cb)(void* user_data, uint8_t* data);

typedef aisuper_session_t (*aisuper_session_create_fn)(
    aisuper_snapshot_cb on_snapshot,
    aisuper_error_cb on_error,
    aisuper_read_file_cb read_file,
    aisuper_free_buf_cb free_buf,
    void* user_data
);

typedef void (*aisuper_session_load_applet_fn)(aisuper_session_t handle, const char* manifest_path);
typedef void (*aisuper_session_send_action_fn)(aisuper_session_t handle, const char* action, const char* args_json);
typedef void (*aisuper_session_set_value_fn)(aisuper_session_t handle, const char* id, const char* value_json);
typedef void (*aisuper_session_module_command_fn)(
    aisuper_session_t handle,
    const char* module_type,
    const char* target,
    const char* command,
    const char* args_json
);
typedef void (*aisuper_session_respond_modal_fn)(aisuper_session_t handle, const char* modal_id, bool confirmed);
typedef void (*aisuper_session_destroy_fn)(aisuper_session_t handle);

#ifdef __cplusplus
}
#endif
