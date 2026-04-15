var playerName = "radioMain";
var plazaUrl = "https://radio.plaza.one/mp3";
var provodachUrl = "https://station.waveradio.org/provodach";

function initialize() {
    audioSubscribe(playerName, "onAudioEvent");
    setValue("current_stream", "Current stream: none");
    setValue("player_event", "Subscribed to player events");
}

function loadPlaza() {
    audioLoad(playerName, plazaUrl);
    setValue("current_stream", "Current stream: plaza");
}

function loadProvodach() {
    audioLoad(playerName, provodachUrl);
    setValue("current_stream", "Current stream: provodach");
}

function playFromJs() {
    audioPlay(playerName);
}

function pauseFromJs() {
    audioPause(playerName);
}

function stopFromJs() {
    audioStop(playerName);
}

function seekBack10s() {
    var state = audioGetState(playerName);
    var current = state.positionMs;
    var target = current - 10000;
    if (target < 0) {
        target = 0;
    }
    audioSeek(playerName, target);
}

function seekForward10s() {
    var state = audioGetState(playerName);
    var current = state.positionMs;
    var target = current + 10000;
    audioSeek(playerName, target);
}

function onAudioEvent(event) {
    var state = event.state;
    var pos = event.positionMs;
    setValue("player_event", "Event: " + state + ", position=" + pos);
}

