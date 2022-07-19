package stroom.websocket.client;

import stroom.util.shared.ResourcePaths;

import com.google.gwt.core.client.GWT;

public final class WebSocketUtil {

    private WebSocketUtil() {
        // Utility class.
    }

    /**
     * @param subPath The sub path relative to {@link ResourcePaths#WEB_SOCKET_ROOT_PATH}
     * @return The full url for the web socket
     */
    public static String createWebSocketUrl(final String subPath) {
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

        final String path = ResourcePaths.buildAuthenticatedWebSocketPath(subPath);
        final String url = scheme + "://" + hostPageBaseUrl + path;
        GWT.log("url: " + url);
        return url;
    }
}
