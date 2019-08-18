var ws = null;
var urlRoot = "ws://localhost:9091/chatapp";

function setConnected(connected) {
    document.getElementById('connect').disabled = connected;
    document.getElementById('disconnect').disabled = !connected;
    document.getElementById('chatsession').disabled = !connected;
}

function connect() {
    var chatRoom = document.getElementById('chatroom').value.replace(/[\s\/\\]/g, '-');
    if (chatRoom === "") {
        alert('chatroom cannot be empty or contain spaces or slashes');
    } else {
        ws = new WebSocket(urlRoot + "/chatrooms/" + chatRoom + "/chatsessions");
        ws.onopen = function() {
            setConnected(true);
            log('Info: connection established');
        }

        ws.onmessage = function(event) {
            log(event.data);
        }

        ws.onclose = function(event) {
            setConnected(false);
            log('Info: closing connection');
        };
    }
}

function disconnect() {
    if (ws != null) {
        ws.close();
        ws = null;
    }
    setConnected(false);
}

function echo() {
    if (ws != null) {
        var message = document.getElementById('message').value;
        log('Sent to server :: ' + message);
        ws.send(message);
    } else {
        alert('connection not established, please connect.');
    }
}

function log(message) {
    var console = document.getElementById('logging');
    var p = document.createElement('p');
    p.appendChild(document.createTextNode(message));
    console.appendChild(p);
}
