---
categories:
- applet-developer
created: '2026-06-20T04:50:46.207559+00:00'
id: dynamic-interfaces-skill
modified: '2026-07-02T01:12:17.288387+00:00'
tags:
- skills
- ui
- layout
- widgets
- design-patterns
title: Dynamic Interfaces Skill
type: leaf
---

# Dynamic Interfaces Skill (AISuper)

This skill documents how to build interactive, responsive, and growable user interfaces in AISuper applets, detailing all supported layout structures, widget properties, reactive data bindings, and scripting patterns.

-----

## 1. UI Layout Widgets Reference

AISuper layouts are defined in JSON. **CRITICAL:** Every layout JSON file MUST have a root object containing a single `"layout"` key. The value of this key is the root widget (typically a `Column` or `Row`). If you omit the `"layout"` root key, the applet will fail to parse the layout.

### Example Complete Layout File
```json
{
  "layout": {
    "type": "Column",
    "fillMaxSize": true,
    "children": [
      {
        "type": "Text",
        "text": "Hello World",
        "classes": ["header_text"]
      }
    ]
  }
}
```

Every widget inside the layout inherits from `WidgetBase` and supports standard attributes:

### Common Widget Properties (`WidgetBase`)
* `type`: (String, Required) One of the widget types below.
* `id`: (String, Optional) State binding key. If a widget has an `id`, its value is reactively synchronized with the feature state.
* `visibilityId`: (String, Optional) State binding key for visibility. If the value of this key is `false`, the widget is not rendered. Defaults to `true` if null or key is missing.
* `fillMaxWidth`: (Boolean, Optional) Forces the widget to stretch to the parent's width.
* `fillMaxSize`: (Boolean, Optional) Forces the widget to fill the entire parent space.
* `weight`: (Float, Optional) Proportion of layout space the widget should occupy compared to siblings in a `Row` or `Column`.
* `classes`: (Array of Strings, Optional) List of class names defined in the active stylesheet (e.g., `["screen", "message_bubble"]`) to apply margins, paddings, colors, alignment, and typography.
* **Note:** The `modifier` property is **NOT** supported by the Kotlin layout renderer. All spacing and styling must be defined in a `StyleSheet` and applied using `classes` or defaults.

### Supported Widgets

For a complete list of all supported widgets, their properties, and JSON code samples, please refer to the [[widgets-reference|Widgets Reference]] article.

-----

## 2. Interface Design Patterns

To build responsive, responsive UI dashboards, use the following two patterns:

### Pattern A: Static Layout with Reactive Value Bindings (Highly Recommended)
For fixed dashboards (like a weather dashboard or profile forms), define a static layout template in JSON and assign unique `id`s to the widgets. 

The Android layout renderer automatically substitutes the text values in Compose.

#### Why it's recommended:
1. **Zero Serialization Overhead**: Avoids passing massive JSON widget payloads through state.
2. **Reliable Updates**: Instantly reactive when calling `setValue(id, value)`.
3. **Muted Buffer Truncation**: High-performance rendering without running into string size limitations on state bridge logs.

-----

### Pattern B: Dynamic Widget List Creation (Growable Lists)
For lists that grow or shrink dynamically (like a chat interface, list of items, or dynamic results), use the `dynamicChildrenId` property on a **`Column`** or **`Row`** widget.

#### Implementation Steps:
1. Define a `Column` in layout JSON and assign it a `dynamicChildrenId`:
   ```json
   {
     "type": "Column",
     "dynamicChildrenId": "chat_message_list",
     "isScrollable": true,
     "weight": 1
   }
   ```
2. In the Javascript script, construct an array of widget JSON definitions and pass it **directly** to `setValue` (do **not** call `JSON.stringify`):
   ```javascript
   function refreshMessages() {
       var widgets = [];
       for (var i = 0; i < messages.length; i++) {
           widgets.push({
               "type": "Text",
               "text": messages[i].sender + ": " + messages[i].text,
               "fillMaxWidth": true
           });
       }
       // Pass the actual array object directly
       setValue("chat_message_list", widgets);
   }
   ```

> [!TIP]
> Both **`Column`** and **`Row`** support `dynamicChildrenId` for generating lists of widgets programmatically. This is extremely useful for a vertical message list (in a `Column`) and a horizontal hourly weather forecast (in a `Row`).

