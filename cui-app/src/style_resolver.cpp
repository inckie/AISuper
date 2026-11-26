#include "style_resolver.hpp"

#include <algorithm>
#include <cctype>

namespace {

std::string to_lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return s;
}

uint8_t parse_hex_byte(const char* p) {
    auto hex = [](char c) -> uint8_t {
        if (c >= '0' && c <= '9') return static_cast<uint8_t>(c - '0');
        if (c >= 'a' && c <= 'f') return static_cast<uint8_t>(10 + (c - 'a'));
        if (c >= 'A' && c <= 'F') return static_cast<uint8_t>(10 + (c - 'A'));
        return 0;
    };
    return static_cast<uint8_t>((hex(p[0]) << 4) | hex(p[1]));
}

void merge_rule(ResolvedStyle& dst, const nlohmann::json& rule) {
    if (!rule.is_object()) return;

    auto read_color = [&](const char* key, std::optional<cui_color>& target) {
        auto it = rule.find(key);
        if (it != rule.end() && it->is_string()) {
            if (auto c = parse_color(it->get<std::string>())) {
                target = c;
            }
        }
    };
    auto read_float = [&](const char* key, std::optional<float>& target) {
        auto it = rule.find(key);
        if (it != rule.end() && it->is_number()) {
            target = it->get<float>();
        }
    };
    auto read_string = [&](const char* key, std::string& target) {
        auto it = rule.find(key);
        if (it != rule.end() && it->is_string()) {
            target = it->get<std::string>();
        }
    };

    read_color("textColor", dst.text_color);
    read_color("backgroundColor", dst.background_color);
    read_color("containerColor", dst.container_color);
    read_float("padding", dst.padding);
    read_float("paddingHorizontal", dst.padding_horizontal);
    read_float("paddingVertical", dst.padding_vertical);
    read_float("cornerRadius", dst.corner_radius);
    read_float("fontSize", dst.font_size);
    read_string("fontWeight", dst.font_weight);
    read_string("textAlign", dst.text_align);
    read_string("alignment", dst.alignment);
    read_string("iconPosition", dst.icon_position);
    read_string("overflow", dst.overflow);

    if (auto it = rule.find("iconOnly"); it != rule.end() && it->is_boolean()) {
        dst.icon_only = it->get<bool>();
    }
    if (auto it = rule.find("noBreak"); it != rule.end() && it->is_boolean()) {
        dst.no_break = it->get<bool>();
    }
    if (auto it = rule.find("noWrap"); it != rule.end() && it->is_boolean()) {
        dst.no_break = dst.no_break || it->get<bool>();
    }
    if (auto it = rule.find("whiteSpace"); it != rule.end() && it->is_string()) {
        std::string ws = to_lower(it->get<std::string>());
        if (ws == "nowrap" || ws == "no-wrap") {
            dst.no_break = true;
        }
    }
    if (auto it = rule.find("maxLines"); it != rule.end() && it->is_number_integer()) {
        dst.max_lines = it->get<int>();
    }
}

std::optional<cui_color> resolve_token_color(const nlohmann::json& tokens, const char* key) {
    if (!tokens.is_object()) return std::nullopt;
    if (auto it = tokens.find(key); it != tokens.end() && it->is_string()) {
        if (auto c = parse_color(it->get<std::string>())) return c;
    }
    if (auto vit = tokens.find("values"); vit != tokens.end() && vit->is_object()) {
        if (auto it = vit->find(key); it != vit->end() && it->is_string()) {
            if (auto c = parse_color(it->get<std::string>())) return c;
        }
    }
    return std::nullopt;
}

} // namespace

std::optional<cui_color> parse_color(const std::string& raw) {
    if (raw.empty()) return std::nullopt;
    std::string lower = to_lower(raw);
    if (lower == "transparent") {
        return cui_color{0, 0, 0, 0};
    }
    std::string hex = (!raw.empty() && raw[0] == '#') ? raw.substr(1) : raw;
    if (hex.size() != 6 && hex.size() != 8) return std::nullopt;
    for (char c : hex) {
        if (!std::isxdigit(static_cast<unsigned char>(c))) return std::nullopt;
    }
    if (hex.size() == 6) {
        uint8_t r = parse_hex_byte(hex.data());
        uint8_t g = parse_hex_byte(hex.data() + 2);
        uint8_t b = parse_hex_byte(hex.data() + 4);
        return cui_color{r, g, b, 255};
    }
    // Kotlin/AISuper uses #AARRGGBB
    uint8_t a = parse_hex_byte(hex.data());
    uint8_t r = parse_hex_byte(hex.data() + 2);
    uint8_t g = parse_hex_byte(hex.data() + 4);
    uint8_t b = parse_hex_byte(hex.data() + 6);
    return cui_color{r, g, b, a};
}

