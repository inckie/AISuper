var messages = [];

function refreshMessages() {
    var widgets = [];
    for (var i = 0; i < messages.length; i = i + 1) {
        var msg = messages[i];
        var text = "";
        var classes = ["message_bubble"];
        if (msg.sender === "user") {
            text = msg.text;
            classes.push("user_bubble");
        } else if (msg.sender === "echo") {
            text = msg.text;
            classes.push("echo_bubble");
        } else {
            text = msg.text;
            classes = ["system_message"];
        }

        var item = {
            "type": "Text",
            "text": text,
            "classes": classes
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
