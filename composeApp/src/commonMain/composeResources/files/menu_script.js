function initialize() {
    var features = getFeatures();

    var widgets = [];
    for (var i = 0; i < features.length; i++) {
        var f = features[i];
        if (f.id === "menu") continue;

        widgets.push({
            "type": "Button",
            "text": f.name,
            "action": "launch:" + f.id
        });
    }

    setValue("menuButtons", widgets);
}