ResolvedStyle resolve_style_rule(const nlohmann::json& widget, const nlohmann::json& style_sheet) {
    ResolvedStyle out;
    if (!style_sheet.is_object()) return out;

    std::string type = widget.value("type", "");
    if (auto dit = style_sheet.find("defaults"); dit != style_sheet.end() && dit->is_object()) {
        if (auto rit = dit->find(type); rit != dit->end()) {
            merge_rule(out, *rit);
        }
    }

    if (auto cit = widget.find("classes"); cit != widget.end() && cit->is_array()) {
        auto classes_obj = style_sheet.find("classes");
        if (classes_obj != style_sheet.end() && classes_obj->is_object()) {
            for (const auto& cls : *cit) {
                if (!cls.is_string()) continue;
                std::string name = cls.get<std::string>();
                if (auto rit = classes_obj->find(name); rit != classes_obj->end()) {
                    merge_rule(out, *rit);
                }
                std::string low = to_lower(name);
                if (low == "no_break" || low == "no-break" || low == "nowrap" || low == "no-wrap") {
                    out.no_break = true;
                }
            }
        }
    }

    bool needs_action = (type == "Button" || type == "Dropdown" || type == "Switch" || type == "Slider");
    if (needs_action && !out.container_color.has_value()) {
        bool destructive = false;
        if (auto cit = widget.find("classes"); cit != widget.end() && cit->is_array()) {
            for (const auto& cls : *cit) {
                if (!cls.is_string()) continue;
                std::string low = to_lower(cls.get<std::string>());
                if (low == "destructive" || low == "danger" || low == "delete") {
                    destructive = true;
                    break;
                }
            }
        }
        auto tit = style_sheet.find("tokens");
        if (tit != style_sheet.end()) {
            out.container_color = resolve_token_color(
                *tit, destructive ? "destructiveColor" : "accentColor");
        }
    }

    return out;
}

void apply_theme_from_stylesheet(cui_ctx* ui, const nlohmann::json& style_sheet, std::string& in_out_theme_key) {
    if (!ui || !style_sheet.is_object()) return;

    std::string scheme = to_lower(style_sheet.value("scheme", "light"));
    std::string name = style_sheet.value("name", "");
    std::string accent_str;
    std::string danger_str;
    if (auto tit = style_sheet.find("tokens"); tit != style_sheet.end() && tit->is_object()) {
        if (auto ac = resolve_token_color(*tit, "accentColor")) {
            accent_str = std::to_string(ac->r) + "," + std::to_string(ac->g) + "," + std::to_string(ac->b);
        }
        if (auto dc = resolve_token_color(*tit, "destructiveColor")) {
            danger_str = std::to_string(dc->r) + "," + std::to_string(dc->g) + "," + std::to_string(dc->b);
        }
    }

    std::string key = scheme + "|" + name + "|" + accent_str + "|" + danger_str;
    if (key == in_out_theme_key) return;
    in_out_theme_key = key;

    cui_set_theme(ui, (scheme == "dark") ? CUI_THEME_DARK : CUI_THEME_LIGHT);

    cui_theme* theme = cui_theme_get(ui);
    if (theme) {
        if (auto tit = style_sheet.find("tokens"); tit != style_sheet.end()) {
            if (auto ac = resolve_token_color(*tit, "accentColor")) {
                theme->colors[CUI_TOKEN_ACCENT] = *ac;
                theme->colors[CUI_TOKEN_ACCENT_STRONG] = *ac;
            }
            if (auto dc = resolve_token_color(*tit, "destructiveColor")) {
                theme->colors[CUI_TOKEN_DANGER] = *dc;
            }
        }
    }
}
