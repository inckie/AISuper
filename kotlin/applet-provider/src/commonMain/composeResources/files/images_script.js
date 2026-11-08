async function initialize() {
    setValue("status_text", "Loading images...");
    try {
        var json = await httpGet("https://gist.githubusercontent.com/hiteshsahu/f58bcca95532fde77fd0d9e94a9c3148/raw/4ef7b30240c781341f1994f12453e9e7a5c2c67d/GirlImages.json");
        var data = jsonParse(json);
        var images = data.GirlImages;

        var widgets = [];
        for (var i = 0; i < images.length; i++) {
            var img = images[i];
            widgets.push({ "type": "Image", "url": img["image-url"], "description": img["description"] });
            widgets.push({ "type": "Text", "text": img["description"] });
        }

        setValue("imageList", widgets);
        setValue("status_text", "Images loaded!");
    } catch (e) {
        setValue("status_text", "Error: " + e);
    }
}
