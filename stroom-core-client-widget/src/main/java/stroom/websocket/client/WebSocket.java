package stroom.websocket.client;

public class WebSocket {

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

    public void close() {
        nativeClose(varName);
    }

    public void send(String msg) {
        nativeSend(varName, msg);
    }

    private static native boolean nativeIsWebsocket() /*-{
        return ("WebSocket" in window);
    }-*/;

    @SuppressWarnings("checkstyle:LineLength")
    private native void nativeOpen(WebSocket ws, WebSocketListener listener, String s, String url) /*-{
        $wnd[s] = new WebSocket(url);
        $wnd[s].onopen = function(e) {
            console.log('WebSocket open: ', e);
            listener.@stroom.websocket.client.WebSocketListener::onOpen(*)(e);
        };
        $wnd[s].onclose = function(e) {
            console.log('WebSocket close: ', e);
            listener.@stroom.websocket.client.WebSocketListener::onClose(*)(e);
        };
        $wnd[s].onmessage = function(e) {
           console.log('WebSocket message: ', e);
           listener.@stroom.websocket.client.WebSocketListener::onMessage(*)(e);
        }
        $wnd[s].onerror = function(e) {
           console.log('WebSocket error: ', e);
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
