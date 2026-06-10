function initialize() {
    // Generate dynamic items for the row
    var rowItems = [];
    for (var i = 1; i <= 10; i++) {
        rowItems.push({
            "type": "Button",
            "text": "Dyn " + i,
            "action": "dummy"
        });
    }
    setValue("dynamic_row_items", rowItems);

    // Generate dynamic items for the column
    var colItems = [];
    for (var j = 1; j <= 5; j++) {
        colItems.push({
            "type": "Text",
            "text": "Dynamic Column Entry #" + j,
            "fillMaxWidth": true
        });
    }
    setValue("dynamic_col_items", colItems);
}

function dummy() {
    // Do nothing
}
