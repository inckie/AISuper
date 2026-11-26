#pragma once

#include <cui/cui.h>
#include <nlohmann/json.hpp>

#include <optional>
#include <string>

struct ResolvedStyle {
    std::optional<cui_color> text_color;
    std::optional<cui_color> background_color;
    std::optional<cui_color> container_color;
    std::optional<float> padding;
    std::optional<float> padding_horizontal;
    std::optional<float> padding_vertical;
    std::optional<float> corner_radius;
    std::optional<float> font_size;
    std::string font_weight;
    std::string text_align;
    std::string alignment;
    std::string icon_position;
    bool icon_only = false;
    bool no_break = false;
    std::optional<int> max_lines;
    std::string overflow;
};

std::optional<cui_color> parse_color(const std::string& raw);
ResolvedStyle resolve_style_rule(const nlohmann::json& widget, const nlohmann::json& style_sheet);
void apply_theme_from_stylesheet(cui_ctx* ui, const nlohmann::json& style_sheet, std::string& in_out_theme_key);
