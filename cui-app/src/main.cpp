#include "applet_loader.hpp"
#include "core_bridge.hpp"
#include "image_cache.hpp"
#include "style_resolver.hpp"
#include "widget_renderer.hpp"

#include <cui/cui.h>
#include <cui/cui_app.h>
#include <cui/cui_icons.h>
#include <nlohmann/json.hpp>

#include <filesystem>
#include <string>

namespace {

struct AppState {
    CoreBridge bridge;
    AppletLoader loader;
    ImageCache images;
    WidgetRenderer renderer;

    nlohmann::json snapshot;
    std::string raw_snapshot_json;
    uint64_t snapshot_seq = 0;
    std::string status_text = "Ready";
    std::string error_text;
    std::string theme_key;
    std::string pending_open_path;

    bool show_debug = false;
    bool fonts_initialized = false;

    AppState()
        : images([this]() { bridge.wake_ui(); }),
          renderer(bridge, images) {}

    bool load_applet(cui_ctx* ui, const std::string& path) {
        std::string err;
        if (!loader.open(path, err)) {
            error_text = err;
            status_text = "Load error";
            return false;
        }
        images.clear(ui);
        images.set_loader(&loader);
        renderer.reset_for_session();
        snapshot = nlohmann::json();
        raw_snapshot_json.clear();
        snapshot_seq = 0;
        error_text.clear();

        if (!bridge.start_session(&loader)) {
            error_text = bridge.load_error().empty() ? "Failed to start AISuper session" : bridge.load_error();
            status_text = "Session error";
            return false;
        }
        status_text = "Loading...";
        bridge.load_applet(loader.manifest_entry());
        return true;
    }
};

void install_unicode_fallback_fonts(cui_ctx* ui) {
#ifdef _WIN32
    const char* candidates[] = {
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/tahoma.ttf"
    };
    for (const char* path : candidates) {
        if (std::filesystem::exists(path)) {
            cui_font f = cui_font_load_file(ui, path);
            if (f >= 0) {
                cui_font_add_fallback(ui, CUI_FONT_BODY, f);
                cui_font_add_fallback(ui, CUI_FONT_MEDIUM, f);
                cui_font_add_fallback(ui, CUI_FONT_DISPLAY, f);
                break;
            }
        }
    }
#elif defined(__APPLE__)
    const char* path = "/System/Library/Fonts/Supplemental/Arial.ttf";
    if (std::filesystem::exists(path)) {
        cui_font f = cui_font_load_file(ui, path);
        if (f >= 0) {
            cui_font_add_fallback(ui, CUI_FONT_BODY, f);
            cui_font_add_fallback(ui, CUI_FONT_MEDIUM, f);
        }
    }
#else
    const char* path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf";
    if (std::filesystem::exists(path)) {
        cui_font f = cui_font_load_file(ui, path);
        if (f >= 0) {
            cui_font_add_fallback(ui, CUI_FONT_BODY, f);
            cui_font_add_fallback(ui, CUI_FONT_MEDIUM, f);
        }
    }
#endif
}

void draw_frame(cui_ctx* ui, void* user) {
    auto* state = static_cast<AppState*>(user);

    if (!state->fonts_initialized) {
        state->fonts_initialized = true;
        install_unicode_fallback_fonts(ui);
    }

    if (!state->pending_open_path.empty()) {
        std::string path = std::move(state->pending_open_path);
        state->pending_open_path.clear();
        state->load_applet(ui, path);
    }

    if (cui_dropped_file_count(ui) > 0) {
        const char* dropped = cui_dropped_file(ui, 0);
        if (dropped && dropped[0] != '\0') {
            state->load_applet(ui, dropped);
        }
    }

    std::string new_json;
    std::string new_err;
    if (state->bridge.poll_updates(&new_json, &new_err, &state->snapshot_seq)) {
        if (!new_json.empty()) {
            try {
                state->snapshot = nlohmann::json::parse(new_json);
                state->raw_snapshot_json = std::move(new_json);
                std::string feat = state->snapshot.value("featureId", "");
                state->status_text = feat.empty() ? "Connected" : ("Feature: " + feat);
            } catch (const std::exception& e) {
                state->error_text = std::string("Snapshot parse error: ") + e.what();
            }
        }
        if (!new_err.empty()) {
            state->error_text = new_err;
        }
    }

    if (state->snapshot.is_object() && state->snapshot.contains("styleSheet") && state->snapshot["styleSheet"].is_object()) {
        apply_theme_from_stylesheet(ui, state->snapshot["styleSheet"], state->theme_key);
    }

    cui_box root{};
    root.dir = CUI_COLUMN;
    root.width = CUI_FILL;
    root.height = CUI_FILL;
    root.bg = CUI_COLOR_BACKGROUND;
    cui_box_begin(ui, &root);

    // Top bar
    cui_box bar{};
    bar.dir = CUI_ROW;
    bar.width = CUI_FILL;
    bar.pad_x = 16.0f;
    bar.pad_y = 10.0f;
    bar.gap = 12.0f;
    bar.align = CUI_ALIGN_CENTER;
    bar.bg = CUI_COLOR_SURFACE;
    bar.border = 1.0f;
    cui_row_begin(ui, &bar);
    {
        cui_box title_col{};
        title_col.dir = CUI_COLUMN;
        title_col.width = CUI_FILL;
        cui_column_begin(ui, &title_col);
        {
            cui_text_opts t_title{};
            t_title.font = CUI_FONT_MEDIUM;
            t_title.size = 15.0f;
            cui_text_ex(ui, "AISuper Native (CUI + K/N)", &t_title);

            cui_text_opts t_sub{};
            t_sub.color = CUI_COLOR_TEXT_SECONDARY;
            t_sub.size = 12.0f;
            cui_text_ex(ui, state->status_text.c_str(), &t_sub);
        }
        cui_box_end(ui);

        cui_button_opts open_btn{};
        open_btn.icon = CUI_ICON_FOLDER_OPEN;
        open_btn.variant = CUI_BUTTON_OUTLINE;
        open_btn.size = CUI_SIZE_SM;
        if (cui_button_ex(ui, "Folder", &open_btn)) {
            char chosen[1024]{};
            cui_file_dialog_opts fd{};
            fd.kind = CUI_FILE_FOLDER;
            fd.title = "Open Applet Folder";
            if (cui_app_file_dialog(nullptr, &fd, chosen, sizeof(chosen))) {
                state->load_applet(ui, chosen);
            }
        }

        cui_button_opts zip_btn{};
        zip_btn.icon = CUI_ICON_ARCHIVE;
        zip_btn.variant = CUI_BUTTON_OUTLINE;
        zip_btn.size = CUI_SIZE_SM;
        if (cui_button_ex(ui, ".zip / .json", &zip_btn)) {
            char chosen[1024]{};
            cui_file_dialog_opts fd{};
            fd.kind = CUI_FILE_OPEN;
            fd.title = "Open Applet Archive or Manifest";
            fd.filter_name = "AISuper Applets";
            fd.filter = "zip;json";
            if (cui_app_file_dialog(nullptr, &fd, chosen, sizeof(chosen))) {
                state->load_applet(ui, chosen);
            }
        }

        cui_button_opts dbg_btn{};
        dbg_btn.icon = CUI_ICON_BUG;
        dbg_btn.variant = state->show_debug ? CUI_BUTTON_PRIMARY : CUI_BUTTON_GHOST;
        dbg_btn.size = CUI_SIZE_SM;
        dbg_btn.tooltip = "Toggle Snapshot Inspector";
        if (cui_icon_button_ex(ui, CUI_ICON_BUG, &dbg_btn)) {
            state->show_debug = !state->show_debug;
        }
    }
    cui_box_end(ui);

    if (!state->error_text.empty()) {
        cui_alert_opts aopts{};
        aopts.tone = CUI_TONE_DANGER;
        cui_alert_ex(ui, state->error_text.c_str(), &aopts);
    }

    // Main content
    bool has_layout = state->snapshot.is_object() &&
                      state->snapshot.contains("layout") &&
                      state->snapshot["layout"].is_object() &&
                      state->snapshot["layout"].contains("layout") &&
                      state->snapshot["layout"]["layout"].is_object();

    cui_box content{};
    content.dir = CUI_COLUMN;
    content.width = CUI_FILL;
    content.height = CUI_FILL;
    content.pad = 16.0f;
    cui_column_begin(ui, &content);
    {
        if (!has_layout) {
            cui_box center{};
            center.dir = CUI_COLUMN;
            center.width = CUI_FILL;
            center.height = CUI_FILL;
            center.align = CUI_ALIGN_CENTER;
            center.justify = CUI_JUSTIFY_CENTER;
            center.gap = 12.0f;
            cui_column_begin(ui, &center);
            {
                if (cui_empty_state_ex(ui, "Load an AISuper Applet", nullptr)) {
                    // handled below
                }
                cui_text_opts hint{};
                hint.color = CUI_COLOR_TEXT_SECONDARY;
                cui_text_ex(ui, "Drop an applet folder, applet.json, or .zip archive onto this window.", &hint);
            }
            cui_box_end(ui);
        } else {
            state->renderer.render_root(ui, state->snapshot);
        }
    }
    cui_box_end(ui);

    cui_box_end(ui); // root

    // Non-cancellable modal confirmation dialog
    if (state->snapshot.is_object() && state->snapshot.contains("modal") && state->snapshot["modal"].is_object()) {
        const auto& modal = state->snapshot["modal"];
        std::string modal_id = modal.value("id", "");
        std::string title = modal.value("title", "Confirmation");
        std::string message = modal.value("message", "");
        std::string confirm_txt = modal.value("confirmText", "Confirm");
        std::string cancel_txt = modal.value("cancelText", "Cancel");
        bool destructive = modal.value("isDestructive", false);

        bool open = true;
        cui_dialog_opts dopts{};
        dopts.persistent = true;
        dopts.no_close_button = true;
        dopts.description = message.empty() ? nullptr : cui_frame_strdup(ui, message.c_str());
        if (cui_dialog_begin(ui, title.c_str(), &open, &dopts)) {
            cui_dialog_footer_begin(ui);
            {
                if (cui_button_ex(ui, cancel_txt.c_str(), nullptr)) {
                    state->bridge.respond_modal(modal_id, false);
                }
                cui_button_opts cbtn{};
                cbtn.variant = destructive ? CUI_BUTTON_DANGER : CUI_BUTTON_PRIMARY;
                if (cui_button_ex(ui, confirm_txt.c_str(), &cbtn)) {
                    state->bridge.respond_modal(modal_id, true);
                }
            }
            cui_dialog_footer_end(ui);
            cui_dialog_end(ui);
        }
    }

    // Debug snapshot sheet
    cui_sheet_opts sopts{};
    sopts.side = CUI_SIDE_RIGHT;
    sopts.size = 460.0f;
    sopts.description = "Live JSON snapshot emitted from Kotlin/Native aisuper_core";
    if (cui_sheet_begin(ui, "Snapshot Inspector", &state->show_debug, &sopts)) {
        if (!state->raw_snapshot_json.empty()) {
            cui_json_opts jopts{};
            jopts.expand_depth = 3;
            cui_json_view_ex(ui, "snapshot_json", state->raw_snapshot_json.c_str(), &jopts);
        } else {
            cui_text_ex(ui, "No active session snapshot yet.", nullptr);
        }
        cui_sheet_end(ui);
    }
}

} // namespace

int main(int argc, char** argv) {
    AppState state;
    if (!state.bridge.load_library()) {
        state.error_text = state.bridge.load_error();
    }
    if (argc > 1 && argv[1] && argv[1][0] != '\0') {
        state.pending_open_path = argv[1];
    }

    cui_app_desc desc{};
    desc.title = "AISuper Native";
    desc.width = 1024;
    desc.height = 768;
    desc.min_width = 480;
    desc.min_height = 420;
    desc.follow_system_theme = true;

    cui_app* app = cui_app_create(&desc);
    if (!app) return 1;

    state.bridge.set_native_window(cui_app_native_window(app));

    while (cui_app_frame(app)) {
        cui_ctx* ui = cui_app_ui(app);
        draw_frame(ui, &state);
    }

    state.images.clear(cui_app_ui(app));
    state.bridge.set_native_window(nullptr);
    state.bridge.destroy_session();
    cui_app_destroy(app);
    return 0;
}