> [!CAUTION]
> **Scrollable Nesting Conflict (Jetpack Compose Crash)**
> Do **NOT** nest scrollable widgets (e.g., a `Column` or `Row` with `isScrollable: true`) inside another scrollable widget scrolling in the same direction.
> Doing so causes an infinite measurement constraint loop in Jetpack Compose, which will instantly crash the running applet server with an `IllegalStateException`.
> * **Rules of thumb**:
>   * If the root `Column` is scrollable (`isScrollable: true`), all nested vertical `Column` children must have scroll disabled (`isScrollable: false` or omitted).
>   * Nesting a horizontal scrollable `Row` inside a vertical scrollable `Column` is safe because they scroll in different directions.

> [!CAUTION]
> **Weight and fillMaxWidth/fillMaxSize Conflict**
> Do **NOT** use `fillMaxWidth: true` (or `fillMaxSize: true`) on a child widget that has `weight` set inside a `Row` or `Column`.
> In Compose, `weight` automatically handles stretching and filling the allocated space by default. Explicitly adding `fillMaxWidth: true` to a weighted child conflicts with the weight calculation and can disrupt the layout or break proportional child distributions (e.g., preventing items in a `Row` from aligning correctly to their weighted ratios).

-----

### Pattern C: Multi-Layout UI Decomposition (Complex Features)
For complex features, rather than building a single massive JSON layout and attempting to toggle the visibility of different sections, you should decompose the UI into multiple distinct layout files.

For example, a chat feature or remote management tool might be broken down into:
- `settings_layout.json`: A screen for entering API keys, host addresses, or credentials.
- `main_layout.json`: The primary view (e.g., the active chat view, or a list of active connections).
- `details_layout.json`: A drill-down view for inspecting a specific item.

#### Implementation Strategy:
1. Define each distinct screen as a separate JSON layout file in your applet's `files/` directory.
2. In your `applet.json`, declare these layouts under a `layouts` dictionary instead of using a single `layout` field:
   ```json
   "my_feature": {
     "layouts": {
       "settings": "files/settings_layout.json",
       "main": "files/main_layout.json"
     },
     "script": "files/feature_script.js"
   }
   ```
3. In your feature script, use the asynchronous `setLayout(layoutName)` function to seamlessly transition the entire widget tree (e.g., transitioning from settings to main after a successful connection).

-----

## 3. Dropdowns, Switches, and Sliders (Action Notification)

When designing an interactive selector or control (e.g. Unit Switcher, Dark Mode, Volume/Brightness Sliders):
* **Switch, Dropdown, and Slider widgets** natively support `onChangeAction`. Changing the state of a Switch, selecting an option in a Dropdown, or adjusting a Slider immediately triggers the JS action, allowing you to re-evaluate and redraw the UI without needing explicit subscriptions. Note that a Switch or Slider passes its updated value to the callback along with any defined `actionArgs`.

#### Layout Dropdown definition:
```json
{
  "layout": {
    "type": "Dropdown",
    "id": "unit_selection",
    "hint": "Units",
    "onChangeAction": "onUnitsChanged",
    "options": [
      { "value": "metric", "label": "Metric" },
      { "value": "imperial", "label": "Imperial" }
    ]
  }
}
```

#### Script Handling (Safe State Pattern):
Due to parameter type differences between Ktor MCP models and JSON schemas, do not pass action arguments to JS callbacks. Instead, read the state directly from the bound key:
```javascript
function onUnitsChanged() {
    // Read the value directly from the state value key
    var newUnits = getValue("unit_selection") || "metric";
    updateUI(newUnits);
}
```

-----

## 4. Layout Design Patterns & Utility Styles

Modern interfaces in AISuper use "orthogonal" styles—modular utility classes that can be combined to create complex layouts without hardcoding values in JSON.

### A. Decorative Utility Styles
Stylesheets should provide utility classes for quick layout adjustments:
*   **Alignment**: `align-start`, `align-center`, `align-end`. Note that `Column` children align horizontally, while `Row` children align vertically.
*   **Text Alignment**: `text-center`, `text-right` to control text flow within a widget's allocated width.
*   **Spacing**: `p-1`, `p-2` for standard padding increments.
*   **Typography**: `bold`, `header-text` for weight and size.

