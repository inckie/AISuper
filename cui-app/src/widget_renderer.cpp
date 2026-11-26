#include "widget_renderer.hpp"
#include "core_bridge.hpp"
#include "image_cache.hpp"
#include "style_resolver.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <sstream>
#include <utility>

namespace {

class IdScope {
public:
    IdScope(cui_ctx* ui, const char* id) : ui_(ui) { cui_push_id(ui_, id); }
    IdScope(cui_ctx* ui, int index) : ui_(ui) { cui_push_id_int(ui_, index); }
    ~IdScope() { cui_pop_id(ui_); }
    IdScope(const IdScope&) = delete;
    IdScope& operator=(const IdScope&) = delete;
private:
    cui_ctx* ui_;
};

class BoxScope {
public:
    BoxScope(cui_ctx* ui, const cui_box& opts, bool is_row = false) : ui_(ui) {
        if (is_row) {
            id_ = cui_row_begin(ui_, &opts);
        } else {
            id_ = cui_box_begin(ui_, &opts);
        }
    }
    ~BoxScope() { cui_box_end(ui_); }
    cui_id id() const { return id_; }
    BoxScope(const BoxScope&) = delete;
    BoxScope& operator=(const BoxScope&) = delete;
private:
    cui_ctx* ui_;
    cui_id id_ = 0;
};

class ScrollScope {
public:
    ScrollScope(cui_ctx* ui, const cui_scroll_opts& opts) : ui_(ui) {
        cui_scroll_begin(ui_, &opts);
    }
    ~ScrollScope() { cui_scroll_end(ui_); }
    ScrollScope(const ScrollScope&) = delete;
    ScrollScope& operator=(const ScrollScope&) = delete;
private:
    cui_ctx* ui_;
};

std::string to_lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return s;
}

const char* frame_cstr(cui_ctx* ui, const std::string& s) {
    return s.empty() ? nullptr : cui_frame_strdup(ui, s.c_str());
}

bool has_class_ci(const nlohmann::json& widget, const std::initializer_list<const char*>& names) {
    auto it = widget.find("classes");
    if (it == widget.end() || !it->is_array()) return false;
    for (const auto& cls : *it) {
        if (!cls.is_string()) continue;
        std::string low = to_lower(cls.get<std::string>());
        for (const char* target : names) {
            if (low == target) return true;
        }
    }
    return false;
}

cui_size resolve_width(const nlohmann::json& widget, bool parent_is_row) {
    if (auto wit = widget.find("weight"); wit != widget.end() && wit->is_number() && parent_is_row) {
        float w = wit->get<float>();
        if (w > 0.0f) return CUI_GROW(w);
    }
    if (widget.value("fillMaxSize", false) || widget.value("fillMaxWidth", false)) {
        return CUI_FILL;
    }
    return CUI_FIT;
}

cui_size resolve_height(const nlohmann::json& widget, bool parent_is_row) {
    if (auto wit = widget.find("weight"); wit != widget.end() && wit->is_number() && !parent_is_row) {
        float w = wit->get<float>();
        if (w > 0.0f) return CUI_GROW(w);
    }
    if (widget.value("fillMaxSize", false)) {
        return CUI_FILL;
    }
    return CUI_FIT;
}

void apply_box_style(cui_box& box, const ResolvedStyle& style, const nlohmann::json& widget, bool parent_is_row) {
    box.width = resolve_width(widget, parent_is_row);
    box.height = resolve_height(widget, parent_is_row);

    if (style.padding.has_value()) {
        box.pad = *style.padding;
    }
    if (style.padding_horizontal.has_value()) {
        box.pad_x = *style.padding_horizontal;
    }
    if (style.padding_vertical.has_value()) {
        box.pad_y = *style.padding_vertical;
    }
    if (style.background_color.has_value()) {
        box.bg = *style.background_color;
    }
    if (style.corner_radius.has_value()) {
        box.radius = *style.corner_radius;
    }
    std::string align = to_lower(style.alignment);
    if (align == "center") {
        box.align = CUI_ALIGN_CENTER;
    } else if (align == "start" || align == "left") {
        box.align = CUI_ALIGN_START;
    } else if (align == "end" || align == "right") {
        box.align = CUI_ALIGN_END;
    }
}

