#pragma once

#include <cui/cui.h>
#include <nlohmann/json.hpp>

#include <string>
#include <unordered_map>
#include <vector>

class CoreBridge;
class ImageCache;

class WidgetRenderer {
public:
    WidgetRenderer(CoreBridge& bridge, ImageCache& images);

    void reset_for_session();

    void render_root(cui_ctx* ui, const nlohmann::json& snapshot);

private:
    void render_widget(cui_ctx* ui,
                       const nlohmann::json& widget,
                       const nlohmann::json& values,
                       const nlohmann::json& style_sheet,
                       bool parent_is_row);

    void render_children_list(cui_ctx* ui,
                              const nlohmann::json& widget,
                              const nlohmann::json& values,
                              const nlohmann::json& style_sheet,
                              bool parent_is_row);

    static bool is_visible(const nlohmann::json& widget, const nlohmann::json& values);
    static std::string value_as_string(const nlohmann::json& v);
    static bool value_as_bool(const nlohmann::json& v, bool fallback);
    static float value_as_float(const nlohmann::json& v, float fallback);
    static nlohmann::json parse_json_input(const std::string& input);

    CoreBridge& bridge_;
    ImageCache& images_;

    std::unordered_map<std::string, std::vector<char>> text_buffers_;
    std::unordered_map<std::string, cui_id> widget_cui_ids_;
};