### B. Chat Bubble Pattern
Combine background colors, corner radii, and alignments to create chat bubbles.
**StyleSheet:**
```json
"message_bubble": { "padding": 10, "cornerRadius": 16, "fontSize": 15 },
"user_bubble": { "backgroundColor": "#4F46E5", "textColor": "#FFFFFF", "alignment": "end" },
"echo_bubble": { "backgroundColor": "#1F2937", "textColor": "#E5E7EB", "alignment": "start" }
```
**Layout/Script Usage:**
```javascript
widgets.push({
    "type": "Text",
    "text": message.content,
    "classes": ["message_bubble", message.isUser ? "user_bubble" : "echo_bubble"]
});
```

### C. Card & Group Pattern
Use a `Column` or `Row` with a background and padding to group elements together visually.
**StyleSheet:**
```json
"card": { "backgroundColor": "#FFFFFF", "cornerRadius": 12, "padding": 12 }
```
**Layout:**
```json
{
  "layout": {
    "type": "Column",
    "classes": ["card"],
    "children": [ ... ]
  }
}
```

### D. Table-like (Weighted) Layouts
To create clean dashboards (like a weather forecast), use a combination of `weight`, `fillMaxWidth`, and `textAlign`.
*   Assign `weight` to columns that should occupy proportional space (e.g., a "Day" column with `weight: 2` and a "Temp" column with `weight: 1`).
*   Ensure the parent container has `fillMaxWidth: true`.
*   Use `text-right` for numerical data to ensure proper vertical alignment across rows.

-----

## 5. UI Scripting API Reference

The JS engine exposes the following global synchronization functions inside features:

### `getValue(key: string): any`
Retrieves a value from the feature's active UI state values dictionary.

### `setValue(key: string, value: any): void`
Sets a value in the feature's UI values dictionary. Renders and binds the update to UI components matching the `key` ID reactively.

### `subscribeValue(key: string, callbackName: string): number`
Subscribes to changes on a specific state `key`. The specified `callbackName` will be invoked with `[key, newValue]` whenever the state is updated. Returns a unique subscription ID token.

### `unsubscribeValue(subscriptionId: number): void`
Unsubscribes an existing value listener using the `subscriptionId` token returned from `subscribeValue`.

### `setLayout(layoutPath: string): Promise<void>`
Dynamically replaces the current layout JSON with another JSON layout file from disk (e.g., `setLayout("files/other_layout.json")`).

### `mcpCall(moduleName: string, functionName: string, params: object): Promise<any>`
Invokes a compiled JS/TS module method registered under `moduleName` with a JSON params object. Returns the result asynchronously.

-----

## 5. Styling & StyleSheets

Paddings, colors, and other cosmetic attributes cannot be defined inline inside the layout JSON. They are declared in a `StyleSheet` and mapped to widgets via the `classes` list.

### 1) Stylesheet Declaration (`applet.json`)
Declare your stylesheet files in the root manifest:
```json
  "styles": {
    "light": {
      "name": "Light Theme",
      "file": "files/styles/light_style.json"
    },
    "dark": {
      "name": "Dark Theme",
      "file": "files/styles/dark_style.json"
    }
  },
  "defaultStyle": "light"
```

### 2) Stylesheet JSON Structure (`light_style.json`)
Stylesheets define default styles per widget type and named custom classes:
```json
{
  "name": "Light",
  "scheme": "light",
  "tokens": {
    "accentColor": "#2563EB"
  },
  "defaults": {
    "Column": { "padding": 8 },
    "Text": { "textColor": "#1F2937" }
  },
  "classes": {
    "screen": { "backgroundColor": "#F8FAFC", "padding": 12 },
    "header_text": { "textColor": "#0F172A", "paddingVertical": 12, "fontSize": 28, "textAlign": "center" }
  }
}
```

### 3) Widget Styling Application
Apply stylesheet rules to layout widgets by adding class names to the `classes` array:
```json
{
  "layout": {
    "type": "Column",
    "classes": ["screen"],
    "children": [
      {
        "type": "Text",
        "text": "Hello World",
        "classes": ["header_text"]
      }
    ]
  }
}
```