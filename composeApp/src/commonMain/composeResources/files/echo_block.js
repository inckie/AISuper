function initialize() {
    setValue("result_text", "Loading images...");
    try {
        var json = httpGet("https://gist.githubusercontent.com/hiteshsahu/f58bcca95532fde77fd0d9e94a9c3148/raw/4ef7b30240c781341f1994f12453e9e7a5c2c67d/GirlImages.json");
        var data = JSON.parse(json);
        var images = data.GirlImages;

        var widgets = [];
        for (var i = 0; i < images.length; i++) {
            var img = images[i];
            widgets.push({
                "type": "Image",
                "url": img["image-url"],
                "description": img["description"]
            });
            widgets.push({
                "type": "Text",
                "text": img["description"]
            });
        }

        setValue("imageList", JSON.stringify(widgets));
        setValue("result_text", "Images loaded!");
    } catch (e) {
        setValue("result_text", "Error: " + e);
    }
}

function process() {
    var input = getValue("input_field");
    var result = "Echo: " + input;
    setValue("result_text", result);
}
