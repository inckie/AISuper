var messages = [];

function refreshMessages() {
    var widgets = [];
    for (var i = 0; i < messages.length; i = i + 1) {
        var msg = messages[i];
        var item = {
            "type": "Text",
            "text": msg
        };
        widgets.push(item);
    }
    var json = JSON.stringify(widgets);
    setValue("messageList", json);
}

function initialize() {
    messages.push("System: Welcome to Echo Chat!");
    refreshMessages();
}

function process() {
    var input = getValue("input_field");
    if (input == undefined) {
        return;
    }
    if (input == "") {
        return;
    }

    var uMsg = "You: " + input;
    messages.push(uMsg);

    var eMsg = "Echo: " + input;
    messages.push(eMsg);

    refreshMessages();
    setValue("input_field", "");
}
