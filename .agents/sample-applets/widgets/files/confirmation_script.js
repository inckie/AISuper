function initialize() {
    setValue("lastResultLabel", "Ready. Choose an action to test native modal dialogs.");
}

async function demoStandardConfirm() {
    setValue("lastResultLabel", "Waiting for confirmation...");

    var ok = await confirm({
        title: "Save Changes?",
        message: "Do you want to save your current work and continue?",
        confirmText: "Save",
        cancelText: "Cancel",
        isDestructive: false
    });

    if (ok) {
        setValue("lastResultLabel", "Result: Confirmed (Save). Changes saved successfully!");
    } else {
        setValue("lastResultLabel", "Result: Cancelled. No changes made.");
    }
}

async function demoDestructiveConfirm() {
    setValue("lastResultLabel", "Waiting for confirmation...");

    var ok = await confirm({
        title: "Delete Account Data?",
        message: "This action cannot be undone. All your stored files will be permanently erased.",
        confirmText: "Permanently Delete",
        cancelText: "Keep Data",
        isDestructive: true
    });

    if (ok) {
        setValue("lastResultLabel", "Result: Confirmed (Destructive). Account data deleted.");
    } else {
        setValue("lastResultLabel", "Result: Cancelled. Deletion aborted safely.");
    }
}

async function demoSimpleStringConfirm() {
    setValue("lastResultLabel", "Waiting for confirmation...");

    // Can also be called with plain string message and optional title
    var ok = await confirm("Proceed with default configuration?", "Quick Confirmation");

    if (ok) {
        setValue("lastResultLabel", "Result: Confirmed simple string prompt.");
    } else {
        setValue("lastResultLabel", "Result: Dismissed / Cancelled simple string prompt.");
    }
}