nlohmann::json resolve_dynamic_widgets(const nlohmann::json& values, const std::string& key) {
    if (key.empty() || !values.is_object()) return nlohmann::json::array();
    auto it = values.find(key);
    if (it == values.end()) return nlohmann::json::array();
    if (it->is_array()) return *it;
    if (it->is_string()) {
        try {
            auto parsed = nlohmann::json::parse(it->get<std::string>());
            if (parsed.is_array()) return parsed;
        } catch (...) {
        }
    }
    return nlohmann::json::array();
}

struct DropdownItem {
    std::string value;
    std::string label;
};

std::vector<DropdownItem> resolve_dropdown_options(const nlohmann::json& widget, const nlohmann::json& values) {
    std::vector<DropdownItem> out;
    auto parse_arr = [&](const nlohmann::json& arr) {
        if (!arr.is_array()) return;
        for (const auto& item : arr) {
            if (item.is_string()) {
                std::string s = item.get<std::string>();
                if (!s.empty()) out.push_back({s, s});
            } else if (item.is_object()) {
                if (auto vit = item.find("value"); vit != item.end() && vit->is_string()) {
                    std::string val = vit->get<std::string>();
                    std::string lbl = (item.contains("label") && item["label"].is_string())
                        ? item["label"].get<std::string>()
                        : val;
                    if (!val.empty()) out.push_back({val, lbl});
                }
            }
        }
    };

    if (auto vid = widget.find("optionsValueId"); vid != widget.end() && vid->is_string()) {
        std::string key = vid->get<std::string>();
        if (!key.empty() && values.is_object() && values.contains(key)) {
            const auto& raw = values[key];
            if (raw.is_array()) {
                parse_arr(raw);
            } else if (raw.is_string()) {
                try {
                    parse_arr(nlohmann::json::parse(raw.get<std::string>()));
                } catch (...) {
                }
            }
            if (!out.empty()) return out;
        }
    }

    if (auto oit = widget.find("options"); oit != widget.end()) {
        parse_arr(*oit);
    }
    return out;
}

cui_text_align parse_text_align(const std::string& raw) {
    std::string low = to_lower(raw);
    if (low == "center") return CUI_TEXT_CENTER;
    if (low == "right" || low == "end") return CUI_TEXT_RIGHT;
    if (low == "left" || low == "start") return CUI_TEXT_LEFT;
    return CUI_TEXT_START;
}

std::pair<float, float> fit_dimensions(int img_w, int img_h, float max_w, float max_h) {
    float iw = static_cast<float>(std::max(1, img_w));
    float ih = static_cast<float>(std::max(1, img_h));
    float mw = std::max(1.0f, max_w);
    float mh = std::max(1.0f, max_h);
    float scale = std::min(mw / iw, mh / ih);
    return { std::max(1.0f, iw * scale), std::max(1.0f, ih * scale) };
}

} // namespace

WidgetRenderer::WidgetRenderer(CoreBridge& bridge, ImageCache& images)
    : bridge_(bridge), images_(images) {}

void WidgetRenderer::reset_for_session() {
    text_buffers_.clear();
    widget_cui_ids_.clear();
}

bool WidgetRenderer::is_visible(const nlohmann::json& widget, const nlohmann::json& values) {
    auto vit = widget.find("visibilityId");
    if (vit == widget.end() || !vit->is_string()) return true;
    std::string key = vit->get<std::string>();
    if (key.empty() || !values.is_object() || !values.contains(key)) return true;
    return value_as_bool(values[key], true);
}

std::string WidgetRenderer::value_as_string(const nlohmann::json& v) {
    if (v.is_string()) return v.get<std::string>();
    if (v.is_boolean()) return v.get<bool>() ? "true" : "false";
    if (v.is_number_integer()) return std::to_string(v.get<long long>());
    if (v.is_number_float()) {
        std::ostringstream oss;
        oss << v.get<double>();
        return oss.str();
    }
    return "";
}

bool WidgetRenderer::value_as_bool(const nlohmann::json& v, bool fallback) {
    if (v.is_boolean()) return v.get<bool>();
    if (v.is_string()) {
        std::string s = to_lower(v.get<std::string>());
        if (s == "true") return true;
        if (s == "false") return false;
    }
    return fallback;
}

