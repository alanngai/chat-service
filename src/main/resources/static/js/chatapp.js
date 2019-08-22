var ws = null;
var urlRoot = "ws://localhost:9091/chatapp";
var chatState = {
    eventIds: {}
};
var initiateClose = false;

function chatMessage(name, message) {
    return {
        'userId': name,
        'message': message,
        'timestamp': (new Date()).getTime()
    };
}

function setConnected(connected) {
    document.getElementById('connect').disabled = connected;
    document.getElementById('disconnect').disabled = !connected;
    document.getElementById('send').disabled = !connected;
}

function setInterrupted(interrupted) {
    document.getElementById('connect').disabled = interrupted;
    document.getElementById('disconnect').disabled = interrupted;
    document.getElementById('send').disabled = interrupted;
}

function connect() {
    var name = document.getElementById('name').value.replace(/[\s\/\\]/g, '-');
    var chatRoom = document.getElementById('chatroom').value.replace(/[\s\/\\]/g, '-');
    var webSocketPort = document.getElementById('port').value;

    if (name === '') {
        alert('please enter a name')
    } else if (chatRoom === '') {
        alert('chatroom cannot be empty or contain spaces or slashes');
    } else {
        var endpoint = "ws://localhost:" + webSocketPort + "/chatapp/chatrooms/" + chatRoom + "?userid=" + name;
        if (chatState.lastEventId) {
            endpoint += "&rejoin&lasteventid=" + chatState.lastEventId;
        }

        console.log("attempting connection to " + endpoint);
        ws = new WebSocket(endpoint);
        ws.onopen = function(event) {
            console.log("connection established");
            console.log(event);
            setInterrupted(false);
            setConnected(true);
            log('Info: connection established');
        }

        ws.onmessage = function(event) {
            var chatEnvelope = JSON.parse(event.data);

            // dedupe
            if (!(chatEnvelope.lastEventId in chatState.eventIds)) {
                chatState.lastEventId = chatEnvelope.lastEventId;
                chatState.eventIds[chatEnvelope.lastEventId] = true;
                log(chatEnvelope);
            }
        }

        ws.onerror = function(event) {
            console.log('encountered error');
            console.log(event);
        }

        ws.onclose = function(event) {
            console.log("connection closed: " + event.code + "/" + event.wasClean);
            console.log(event);

            // if close was not initiated by user, disable controls and try to re-establish connection
            if (!initiateClose) {
                setInterrupted(true);
                window.setTimeout(connect, 5000);
            }
        };
    }
}

function disconnect() {
    initiateClose = true;
    console.log("closing websocket connection...");
    if (ws != null) {
        ws.close();
        ws = null;
    }
    setConnected(false);
}

function sendMessage() {
    if (ws != null) {
        var name = document.getElementById('name').value;
        var message = document.getElementById('message').value;
        ws.send(JSON.stringify(chatMessage(name, message)));
    } else {
        alert('connection not established, please connect.');
    }
}

function log(message) {
    var console = document.getElementById('logging');
    var p = document.createElement('p');
    p.appendChild(document.createTextNode(JSON.stringify(message)));
    console.appendChild(p);
}
