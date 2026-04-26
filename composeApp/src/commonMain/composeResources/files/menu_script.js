function initialize() {
    var themes = getAvailableThemes();
    var currentTheme = getCurrentTheme();

    var themeOptions = [];
    for (var t = 0; t < themes.length; t++) {
        var theme = themes[t];
        themeOptions.push({
            "value": theme.id,
            "label": theme.name
        });
    }

    setValue("themeOptions", themeOptions);
    setValue("themePicker", currentTheme);

    var features = getFeatures();

    var widgets = [];
    var mediaHeaderAdded = false;
    for (var i = 0; i < features.length; i++) {
        var f = features[i];
        if (f.id === "menu") continue;

        if (f.id === "radio" && !mediaHeaderAdded) {
            widgets.push({
                "type": "Text",
                "text": "Media",
                "classes": ["section_title"]
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
            "fillMaxWidth": true,
            "classes": ["menu_button"]
        });
    }

    setValue("menuButtons", widgets);
}

function changeTheme(themeId) {
    if (themeId == undefined || themeId == null || themeId == "") {
        return;
    }

    var applied = setCurrentTheme(themeId);
    if (applied) {
        setValue("themePicker", themeId);
    }
}
