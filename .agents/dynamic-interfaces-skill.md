# Dynamic Interfaces Skill (AISuper)

This skill documents how to build interactive, responsive, and growable user interfaces in AISuper applets, detailing all supported layout structures, widget properties, reactive data bindings, and scripting patterns.

---

## 1. UI Layout Widgets Reference

AISuper layouts are defined in JSON (e.g., [main_layout.json](file:///d:/Work/Mobile/Android/AISuper/.temp/superapplet/applet/files/main_layout.json)). Every widget inherits from `WidgetBase` and supports standard attributes:

### Common Widget Properties (`WidgetBase`)
* `type`: (String, Required) One of the widget types below.
* `id`: (String, Optional) State binding key. If a widget has an `id`, its value is reactively synchronized with the feature state.
* `fillMaxWidth`: (Boolean, Optional) Forces the widget to stretch to the parent's width.
* `fillMaxSize`: (Boolean, Optional) Forces the widget to fill the entire parent space.
* `weight`: (Float, Optional) Proportion of layout space the widget should occupy compared to siblings in a `Row` or `Column`.
* `classes`: (Array of Strings, Optional) List of class names defined in the active stylesheet (e.g., `["screen", "header_text"]`) to apply margins, paddings, and colors.
* **Note:** The `modifier` property is **NOT** supported by the Kotlin layout renderer. All spacing and styling must be defined in a `StyleSheet` and applied using `classes` or defaults.

### Supported Widgets

| Widget | Properties | Description |
|---|---|---|
| **`Column`** | `children` (Array), `isScrollable` (Boolean), `dynamicChildrenId` (String) | Arranges children vertically. Set `isScrollable: true` to enable vertical scrolling. |
| **`Row`** | `children` (Array), `isScrollable` (Boolean), `dynamicChildrenId` (String) | Arranges children horizontally. Set `isScrollable: true` to enable horizontal scrolling. Supports dynamic children list population via `dynamicChildrenId`. |
| **`Text`** | `text` (String), `align` (String - "center"/"left"/"right") | Renders a text label. |
| **`TextField`** | `hint` (String), `singleLine` (Boolean), `password` (Boolean), `imeAction` (String), `onImeAction` (String), `nextFocusId` (String) | Text input field. Updates the state key corresponding to its `id` automatically as the user types. |
| **`Button`** | `text` (String), `action` (String), `actionArgs` (Array) | Action trigger button. Triggers the specified JS function name on click. |
| **`Dropdown`** | `hint` (String), `options` (Array of value/label), `optionsValueId` (String), `onChangeAction` (String) | Dropdown selection field. Triggers the specified JS callback when selection changes. |
| **`Switch`** | `text` (String), `checked` (Boolean) | Binary toggle switch. Automatically updates its bound `id` state, but **does not** trigger JS action callbacks. |
| **`Spinner`** | `visibilityId` (String) | Circular loading progress indicator. Visually toggled by binding to a boolean state key. |
| **`Progress`** | `progress` (Float), `progressId` (String), `indeterminate` (Boolean) | Linear progress bar. Can bind to a float state key (`0.0` to `1.0`). |

---

## 2. Interface Design Patterns

To build responsive, responsive UI dashboards, use the following two patterns:

### Pattern A: Static Layout with Reactive Value Bindings (Highly Recommended)
For fixed dashboards (like a weather dashboard or profile forms), define a static layout template in JSON and assign unique `id`s to the widgets. 

The Android layout renderer automatically substitutes the text values in Compose:
```kotlin
val displayText = if (widgetId != null && values.containsKey(widgetId)) {
    values[widgetId]?.stringOrNull() ?: widget.text
} else {
    widget.text
}
```

#### Why it's recommended:
1. **Zero Serialization Overhead**: Avoids passing massive JSON widget payloads through state.
2. **Reliable Updates**: Instantly reactive when calling `setValue(id, value)`.
3. **Muted Buffer Truncation**: High-performance rendering without running into string size limitations on state bridge logs.

---

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

---

## 3. Dropdowns vs. Switches (Action Notification)

When designing a toggle (e.g. Unit Switcher, Dark Mode, Toggle Preferences):
* ❌ **Avoid Switch widgets** if you need the layout to refresh immediately when toggled. Switches do not support `onChangeAction` or click actions. They update their state key silently, meaning the script won't know when to redraw until a separate submit action is triggered.
* **Use Dropdown widgets** with `onChangeAction` instead. Selecting an option in a Dropdown immediately triggers the JS action, allowing you to re-evaluate and redraw the UI:

#### Layout Dropdown definition:
```json
{
  "type": "Dropdown",
  "id": "unit_selection",
  "hint": "Units",
  "onChangeAction": "onUnitsChanged",
  "options": [
    { "value": "metric", "label": "Metric" },
    { "value": "imperial", "label": "Imperial" }
  ]
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

---

## 4. UI Scripting API Reference

The JS engine exposes the following global synchronization functions inside features:

### `getValue(key: string): any`
Retrieves a value from the feature's active UI state values dictionary.

### `setValue(key: string, value: any): void`
Sets a value in the feature's UI values dictionary. Renders and binds the update to UI components matching the `key` ID reactively.

### `setLayout(layoutPath: string): Promise<void>`
Dynamically replaces the current layout JSON with another JSON layout file from disk (e.g., `setLayout("files/other_layout.json")`).

### `mcpCall(moduleName: string, functionName: string, params: object): Promise<any>`
Invokes a compiled JS/TS module method registered under `moduleName` with a JSON params object. Returns the result asynchronously.

---

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
```
