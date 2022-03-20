package stroom.websocket.client;

public interface WebSocketListener {

    void onOpen(OpenEvent event);

    void onClose(CloseEvent event);

    void onMessage(MessageEvent event);

    void onError(ErrorEvent event);
}
