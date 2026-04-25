function initialize() {
    setValue("formResult", []);
}

function submitForm() {
    var a = getValue("first_field") || "";
    var b = getValue("second_field") || "";

    var widgets = [
        { "type": "Text", "text": "First: " + a },
        { "type": "Text", "text": "Second: " + b }
    ];

    setValue("formResult", widgets);
}