float WidgetRenderer::value_as_float(const nlohmann::json& v, float fallback) {
    if (v.is_number()) return v.get<float>();
    if (v.is_string()) {
        try {
            return std::stof(v.get<std::string>());
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

nlohmann::json WidgetRenderer::parse_json_input(const std::string& input) {
    std::string trimmed = input;
    auto first = trimmed.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return "";
    auto last = trimmed.find_last_not_of(" \t\r\n");
    trimmed = trimmed.substr(first, last - first + 1);
    try {
        return nlohmann::json::parse(trimmed);
    } catch (...) {
        return input;
    }
}

void WidgetRenderer::render_root(cui_ctx* ui, const nlohmann::json& snapshot) {
    if (!ui || !snapshot.is_object()) return;
    auto lit = snapshot.find("layout");
    if (lit == snapshot.end() || !lit->is_object()) return;
    auto root_it = lit->find("layout");
    if (root_it == lit->end() || !root_it->is_object()) return;

    nlohmann::json empty_obj = nlohmann::json::object();
    const nlohmann::json& values = (snapshot.contains("values") && snapshot["values"].is_object())
        ? snapshot["values"]
        : empty_obj;
    const nlohmann::json& style_sheet = (snapshot.contains("styleSheet") && snapshot["styleSheet"].is_object())
        ? snapshot["styleSheet"]
        : empty_obj;

    render_widget(ui, *root_it, values, style_sheet, false);
}

void WidgetRenderer::render_children_list(cui_ctx* ui,
                                          const nlohmann::json& widget,
                                          const nlohmann::json& values,
                                          const nlohmann::json& style_sheet,
                                          bool parent_is_row) {
    int idx = 0;
    if (auto cit = widget.find("children"); cit != widget.end() && cit->is_array()) {
        for (const auto& child : *cit) {
            if (!child.is_object()) continue;
            IdScope id_scope(ui, idx++);
            render_widget(ui, child, values, style_sheet, parent_is_row);
        }
    }
    if (auto dit = widget.find("dynamicChildrenId"); dit != widget.end() && dit->is_string()) {
        nlohmann::json dyn = resolve_dynamic_widgets(values, dit->get<std::string>());
        for (const auto& child : dyn) {
            if (!child.is_object()) continue;
            IdScope id_scope(ui, idx++);
            render_widget(ui, child, values, style_sheet, parent_is_row);
        }
    }
}

void WidgetRenderer::render_widget(cui_ctx* ui,
                                   const nlohmann::json& widget,
                                   const nlohmann::json& values,
                                   const nlohmann::json& style_sheet,
                                   bool parent_is_row) {
    if (!is_visible(widget, values)) return;

    std::string type = widget.value("type", "");
    std::string widget_id = (widget.contains("id") && widget["id"].is_string()) ? widget["id"].get<std::string>() : "";
    ResolvedStyle style = resolve_style_rule(widget, style_sheet);

    if (type == "Column") {
        bool scrollable = widget.value("isScrollable", false);
        cui_box box{};
        box.dir = CUI_COLUMN;
        box.gap = 8.0f;
        apply_box_style(box, style, widget, parent_is_row);
        if (widget.contains("classes") && has_class_ci(widget, {"screen"}) && box.height.kind == CUI_SIZE_FIT) {
            box.height = CUI_FILL;
        }
        BoxScope outer(ui, box);
        if (scrollable) {
            cui_scroll_opts sc{};
            sc.id = widget_id.empty() ? "col_scroll" : frame_cstr(ui, widget_id);
            sc.width = CUI_FILL;
            sc.height = CUI_FILL;
            sc.gap = 8.0f;
            ScrollScope scroll(ui, sc);
            render_children_list(ui, widget, values, style_sheet, false);
        } else {
            render_children_list(ui, widget, values, style_sheet, false);
        }
        return;
    }

    if (type == "Row") {
        bool scrollable = widget.value("isScrollable", false);
        cui_box box{};
        box.dir = CUI_ROW;
        box.align = CUI_ALIGN_CENTER;
        box.gap = 8.0f;
        apply_box_style(box, style, widget, parent_is_row);
        if (scrollable) {
            box.scroll_x = true;
            if (box.width.kind == CUI_SIZE_FIT) box.width = CUI_FILL;
        }
        BoxScope outer(ui, box, true);
        render_children_list(ui, widget, values, style_sheet, true);
        return;
    }

    if (type == "Table") {
        std::vector<nlohmann::json> all_rows;
        if (widget.contains("rows") && widget["rows"].is_array() && !widget["rows"].empty()) {
            for (const auto& r : widget["rows"]) all_rows.push_back(r);
        } else if (widget.contains("children") && widget["children"].is_array()) {
            for (const auto& r : widget["children"]) all_rows.push_back(r);
        }
        std::string dyn_key;
        if (widget.contains("dynamicRowsId") && widget["dynamicRowsId"].is_string()) {
            dyn_key = widget["dynamicRowsId"].get<std::string>();
        } else if (widget.contains("dynamicChildrenId") && widget["dynamicChildrenId"].is_string()) {
            dyn_key = widget["dynamicChildrenId"].get<std::string>();
        }
        for (const auto& r : resolve_dynamic_widgets(values, dyn_key)) {
            all_rows.push_back(r);
        }

        const nlohmann::json cols = (widget.contains("columns") && widget["columns"].is_array())
            ? widget["columns"]
            : nlohmann::json::array();

        bool scrollable = widget.value("isScrollable", false);
        std::string scroll_dir = to_lower(widget.value("scrollDirection", "horizontal"));

        cui_box tbl{};
        tbl.dir = CUI_COLUMN;
        tbl.gap = 2.0f;
        apply_box_style(tbl, style, widget, parent_is_row);
        if (scrollable) {
            if (scroll_dir == "vertical") {
                tbl.scroll_y = true;
            } else if (scroll_dir == "both") {
                tbl.scroll_x = true;
                tbl.scroll_y = true;
            } else {
                tbl.scroll_x = true;
            }
        }
        BoxScope table_scope(ui, tbl);

        int r_idx = 0;
        for (const auto& row : all_rows) {
            if (!row.is_object() || !is_visible(row, values)) continue;
            IdScope r_id(ui, r_idx++);
            ResolvedStyle row_style = resolve_style_rule(row, style_sheet);

            cui_box row_box{};
            row_box.dir = CUI_ROW;
            row_box.align = CUI_ALIGN_CENTER;
            row_box.width = CUI_FILL;
            row_box.gap = 8.0f;
            if (row_style.background_color.has_value()) row_box.bg = *row_style.background_color;
            if (row_style.padding.has_value()) row_box.pad = *row_style.padding;
            if (row_style.padding_horizontal.has_value()) row_box.pad_x = *row_style.padding_horizontal;
            if (row_style.padding_vertical.has_value()) row_box.pad_y = *row_style.padding_vertical;
            if (row_style.corner_radius.has_value()) row_box.radius = *row_style.corner_radius;
            BoxScope row_scope(ui, row_box, true);

            std::vector<nlohmann::json> cells;
            if (row.contains("children") && row["children"].is_array()) {
                for (const auto& c : row["children"]) cells.push_back(c);
            } else {
                cells.push_back(row);
            }

            int c_idx = 0;
            for (const auto& cell : cells) {
                if (!cell.is_object() || !is_visible(cell, values)) {
                    c_idx++;
                    continue;
                }
                IdScope c_id(ui, c_idx);
                cui_box cell_box{};
                cell_box.dir = CUI_COLUMN;
                cell_box.width = CUI_FILL;
                if (c_idx < static_cast<int>(cols.size()) && cols[c_idx].is_object()) {
                    const auto& col_def = cols[c_idx];
                    if (col_def.contains("width") && col_def["width"].is_number()) {
                        cell_box.width = CUI_PX(col_def["width"].get<float>());
                    } else if (col_def.contains("weight") && col_def["weight"].is_number()) {
                        float w = col_def["weight"].get<float>();
                        if (w > 0.0f) cell_box.width = CUI_GROW(w);
                    }
                }
                BoxScope cell_scope(ui, cell_box);
                render_widget(ui, cell, values, style_sheet, false);
                c_idx++;
            }
        }
        return;
    }

    if (type == "TableRow") {
        cui_box row_box{};
        row_box.dir = CUI_ROW;
        row_box.align = CUI_ALIGN_CENTER;
        row_box.gap = 8.0f;
        apply_box_style(row_box, style, widget, parent_is_row);
        BoxScope row_scope(ui, row_box, true);
        render_children_list(ui, widget, values, style_sheet, true);
        return;
    }

    if (type == "Text") {
        std::string text = widget.value("text", "");
        if (!widget_id.empty() && values.is_object() && values.contains(widget_id)) {
            std::string bound = value_as_string(values[widget_id]);
            if (!bound.empty() || values[widget_id].is_string()) {
                text = bound;
            }
        }

        cui_text_opts opts{};
        if (style.font_size.has_value()) opts.size = *style.font_size;
        if (style.text_color.has_value()) opts.color = *style.text_color;
        if (to_lower(style.font_weight) == "bold" || to_lower(style.font_weight) == "semibold" ||
            to_lower(style.font_weight) == "medium") {
            opts.font = CUI_FONT_MEDIUM;
        }
        std::string align_raw = widget.contains("align") && widget["align"].is_string()
            ? widget["align"].get<std::string>()
            : style.text_align;
        if (!align_raw.empty()) {
            opts.align = parse_text_align(align_raw);
        }
        opts.nowrap = style.no_break || widget.value("noBreak", false);
        opts.width = resolve_width(widget, parent_is_row);

        bool needs_box = style.background_color.has_value() || style.padding.has_value() ||
                         style.padding_horizontal.has_value() || style.padding_vertical.has_value() ||
                         style.corner_radius.has_value();
        if (needs_box) {
            cui_box wrap{};
            wrap.dir = CUI_COLUMN;
            apply_box_style(wrap, style, widget, parent_is_row);
            BoxScope scope(ui, wrap);
            opts.width = CUI_FILL;
            cui_text_ex(ui, text.c_str(), &opts);
        } else {
            cui_text_ex(ui, text.c_str(), &opts);
        }
        return;
    }

    if (type == "TextField") {
        std::string key = widget_id.empty() ? "textfield_anon" : widget_id;
        auto& buf = text_buffers_[key];
        if (buf.size() < 4096) {
            buf.assign(4096, '\0');
        }

        cui_id field_cui_id = cui_make_id(ui, key.c_str());
        bool is_focused = (cui_focused_id(ui) == field_cui_id);
        if (!is_focused && !widget_id.empty() && values.is_object() && values.contains(widget_id)) {
            std::string val_str = value_as_string(values[widget_id]);
            if (val_str != buf.data()) {
                std::fill(buf.begin(), buf.end(), '\0');
                size_t n = std::min(val_str.size(), buf.size() - 1);
                std::memcpy(buf.data(), val_str.data(), n);
            }
        }

        std::string hint = widget.value("hint", "");
        cui_input_opts opts{};
        opts.placeholder = frame_cstr(ui, hint);
        opts.password = widget.value("password", false);
        opts.width = resolve_width(widget, parent_is_row);
        if (opts.width.kind == CUI_SIZE_FIT) opts.width = CUI_FILL;

        bool single_line = widget.value("singleLine", true);
        int flags = 0;
        if (single_line) {
            flags = cui_input_ex(ui, key.c_str(), buf.data(), buf.size(), &opts);
        } else {
            opts.height = 96.0f;
            flags = cui_textarea_ex(ui, key.c_str(), buf.data(), buf.size(), &opts);
        }

        if (!widget_id.empty()) {
            widget_cui_ids_[widget_id] = cui_last_id(ui);
        }

        if ((flags & CUI_CHANGED) && !widget_id.empty()) {
            nlohmann::json val = parse_json_input(std::string(buf.data()));
            bridge_.set_value(widget_id, val.dump());
        }
        if (flags & CUI_SUBMITTED) {
            std::string ime = to_lower(widget.value("imeAction", ""));
            std::string next_focus = widget.contains("nextFocusId") && widget["nextFocusId"].is_string()
                ? widget["nextFocusId"].get<std::string>()
                : "";
            if (ime == "next" && !next_focus.empty()) {
                auto fit = widget_cui_ids_.find(next_focus);
                if (fit != widget_cui_ids_.end() && fit->second != 0) {
                    cui_request_component_focus(ui, fit->second);
                }
            } else {
                std::string action_name = (widget.contains("onImeAction") && widget["onImeAction"].is_string())
                    ? widget["onImeAction"].get<std::string>()
                    : widget.value("imeAction", "");
                if (!action_name.empty()) {
                    bridge_.send_action(action_name, "[]");
                }
            }
        }
        return;
    }

    if (type == "Button") {
        std::string label = widget.value("text", "Button");
        std::string icon_url = (widget.contains("icon") && widget["icon"].is_string())
            ? widget["icon"].get<std::string>()
            : "";
        std::string action = widget.value("action", "");
        std::string args_json = (widget.contains("actionArgs") && widget["actionArgs"].is_array())
            ? widget["actionArgs"].dump()
            : "[]";

        cui_button_opts opts{};
        if (has_class_ci(widget, {"destructive", "danger", "delete"})) {
            opts.variant = CUI_BUTTON_DANGER;
        } else if (has_class_ci(widget, {"primary", "accent"})) {
            opts.variant = CUI_BUTTON_PRIMARY;
        } else if (has_class_ci(widget, {"outline"})) {
            opts.variant = CUI_BUTTON_OUTLINE;
        } else if (has_class_ci(widget, {"ghost"})) {
            opts.variant = CUI_BUTTON_GHOST;
        } else if (style.container_color.has_value()) {
            opts.variant = CUI_BUTTON_PRIMARY;
        } else {
            opts.variant = CUI_BUTTON_SECONDARY;
        }

        if (widget.value("fillMaxWidth", false)) {
            opts.full_width = true;
            opts.width = CUI_FILL;
        } else {
            opts.width = resolve_width(widget, parent_is_row);
        }
        if (style.text_color.has_value()) {
            opts.color = *style.text_color;
        }

        if (!icon_url.empty()) {
            cui_color icon_fg = cui_color_of(
                ui,
                style.text_color.value_or(
                    (opts.variant == CUI_BUTTON_PRIMARY || opts.variant == CUI_BUTTON_DANGER)
                        ? CUI_COLOR_ACCENT_FOREGROUND
                        : CUI_COLOR_FOREGROUND));
            CachedImage icon_img = images_.resolve(ui, icon_url, icon_fg, 64);
            bool icon_on_right = (to_lower(style.icon_position) == "end" ||
                                  to_lower(style.icon_position) == "right" ||
                                  has_class_ci(widget, {"icon_right"}));

            cui_box row{};
            row.dir = CUI_ROW;
            row.align = CUI_ALIGN_CENTER;
            row.gap = 6.0f;
            row.width = opts.width;
            BoxScope bs(ui, row, true);

            auto draw_icon = [&]() {
                if (icon_img.ready && icon_img.texture) {
                    auto [iw, ih] = fit_dimensions(icon_img.width, icon_img.height, 18.0f, 18.0f);
                    cui_image_opts iopts{};
                    iopts.width = CUI_PX(iw);
                    iopts.height = CUI_PX(ih);
                    iopts.radius = 4.0f;
                    cui_image_ex(ui, icon_img.texture, &iopts);
                }
            };

            if (!icon_on_right) draw_icon();
            if (!style.icon_only || !icon_img.ready) {
                if (cui_button_ex(ui, label.c_str(), &opts)) {
                    if (!action.empty()) bridge_.send_action(action, args_json);
                }
            } else if (cui_button_ex(ui, "", &opts)) {
                if (!action.empty()) bridge_.send_action(action, args_json);
            }
            if (icon_on_right) draw_icon();
        } else {
            if (cui_button_ex(ui, label.c_str(), &opts)) {
                if (!action.empty()) {
                    bridge_.send_action(action, args_json);
                }
            }
        }
        return;
    }

    if (type == "Image") {
        std::string source;
        if (!widget_id.empty() && values.is_object() && values.contains(widget_id)) {
            source = value_as_string(values[widget_id]);
        }
        if (source.empty() && widget.contains("data") && widget["data"].is_string()) {
            source = widget["data"].get<std::string>();
        }
        if (source.empty()) {
            source = widget.value("url", "");
        }
        if (source.empty()) return;

        cui_color fg = cui_color_of(ui, style.text_color.value_or(CUI_COLOR_FOREGROUND));
        CachedImage img = images_.resolve(ui, source, fg, 512);
        if (!img.ready || !img.texture) {
            if (!img.failed) {
                cui_spinner_opts sp{};
                sp.style = CUI_SPINNER_COMET;
                cui_spinner_ex(ui, 24.0f, &sp);
            }
            return;
        }

        cui_image_opts opts{};
        bool fill_w = widget.value("fillMaxWidth", false) || widget.value("fillMaxSize", false);
        bool has_row_weight = parent_is_row && widget.contains("weight") && widget["weight"].is_number() &&
                              widget["weight"].get<float>() > 0.0f;
        bool flexible_w = fill_w || has_row_weight;
        float target_h = widget.contains("height") && widget["height"].is_number()
            ? widget["height"].get<float>()
            : 0.0f;

        cui_box wrap{};
        wrap.id = "img_wrap";
        wrap.dir = CUI_ROW;
        wrap.align = CUI_ALIGN_CENTER;
        std::string align = to_lower(style.alignment);
        if (align == "start" || align == "left") {
            wrap.justify = CUI_JUSTIFY_START;
        } else if (align == "end" || align == "right") {
            wrap.justify = CUI_JUSTIFY_END;
        } else {
            wrap.justify = CUI_JUSTIFY_CENTER;
        }

        float draw_w = 0.0f;
        float draw_h = 0.0f;
        if (flexible_w) {
            float max_h = (target_h > 0.0f) ? target_h : 200.0f;
            wrap.width = resolve_width(widget, parent_is_row);
            wrap.height = CUI_PX(max_h);
            cui_id wrap_id = cui_make_id(ui, "img_wrap");
            cui_rect prev_rect = cui_get_rect(ui, wrap_id);
            float avail_w = prev_rect.w;
            if (avail_w <= 1.0f) {
                avail_w = max_h;
                cui_request_redraw(ui);
            }
            auto [fw, fh] = fit_dimensions(img.width, img.height, avail_w, max_h);
            draw_w = fw;
            draw_h = fh;
        } else {
            float box_size = (target_h > 0.0f) ? target_h : 64.0f;
            wrap.width = CUI_PX(box_size);
            wrap.height = CUI_PX(box_size);
            auto [fw, fh] = fit_dimensions(img.width, img.height, box_size, box_size);
            draw_w = fw;
            draw_h = fh;
        }

        BoxScope bs(ui, wrap, true);
        opts.width = CUI_PX(draw_w);
        opts.height = CUI_PX(draw_h);
        if (style.corner_radius.has_value()) {
            opts.radius = *style.corner_radius;
        }
        cui_image_ex(ui, img.texture, &opts);
        return;
    }

    if (type == "AudioPlayer") {
        std::string player = widget.value("player", "");
        std::string title = widget.value("title", "Audio Player");
        std::string prefix = player + ".media";
        std::string phase = (values.is_object() && values.contains(prefix + ".state"))
            ? value_as_string(values[prefix + ".state"])
            : "idle";
        std::string pos = (values.is_object() && values.contains(prefix + ".positionMs"))
            ? value_as_string(values[prefix + ".positionMs"])
            : "0";

        cui_box card{};
        card.dir = CUI_COLUMN;
        card.gap = 8.0f;
        card.pad = 12.0f;
        card.radius = 10.0f;
        card.border = 1.0f;
        card.bg = CUI_COLOR_SURFACE_MUTED;
        card.width = resolve_width(widget, parent_is_row);
        BoxScope scope(ui, card);

        std::string line1 = title + ": " + phase;
        std::string line2 = "Position: " + pos + "ms";
        cui_text_ex(ui, line1.c_str(), nullptr);
        cui_text_opts muted{};
        muted.color = CUI_COLOR_TEXT_SECONDARY;
        cui_text_ex(ui, line2.c_str(), &muted);

        cui_box row{};
        row.dir = CUI_ROW;
        row.gap = 8.0f;
        BoxScope btn_row(ui, row, true);
        if (cui_button_ex(ui, "Play", nullptr)) {
            bridge_.module_command("audioPlayer", player, "play", "[]");
        }
        if (cui_button_ex(ui, "Pause", nullptr)) {
            bridge_.module_command("audioPlayer", player, "pause", "[]");
        }
        if (cui_button_ex(ui, "Stop", nullptr)) {
            bridge_.module_command("audioPlayer", player, "stop", "[]");
        }
        return;
    }

    if (type == "Dropdown") {
        std::vector<DropdownItem> items = resolve_dropdown_options(widget, values);
        std::string selected_val;
        if (!widget_id.empty() && values.is_object() && values.contains(widget_id)) {
            selected_val = value_as_string(values[widget_id]);
        }

        int sel_idx = -1;
        std::vector<const char*> labels;
        labels.reserve(items.size());
        for (int i = 0; i < static_cast<int>(items.size()); ++i) {
            labels.push_back(items[i].label.c_str());
            if (items[i].value == selected_val) {
                sel_idx = i;
            }
        }

        std::string key = widget_id.empty() ? "dropdown_anon" : widget_id;
        std::string hint = widget.value("hint", "Select");
        cui_select_opts opts{};
        opts.placeholder = frame_cstr(ui, hint);
        opts.width = resolve_width(widget, parent_is_row);
        if (opts.width.kind == CUI_SIZE_FIT) opts.width = CUI_FILL;

        if (cui_select_ex(ui, key.c_str(), labels.data(), static_cast<int>(labels.size()), &sel_idx, &opts)) {
            if (sel_idx >= 0 && sel_idx < static_cast<int>(items.size())) {
                const std::string& chosen = items[sel_idx].value;
                if (!widget_id.empty()) {
                    bridge_.set_value(widget_id, nlohmann::json(chosen).dump());
                }
                if (widget.contains("onChangeAction") && widget["onChangeAction"].is_string()) {
                    std::string act = widget["onChangeAction"].get<std::string>();
                    if (!act.empty()) {
                        bridge_.send_action(act, nlohmann::json::array({chosen}).dump());
                    }
                }
            }
        }
        return;
    }

    if (type == "Switch") {
        bool fallback = widget.value("checked", false);
        bool checked = (!widget_id.empty() && values.is_object() && values.contains(widget_id))
            ? value_as_bool(values[widget_id], fallback)
            : fallback;
        std::string label = widget.value("text", "");
        std::string keyed_label = widget_id.empty() ? label : (label + "##" + widget_id);

        if (cui_switch_ex(ui, keyed_label.c_str(), &checked, nullptr)) {
            if (!widget_id.empty()) {
                bridge_.set_value(widget_id, checked ? "true" : "false");
            }
            if (widget.contains("onChangeAction") && widget["onChangeAction"].is_string()) {
                std::string act = widget["onChangeAction"].get<std::string>();
                if (!act.empty()) {
                    nlohmann::json args = (widget.contains("actionArgs") && widget["actionArgs"].is_array())
                        ? widget["actionArgs"]
                        : nlohmann::json::array();
                    args.push_back(checked);
                    bridge_.send_action(act, args.dump());
                }
            }
        }
        return;
    }

    if (type == "Spinner") {
        cui_spinner_opts opts{};
        opts.style = CUI_SPINNER_COMET;
        if (style.text_color.has_value()) opts.color = *style.text_color;
        cui_spinner_ex(ui, 24.0f, &opts);
        return;
    }

    if (type == "Progress") {
        bool indet = widget.value("indeterminate", false);
        float progress = 0.0f;
        bool has_val = false;
        if (widget.contains("progressId") && widget["progressId"].is_string()) {
            std::string pid = widget["progressId"].get<std::string>();
            if (!pid.empty() && values.is_object() && values.contains(pid)) {
                progress = value_as_float(values[pid], 0.0f);
                has_val = true;
            }
        }
        if (!has_val && widget.contains("progress") && widget["progress"].is_number()) {
            progress = widget["progress"].get<float>();
            has_val = true;
        }
        cui_progress_opts opts{};
        opts.indeterminate = indet || !has_val;
        opts.width = resolve_width(widget, parent_is_row);
        if (opts.width.kind == CUI_SIZE_FIT) opts.width = CUI_FILL;
        cui_progress_ex(ui, std::clamp(progress, 0.0f, 1.0f), &opts);
        return;
    }

    if (type == "Slider") {
        float min_v = widget.value("min", 0.0f);
        float max_v = widget.value("max", 100.0f);
        if (max_v <= min_v) max_v = 100.0f;
        float step = (widget.contains("step") && widget["step"].is_number()) ? widget["step"].get<float>() : 0.0f;
        float fallback = (widget.contains("value") && widget["value"].is_number()) ? widget["value"].get<float>() : min_v;
        float val = (!widget_id.empty() && values.is_object() && values.contains(widget_id))
            ? value_as_float(values[widget_id], fallback)
            : fallback;
        val = std::clamp(val, min_v, max_v);

        std::string key = widget_id.empty() ? "Slider" : ("##" + widget_id);
        cui_slider_opts opts{};
        opts.step = step;
        opts.show_value = true;
        opts.width = resolve_width(widget, parent_is_row);
        if (opts.width.kind == CUI_SIZE_FIT) opts.width = CUI_FILL;

        if (cui_slider_ex(ui, key.c_str(), &val, min_v, max_v, &opts)) {
            if (!widget_id.empty()) {
                bridge_.set_value(widget_id, nlohmann::json(val).dump());
            }
            if (widget.contains("onChangeAction") && widget["onChangeAction"].is_string()) {
                std::string act = widget["onChangeAction"].get<std::string>();
                if (!act.empty()) {
                    nlohmann::json args = (widget.contains("actionArgs") && widget["actionArgs"].is_array())
                        ? widget["actionArgs"]
                        : nlohmann::json::array();
                    args.push_back(val);
                    bridge_.send_action(act, args.dump());
                }
            }
        }
        return;
    }
}
