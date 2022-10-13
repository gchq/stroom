package stroom.websocket.client;

import stroom.util.shared.IsWebSocket;
import stroom.util.shared.WebSocketMessage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.json.client.JSONValue;

public class WebSocket {

    private static final WebSocketMessageCodec WEB_SOCKET_MESSAGE_CODEC =
            GWT.create(WebSocketMessageCodec.class);

    private static int counter = 1;

    public static boolean isSupported() {
        return nativeIsWebsocket();
    }

    private final String varName;

    public WebSocket(final String url,
                     final WebSocketListener listener) {
        this.varName = "gwt-WebSocket-" + counter++;
        nativeOpen(this, listener, varName, url);
    }

    public ReadyState getReadyState() {
        final int readyState = nativeReadyState(varName);
        switch (readyState) {
            case 0:
                return ReadyState.CONNECTING;
            case 1:
                return ReadyState.OPEN;
            case 2:
                return ReadyState.CLOSING;
            case 3:
                return ReadyState.CLOSED;
        }
        throw new RuntimeException("Unknown ready state: " + readyState);
    }

    public void close(final String reason) {
        try {
            sendInfoMessage("Explicitly closing web socket. Reason: " + reason);
        } catch (Exception e) {
            // Only for information purposes so just swallow any error
        }
        nativeClose(varName);
    }

    public void send(WebSocketMessage msg) {
        // Convert the msg object to json
        final JSONValue jsonValue = WEB_SOCKET_MESSAGE_CODEC.encode(msg);
        nativeSend(varName, jsonValue.toString());
    }

    private void sendInfoMessage(String info) {
        send(WebSocketMessage.of(IsWebSocket.WEB_SOCKET_MSG_KEY_INFO, info));
    }

    private static native boolean nativeIsWebsocket() /*-{
        return ("WebSocket" in window);
    }-*/;

    @SuppressWarnings("checkstyle:LineLength")
    private native void nativeOpen(WebSocket ws, WebSocketListener listener, String s, String url) /*-{
        $wnd[s] = new WebSocket(url);
        $wnd[s].onopen = function(e) {
            console.log('WebSocket onOpen() called: ', e);
            listener.@stroom.websocket.client.WebSocketListener::onOpen(*)(e);
        };
        $wnd[s].onclose = function(e) {
            console.log('WebSocket onClose() called: ', e);
            listener.@stroom.websocket.client.WebSocketListener::onClose(*)(e);
        };
        $wnd[s].onmessage = function(e) {
           console.log('WebSocket onMessage() called: ', e);
           listener.@stroom.websocket.client.WebSocketListener::onMessage(*)(e);
        }
        $wnd[s].onerror = function(e) {
           console.log('WebSocket onError() called: ', e);
           listener.@stroom.websocket.client.WebSocketListener::onError(*)(e);
        };
    }-*/;

    public native void nativeClose(String s) /*-{
        $wnd[s].close();
    }-*/;

    public native void nativeSend(String s, String msg) /*-{
        $wnd[s].send(msg);
    }-*/;

    private native int nativeReadyState(String s) /*-{
    return $wnd[s].readyState;
    }-*/;

    public enum ReadyState {
        CONNECTING,
        OPEN,
        CLOSING,
        CLOSED
    }
}
