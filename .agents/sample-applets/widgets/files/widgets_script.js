var currentProgress = 0.5;

function initialize() {
    // Initial state
    setValue("spinner_visible", false);
    setValue("demo_progress", currentProgress);
    setValue("demo_switch", false);
    
    // Demonstrate subscription
    subscribeValue("demo_textfield", "onTextFieldChange");
}

function onDropdownChange() {
    var selected = getValue("demo_dropdown");
    setValue("dropdown_status", "Selected: " + selected);
}

// Switch callback gets actionArgs + [newValue]
function onSwitchToggle(switchId, newValue) {
    // We could read from getValue("demo_switch") but newValue is passed natively!
    setValue("switch_status", "Switch State: " + newValue + " (id=" + switchId + ")");
}

function onTextFieldChange(key, newValue) {
    setValue("textfield_status", "Typing: " + newValue);
}

function toggleSpinner(newValue) {
    setValue("spinner_visible", newValue);
}

function increaseProgress() {
    currentProgress += 0.1;
    if (currentProgress > 1.0) {
        currentProgress = 0.0;
    }
    setValue("demo_progress", currentProgress);
}
