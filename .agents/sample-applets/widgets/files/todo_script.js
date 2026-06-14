var tasks = [];
var filter = "all";

function initialize() {
    loadData();
}

async function loadData() {
    try {
        var savedTasks = await persistentStorageGetObject("feature", "todo_tasks");
        if (savedTasks && typeof savedTasks === "object" && savedTasks.length !== undefined) {
            tasks = savedTasks;
        } else {
            tasks = [];
        }
    } catch(e) {
        consoleError("Failed to load tasks: " + e);
        tasks = [];
    }
    filter = "all";
    render();
}

async function saveTasks() {
    try {
        await persistentStoragePutObject("feature", "todo_tasks", tasks);
    } catch(e) {
        consoleError("Failed to save tasks: " + e);
    }
}

async function addTask() {
    var inputStr = getValue("todo_input");
    if (!inputStr || inputStr.trim() === "") {
        return;
    }

    var taskId = "task_" + new Date().getTime() + "_" + Math.floor(Math.random() * 1000);
    tasks.push({
        id: taskId,
        text: inputStr.trim(),
        completed: false
    });

    setValue("todo_input", "");
    await saveTasks();
    render();
}

async function toggleTask(taskId) {
    if (!taskId) {
        taskId = getValue("selected_task_id");
    }
    if (!taskId) {
        consoleError("toggleTask: No task ID provided");
        return;
    }
    for (var i = 0; i < tasks.length; i++) {
        if (tasks[i].id === taskId) {
            tasks[i].completed = !tasks[i].completed;
            break;
        }
    }
    await saveTasks();
    render();
}

async function deleteTask(taskId) {
    if (!taskId) {
        taskId = getValue("selected_task_id");
    }
    if (!taskId) {
        consoleError("deleteTask: No task ID provided");
        return;
    }
    var newTasks = [];
    for (var i = 0; i < tasks.length; i++) {
        if (tasks[i].id !== taskId) {
            newTasks.push(tasks[i]);
        }
    }
    tasks = newTasks;
    await saveTasks();
    render();
}

function setFilter(newFilter) {
    if (!newFilter) {
        newFilter = getValue("selected_filter");
    }
    if (!newFilter) {
        consoleError("setFilter: No filter provided");
        return;
    }
    filter = newFilter;
    render();
}

async function clearCompleted() {
    var newTasks = [];
    for (var i = 0; i < tasks.length; i++) {
        if (!tasks[i].completed) {
            newTasks.push(tasks[i]);
        }
    }
    tasks = newTasks;
    await saveTasks();
    render();
}

function render() {
    var activeCount = 0;
    for (var i = 0; i < tasks.length; i++) {
        if (!tasks[i].completed) {
            activeCount++;
        }
    }

    setValue("todo_stats", activeCount + " tasks remaining (Filter: " + filter.toUpperCase() + ")");

    var filteredTasks = [];
    for (var i = 0; i < tasks.length; i++) {
        var t = tasks[i];
        if (filter === "all") {
            filteredTasks.push(t);
        } else if (filter === "active" && !t.completed) {
            filteredTasks.push(t);
        } else if (filter === "completed" && t.completed) {
            filteredTasks.push(t);
        }
    }

    var listWidgets = [];
    for (var i = 0; i < filteredTasks.length; i++) {
        var task = filteredTasks[i];
        listWidgets.push({
            "type": "Row",
            "fillMaxWidth": true,
            "children": [
                {
                    "type": "Button",
                    "text": task.completed ? "✓" : " ",
                    "action": "toggleTask",
                    "actionArgs": [task.id]
                },
                {
                    "type": "Text",
                    "text": task.completed ? "[Done] " + task.text : task.text,
                    "weight": 1
                },
                {
                    "type": "Button",
                    "text": "X",
                    "action": "deleteTask",
                    "actionArgs": [task.id],
                    "classes": ["back_button"]
                }
            ]
        });
    }

    setValue("todo_list", listWidgets);
}
