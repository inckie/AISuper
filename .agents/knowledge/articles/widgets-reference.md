---
categories:
- applet-developer
created: '2026-07-02T01:11:48.623005+00:00'
id: widgets-reference
modified: '2026-09-02T00:22:37.168482+00:00'
tags:
- widgets
- ui
- layout
- reference
title: Widgets Reference
type: leaf
---

# Widgets Reference (AISuper)

This article lists all available widgets for building layouts in AISuper applets, including their properties and JSON sample snippets.

For information on how to assemble these widgets into complete layouts, handle dynamic data, and apply styles, see the [[dynamic-interfaces-skill|Dynamic Interfaces Skill]].

## Common Widget Properties

Every widget inherits from `WidgetBase` and supports standard attributes:

* `type`: (String, Required) The type name of the widget (e.g., `"Text"`, `"Column"`).
* `id`: (String, Optional) State binding key. Reactively synchronizes the widget's value with the feature state.
* `visibilityId`: (String, Optional) State binding key for visibility. If the bound value is `false`, the widget is not rendered.
* `fillMaxWidth`: (Boolean, Optional) Forces the widget to stretch to the parent's width (default `false`).
* `fillMaxSize`: (Boolean, Optional) Forces the widget to fill the entire parent space (default `false`).
* `weight`: (Float, Optional) Proportion of layout space the widget should occupy compared to siblings in a `Row` or `Column`.
* `classes`: (Array of Strings, Optional) List of class names defined in the active stylesheet.

---

## Layout Widgets

### Column
Arranges children vertically.

**Properties:**
* `children` (Array of Widgets): Nested child widgets.
* `dynamicChildrenId` (String): Used to dynamically populate the list of children from state.
* `isScrollable` (Boolean): Set to `true` to enable vertical scrolling.

**Example:**
```json
{
  "type": "Column",
  "fillMaxSize": true,
  "isScrollable": true,
  "children": [
    { "type": "Text", "text": "Item 1" },
    { "type": "Text", "text": "Item 2" }
  ]
}
```

### Row
Arranges children horizontally.

**Properties:**
* `children` (Array of Widgets): Nested child widgets.
* `dynamicChildrenId` (String): Used to dynamically populate the list of children from state.
* `isScrollable` (Boolean): Set to `true` to enable horizontal scrolling.

**Example:**
```json
{
  "type": "Row",
  "fillMaxWidth": true,
  "children": [
    { "type": "Text", "text": "Left", "weight": 1 },
    { "type": "Text", "text": "Right" }
  ]
}
```

---

## Content & Input Widgets

### Text
Renders a text label.

**Properties:**
* `text` (String): The text to display.
* `align` (String): Text alignment (`"center"`, `"left"`, `"right"`). Alternatively controlled via stylesheet classes.

**Example:**
```json
{
  "type": "Text",
  "text": "Hello, World!",
  "classes": ["header_text"]
}
```

### TextField
Text input field. Updates the state key corresponding to its `id` automatically as the user types.

**Properties:**
* `hint` (String): Placeholder text.
* `singleLine` (Boolean): Defaults to `true`. Set to `false` for multi-line input.
* `password` (Boolean): Set to `true` to mask input.
* `imeAction` (String): Action to request on the keyboard (e.g., `"Search"`, `"Next"`, `"Done"`).
* `onImeAction` (String): Javascript action to call when the IME action is triggered.
* `nextFocusId` (String): The ID of the next widget to focus if `imeAction` is `"Next"`.

**Example:**
```json
{
  "type": "TextField",
  "id": "username_input",
  "hint": "Enter your username",
  "singleLine": true,
  "imeAction": "Next",
  "nextFocusId": "password_input"
}
```

### Button
Action trigger button.

**Properties:**
* `text` (String): Button label.
* `icon` (String): URL or Data URI for an icon.
* `action` (String): Javascript callback action name to trigger on click.
* `actionArgs` (Array): Array of arguments to pass to the JS action callback.

**Example:**
```json
{
  "type": "Button",
  "text": "Submit",
  "action": "onSubmit",
  "actionArgs": ["submit_clicked"]
}
```

### Image
Renders an image from a URL or base64 data.

**Properties:**
* `url` (String): URL of the image to display.
* `data` (String): Base64 encoded image data.
* `description` (String): Accessibility description.

**Example:**
```json
{
  "type": "Image",
  "url": "https://example.com/logo.png",
  "description": "App Logo",
  "fillMaxWidth": true
}
```

### AudioPlayer
Embedded audio player control.

**Properties:**
* `player` (String): The ID or URL of the audio stream/player to bind to.
* `title` (String): Title of the audio track (defaults to "Audio Player").

**Example:**
```json
{
  "type": "AudioPlayer",
  "player": "stream_url",
  "title": "Live Radio"
}
```

### Dropdown
Dropdown selection field.

**Properties:**
* `hint` (String): Placeholder text (default "Select").
* `options` (Array): Array of objects with `value` (String) and `label` (String).
* `optionsValueId` (String): ID to bind the selected value.
* `onChangeAction` (String): JS callback triggered when selection changes.

**Example:**
```json
{
  "type": "Dropdown",
  "id": "theme_selector",
  "hint": "Select Theme",
  "options": [
    { "value": "light", "label": "Light Theme" },
    { "value": "dark", "label": "Dark Theme" }
  ],
  "onChangeAction": "onThemeChanged"
}
```

### Switch
Binary toggle switch.

**Properties:**
* `text` (String): Label for the switch.
* `checked` (Boolean): Initial state.
* `onChangeAction` (String): JS callback triggered on toggle.
* `actionArgs` (Array): Arguments passed to the callback (along with the new boolean value).

**Example:**
```json
{
  "type": "Switch",
  "id": "notifications_enabled",
  "text": "Enable Notifications",
  "checked": true,
  "onChangeAction": "onToggleNotifications"
}
```

### Spinner
Circular loading progress indicator.

**Properties:**
(No specific properties other than WidgetBase). Often bound using `visibilityId`.

**Example:**
```json
{
  "type": "Spinner",
  "visibilityId": "is_loading"
}
```

### Progress
Linear progress bar.

**Properties:**
* `progress` (Float): Fixed progress value from 0.0 to 1.0.
* `progressId` (String): State key to bind the progress value.
* `indeterminate` (Boolean): If `true`, renders an indeterminate loading bar.

**Example:**
```json
{
  "type": "Progress",
  "progressId": "download_progress",
  "fillMaxWidth": true
}
```

### Slider
Horizontal slider control with support for range limits and discrete steps. Automatically synchronizes with state via `id` and emits events on adjustment.

**Properties:**
* `min` (Float, Optional): Minimum value for the slider (defaults to `0.0`).
* `max` (Float, Optional): Maximum value for the slider (defaults to `100.0`).
* `step` (Float, Optional): Step increment value. If specified and greater than 0, the slider snaps to discrete steps between `min` and `max`.
* `value` (Float, Optional): Initial fallback value when not bound in state.
* `onChangeAction` (String, Optional): JS action callback triggered when the user adjusts the slider.
* `actionArgs` (Array, Optional): Arguments passed to the callback (along with the new numeric value).

**Example:**
```json
{
  "type": "Slider",
  "id": "volume_level",
  "min": 0,
  "max": 100,
  "step": 5,
  "value": 50,
  "fillMaxWidth": true,
  "onChangeAction": "onVolumeChanged",
  "actionArgs": ["volume_level"]
}
```