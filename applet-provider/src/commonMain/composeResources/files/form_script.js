function initialize() {
    setValue("formResult", []);
}

function submitForm() {
    var a = getValue("first_field") || "";
    var b = getValue("second_field") || "";
    var agreed = getValue("agree_toggle") || "false";

    var widgets = [
        { "type": "Text", "text": "First: " + a },
        { "type": "Text", "text": "Second: " + b },
        { "type": "Text", "text": "Agreed: " + agreed }
    ];

    setValue("formResult", widgets);
}

