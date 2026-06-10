async function initialize() {
    // When the feature is opened, we must set the initial layout
    await setLayout("view1");
}

async function switchToView2() {
    // Dynamically switch the entire layout tree
    await setLayout("view2");
}

async function switchToView1() {
    // Dynamically switch back
    await setLayout("view1");
}
