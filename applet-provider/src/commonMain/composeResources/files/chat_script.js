var messages = [];

function refreshMessages() {
    var widgets = [];
    for (var i = 0; i < messages.length; i = i + 1) {
        var msg = messages[i];
        var text = "";
        var alignVal = "start";
        if (msg.sender === "user") {
            text = "You: " + msg.text;
            alignVal = "end";
        } else if (msg.sender === "echo") {
            text = "Echo: " + msg.text;
            alignVal = "start";
        } else {
            text = "System: " + msg.text;
            alignVal = "center";
        }

        var item = {
            "type": "Text",
            "text": text,
            "align": alignVal,
            "fillMaxWidth": true
        };
        widgets.push(item);
    }
    setValue("messageList", widgets);
}

function initialize() {
    messages.push({ sender: "system", text: "Welcome to Echo Chat!" });
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

    messages.push({ sender: "user", text: input });
    messages.push({ sender: "echo", text: input });

    refreshMessages();
    setValue("input_field", "");
}
