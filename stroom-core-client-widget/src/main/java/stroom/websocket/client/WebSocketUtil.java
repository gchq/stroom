package stroom.websocket.client;

import com.google.gwt.core.client.GWT;

public final class WebSocketUtil {

    private WebSocketUtil() {
        // Utility class.
    }

    public static String createWebSocketUrl(final String path) {
        String hostPageBaseUrl = GWT.getHostPageBaseURL();
        // Decide whether to use secure web sockets or not depending on current http/https.
        // Default to secure
        final String scheme = hostPageBaseUrl.startsWith("http:")
                ? "ws"
                : "wss";

        GWT.log("hostPageBaseUrl: " + hostPageBaseUrl);
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));

        int index = hostPageBaseUrl.indexOf("://");
        if (index != -1) {
            hostPageBaseUrl = hostPageBaseUrl.substring(index + 3);
        }

        return scheme + "://" + hostPageBaseUrl + path;
    }

}
