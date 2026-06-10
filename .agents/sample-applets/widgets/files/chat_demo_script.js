var messages = [];

function initialize() {
    // Start with a welcome message
    messages.push({
        "type": "Text",
        "text": "Welcome to the Chat Demo! Scrollable area will hold messages.",
        "classes": ["section_title"]
    });
    
    // Add some dummy messages to demonstrate scrolling
    for(var i = 1; i <= 20; i++) {
        messages.push({
            "type": "Row",
            "fillMaxWidth": true,
            "children": [
                {
                    "type": "Image",
                    "url": "https://ui-avatars.com/api/?name=User+" + i + "&background=random",
                    "id": "avatar_" + i
                },
                {
                    "type": "Text",
                    "text": "This is message number " + i + " from history.",
                    "weight": 1
                }
            ]
        });
    }

    renderMessages();
}

function send_message() {
    var inputStr = getValue("chat_input");
    if (!inputStr || inputStr.trim() === "") {
        return;
    }

    // Add new message to the list
    messages.push({
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
            {
                "type": "Image",
                "url": "https://ui-avatars.com/api/?name=Me&background=0D8ABC&color=fff",
                "id": "avatar_me_" + messages.length
            },
            {
                "type": "Text",
                "text": inputStr,
                "weight": 1
            }
        ]
    });

    renderMessages();
    
    // Clear input
    setValue("chat_input", "");
}

function renderMessages() {
    // Clone array to trigger a state update in the reactive tree
    // if using reference equality, though Duktape serialization handles it anyway.
    setValue("chat_messages", messages);
}
