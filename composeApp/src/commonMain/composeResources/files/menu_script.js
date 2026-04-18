function initialize() {
    var features = getFeatures();

    var widgets = [];
    var mediaHeaderAdded = false;
    for (var i = 0; i < features.length; i++) {
        var f = features[i];
        if (f.id === "menu") continue;

        if (f.id === "radio" && !mediaHeaderAdded) {
            widgets.push({
                "type": "Text",
                "text": "Media"
            });
            mediaHeaderAdded = true;
        }

        var buttonText = f.name;
        if (f.id === "radio") {
            buttonText = "Radio Search";
        }

        widgets.push({
            "type": "Button",
            "text": buttonText,
            "action": "launch:" + f.id,
            "fillMaxWidth": true
        });
    }

    setValue("menuButtons", widgets);
}
