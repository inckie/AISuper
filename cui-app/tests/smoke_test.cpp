#include "applet_loader.hpp"
#include "core_bridge.hpp"
#include "image_cache.hpp"
#include "style_resolver.hpp"
#include "widget_renderer.hpp"

#include <cui/cui.h>
#include <cui/cui_render.h>
#include <nlohmann/json.hpp>

#include <algorithm>
#include <cmath>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

static int fail(const std::string& msg) {
    std::cerr << "[SMOKE FAIL] " << msg << std::endl;
    return 1;
}

int main(int argc, char** argv) {
    std::string applet_path = (argc > 1 && argv[1])
        ? argv[1]
        : "d:/Work/Mobile/Android/AISuper/.agents/sample-applets/widgets/applet.json";

    CoreBridge bridge;
    if (!bridge.load_library()) {
        return fail("Could not load aisuper_core.dll: " + bridge.load_error());
    }

    AppletLoader loader;
    std::string err;
    if (!loader.open(applet_path, err)) {
        return fail("AppletLoader::open failed: " + err);
    }

    if (!bridge.start_session(&loader)) {
        return fail("CoreBridge::start_session failed");
    }

    bridge.load_applet(loader.manifest_entry());

    std::string snap_json;
    uint64_t seq = 0;
    nlohmann::json snap;
    bool got_menu = false;
    for (int attempt = 0; attempt < 20; ++attempt) {
        if (!bridge.wait_for_snapshot(seq, 15000, &snap_json, &seq)) {
            return fail("Timed out waiting for initial snapshot. Last error: " + bridge.last_error());
        }
        snap = nlohmann::json::parse(snap_json);
        if (snap.value("featureId", "") == "menu" && snap.contains("layout") && snap["layout"].is_object()) {
            got_menu = true;
            break;
        }
    }
    if (!got_menu) {
        return fail("Never received 'menu' snapshot with layout. Last error: " + bridge.last_error());
    }
    std::cout << "[SMOKE] Initial snapshot seq=" << seq << " featureId=menu" << std::endl;

    // Exercise CUI software renderer + WidgetRenderer across multiple frames
    cui_sw_renderer* sw = cui_sw_create();
    if (!sw) return fail("cui_sw_create failed");

    cui_ctx_desc cdesc{};
    cdesc.renderer = cui_sw_iface(sw);
    cui_ctx* ui = cui_create(&cdesc);
    if (!ui) return fail("cui_create failed");

    ImageCache images([]() {});
    images.set_loader(&loader);
    WidgetRenderer renderer(bridge, images);
    std::string theme_key;

    const int W = 640, H = 480;
    std::vector<uint8_t> pixels(static_cast<size_t>(W) * H * 4, 0);

    for (int f = 0; f < 3; ++f) {
        cui_set_viewport(ui, static_cast<float>(W), static_cast<float>(H), 1.0f);
        cui_begin_frame(ui, 1.0f / 60.0f);
        if (snap.contains("styleSheet") && snap["styleSheet"].is_object()) {
            apply_theme_from_stylesheet(ui, snap["styleSheet"], theme_key);
        }
        renderer.render_root(ui, snap);
        cui_end_frame(ui);
        const cui_draw_data* dd = cui_get_draw_data(ui);
        if (!dd || dd->vertex_count <= 0 || dd->command_count <= 0) {
            return fail("CUI produced empty draw data on menu feature");
        }
        cui_sw_render(sw, dd, pixels.data(), W, H, W * 4, CUI_SW_RGBA);
    }
    std::cout << "[SMOKE] Rendered 3 offscreen CUI frames successfully." << std::endl;

    // Navigate to confirmation feature and test non-cancellable modal roundtrip
    uint64_t before_nav = seq;
    bridge.send_action("launch:confirmation", "[]");

    bool got_confirmation_feature = false;
    for (int attempt = 0; attempt < 20; ++attempt) {
        if (!bridge.wait_for_snapshot(before_nav, 5000, &snap_json, &seq)) break;
        before_nav = seq;
        snap = nlohmann::json::parse(snap_json);
        if (snap.value("featureId", "") == "confirmation" &&
            snap.contains("layout") && snap["layout"].is_object() &&
            snap.contains("values") && snap["values"].is_object() &&
            snap["values"].contains("lastResultLabel")) {
            got_confirmation_feature = true;
            break;
        }
    }
    if (!got_confirmation_feature) {
        return fail("Failed to switch to 'confirmation' feature");
    }
    std::cout << "[SMOKE] Switched to feature='confirmation' (seq=" << seq << ")." << std::endl;

    // Trigger confirmation dialog via JS action from confirmation_script.js
    std::string action_name;
    auto find_first_action = [&](auto& self, const nlohmann::json& node) -> void {
        if (!action_name.empty() || !node.is_object()) return;
        if (node.value("type", "") == "Button") {
            std::string act = node.value("action", "");
            if (!act.empty() && act.rfind("launch:", 0) != 0) {
                action_name = act;
                return;
            }
        }
        if (node.contains("children") && node["children"].is_array()) {
            for (const auto& ch : node["children"]) self(self, ch);
        }
    };
    find_first_action(find_first_action, snap["layout"]["layout"]);
    if (action_name.empty()) {
        return fail("Could not find a non-launch action button in confirmation layout");
    }

    uint64_t before_modal = seq;
    bridge.send_action(action_name, "[]");
    bool got_modal = false;
    for (int attempt = 0; attempt < 10; ++attempt) {
        if (!bridge.wait_for_snapshot(before_modal, 5000, &snap_json, &seq)) break;
        before_modal = seq;
        snap = nlohmann::json::parse(snap_json);
        if (snap.contains("modal") && snap["modal"].is_object()) {
            got_modal = true;
            break;
        }
    }
    if (!got_modal) {
        return fail("Action '" + action_name + "' did not trigger modal snapshot");
    }

    std::string modal_id = snap["modal"].value("id", "");
    std::cout << "[SMOKE] Modal appeared: id=" << modal_id
              << " title='" << snap["modal"].value("title", "") << "'" << std::endl;

    uint64_t before_resp = seq;
    bridge.respond_modal(modal_id, true);
    bool modal_cleared = false;
    for (int attempt = 0; attempt < 10; ++attempt) {
        if (!bridge.wait_for_snapshot(before_resp, 5000, &snap_json, &seq)) break;
        before_resp = seq;
        snap = nlohmann::json::parse(snap_json);
        if (!snap.contains("modal") || snap["modal"].is_null()) {
            modal_cleared = true;
            break;
        }
    }
    if (!modal_cleared) {
        return fail("Modal did not clear after respond_modal");
    }
    std::cout << "[SMOKE] Modal confirmed and cleared cleanly." << std::endl;

    // Navigate to images feature and verify LunaSVG renders dynamic_svg + currentColor icons
    uint64_t before_images = seq;
    bridge.send_action("launch:images", "[]");
    bool got_images_feature = false;
    for (int attempt = 0; attempt < 20; ++attempt) {
        if (!bridge.wait_for_snapshot(before_images, 5000, &snap_json, &seq)) break;
        before_images = seq;
        snap = nlohmann::json::parse(snap_json);
        if (snap.value("featureId", "") == "images" &&
            snap.contains("layout") && snap["layout"].is_object() &&
            snap.contains("values") && snap["values"].is_object() &&
            snap["values"].contains("dynamic_svg")) {
            got_images_feature = true;
            break;
        }
    }
    if (!got_images_feature) {
        return fail("Failed to switch to 'images' feature with dynamic_svg");
    }

    std::string dynamic_svg = snap["values"]["dynamic_svg"].get<std::string>();
    const cui_draw_data* images_dd = nullptr;
    for (int f = 0; f < 3; ++f) {
        cui_set_viewport(ui, static_cast<float>(W), static_cast<float>(H), 1.0f);
        cui_begin_frame(ui, 1.0f / 60.0f);
        if (snap.contains("styleSheet") && snap["styleSheet"].is_object()) {
            apply_theme_from_stylesheet(ui, snap["styleSheet"], theme_key);
        }
        renderer.render_root(ui, snap);
        cui_end_frame(ui);
        images_dd = cui_get_draw_data(ui);
        cui_sw_render(sw, images_dd, pixels.data(), W, H, W * 4, CUI_SW_RGBA);
    }

    cui_color fg = cui_color_of(ui, CUI_COLOR_FOREGROUND);
    CachedImage svg_img = images.resolve(ui, dynamic_svg, fg, 512);
    if (!svg_img.ready || svg_img.texture == 0 || svg_img.width <= 0 || svg_img.height <= 0) {
        return fail("LunaSVG failed to decode dynamic_svg from images demo");
    }
    bool found_svg_quad = false;
    float min_x = std::numeric_limits<float>::max();
    float max_x = std::numeric_limits<float>::lowest();
    float min_y = std::numeric_limits<float>::max();
    float max_y = std::numeric_limits<float>::lowest();
    if (images_dd && images_dd->commands && images_dd->vertices && images_dd->indices) {
        for (int ci = 0; ci < images_dd->command_count; ++ci) {
            const cui_draw_cmd& cmd = images_dd->commands[ci];
            if (cmd.texture != svg_img.texture || cmd.index_count == 0) continue;
            found_svg_quad = true;
            for (uint32_t k = 0; k < cmd.index_count; ++k) {
                uint32_t idx = images_dd->indices[cmd.index_offset + k];
                if (idx >= static_cast<uint32_t>(images_dd->vertex_count)) continue;
                const cui_vertex& v = images_dd->vertices[idx];
                min_x = std::min(min_x, v.x);
                max_x = std::max(max_x, v.x);
                min_y = std::min(min_y, v.y);
                max_y = std::max(max_y, v.y);
            }
        }
    }
    if (!found_svg_quad) {
        return fail("Could not find draw command for dynamic_svg texture");
    }
    float quad_w = max_x - min_x;
    float quad_h = max_y - min_y;
    if (quad_w <= 1.0f || quad_h <= 1.0f || std::fabs(quad_w - quad_h) >= 2.0f || quad_w > 205.0f) {
        return fail("dynamic_svg quad was stretched instead of Fit-scaled (" +
                    std::to_string(quad_w) + "x" + std::to_string(quad_h) + ")");
    }
    std::cout << "[SMOKE] Fit-scaled dynamic_svg quad=" << quad_w << "x" << quad_h << std::endl;
    const char* current_color_icon =
        "data:image/svg+xml;base64,"
        "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9ImN1cnJlbnRDb2xvciIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNMTUgMThsLTYtNiA2LTYiPjwvcGF0aD48L3N2Zz4=";
    CachedImage icon_img = images.resolve(ui, current_color_icon, cui_color{255, 255, 255, 255}, 64);
    if (!icon_img.ready || icon_img.texture == 0 || icon_img.width <= 0 || icon_img.height <= 0) {
        return fail("LunaSVG failed to decode currentColor SVG button icon");
    }
    std::cout << "[SMOKE] LunaSVG rendered dynamic_svg (" << svg_img.width << "x" << svg_img.height
              << ") and currentColor icon (" << icon_img.width << "x" << icon_img.height << ")." << std::endl;

    images.clear(ui);
    cui_destroy(ui);
    cui_sw_destroy(sw);
    bridge.destroy_session();
    std::cout << "[SMOKE PASS] All Kotlin/Native DLL + CUI verification checks passed." << std::endl;
    return 0;
}
