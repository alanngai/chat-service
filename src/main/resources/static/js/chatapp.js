var ws = null;
var urlRoot = "ws://localhost:9091/chatapp";
var chatState = {};

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
    document.getElementById('chatsession').disabled = !connected;
}

function connect() {
    var name = document.getElementById('name').value.replace(/[\s\/\\]/g, '-');
    var chatRoom = document.getElementById('chatroom').value.replace(/[\s\/\\]/g, '-');

    if (name === '') {
        alert('please enter a name')
    } else if (chatRoom === '') {
        alert('chatroom cannot be empty or contain spaces or slashes');
    } else {
        // ws = new WebSocket(urlRoot + "/chatrooms/" + chatRoom + "/chatsessions/" + name + "?foo=bar&baz=blah");
        var endpoint = urlRoot + "/chatrooms/" + chatRoom + "?userid=" + name;
        if (chatState.lastEventId) {
            endpoint += "&rejoin&lasteventid=" + chatState.lastEventId;
        }
        ws = new WebSocket(endpoint);
        ws.onopen = function() {
            setConnected(true);
            log('Info: connection established');
        }

        ws.onmessage = function(event) {
            var chatEnvelope = JSON.parse(event.data);
            chatState.lastEventId = chatEnvelope.lastEventId;
            log(chatEnvelope);
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
