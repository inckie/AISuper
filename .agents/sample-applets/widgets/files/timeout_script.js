function initialize() {
    setValue("statusLabel", "Press the button to start a 5-second timer");
}

function startTimer() {
    setValue("statusLabel", "Timer started...");

    setTimeout(function() {
        setValue("statusLabel", "Timeout complete!");
    }, 5000);
}
