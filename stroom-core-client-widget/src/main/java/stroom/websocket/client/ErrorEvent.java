package stroom.websocket.client;

/**
 * isTrusted: true
 * bubbles: false
 * cancelBubble: false
 * cancelable: false
 * composed: false
 * currentTarget: WebSocket {
 *   url: 'ws://localhost:8080/active-queries-ws',
 *   readyState: 3,
 *   bufferedAmount: 43,
 *   onopen: ƒ,
 *   onerror: ƒ,
 *   …
 * }
 * defaultPrevented: false
 * eventPhase: 0
 * path: []
 * returnValue: true
 * srcElement: WebSocket {
 *   url: 'ws://localhost:8080/active-queries-ws',
 *   readyState: 3,
 *   bufferedAmount: 43,
 *   onopen: ƒ,
 *   onerror: ƒ,
 *   …
 * }
 * target: WebSocket {
 *   url: 'ws://localhost:8080/active-queries-ws',
 *   readyState: 3,
 *   bufferedAmount: 43,
 *   onopen: ƒ,
 *   onerror: ƒ,
 *   …
 * }
 * timeStamp: 16817.79999999702
 * type: "error"
 */
public final class ErrorEvent extends WebSocketEvent {

    /**
     * Required constructor for GWT compiler to function.
     */
    protected ErrorEvent() {
    }

    private native int getCode() /*-{
        return this.code;
    }-*/;

    public String getReason() {
        final int code = getCode();

        String reason = "";
        if (code == 1000) {
            reason = "Normal closure, meaning that the purpose for which the connection was established has " +
                    "been fulfilled.";
        } else if (code == 1001) {
            reason = "An endpoint is \"going away\", such as a server going down or a browser having navigated " +
                    "away from a page.";
        } else if (code == 1002) {
            reason = "An endpoint is terminating the connection due to a protocol error";
        } else if (code == 1003) {
            reason = "An endpoint is terminating the connection because it has received a type of data it cannot " +
                    "accept (e.g., an endpoint that understands only text data MAY send this if it receives a " +
                    "binary message) {.";
        } else if (code == 1004) {
            reason = "Reserved. The specific meaning might be defined in the future.";
        } else if (code == 1005) {
            reason = "No status code was actually present.";
        } else if (code == 1006) {
            reason = "The connection was closed abnormally, e.g., without sending or receiving a Close control frame";
        } else if (code == 1007) {
            reason = "An endpoint is terminating the connection because it has received data within a message that " +
                    "was not consistent with the type of the message (e.g., non-UTF-8 " +
                    "[https://www.rfc-editor.org/rfc/rfc3629] data within a text message) {.";
        } else if (code == 1008) {
            reason = "An endpoint is terminating the connection because it has received a message " +
                    "that \"violates its policy\". This reason is given either if there is no other suitable reason, " +
                    "or if there is a need to hide specific details about the policy.";
        } else if (code == 1009) {
            reason = "An endpoint is terminating the connection because it has received a message that is too big " +
                    "for it to process.";
        } else if (code == 1010) {
            // Note that this status code is not used by the server, because it can fail the WebSocket
            // handshake instead.
            reason = "An endpoint (client) { is terminating the connection because it has expected the server to " +
                    "negotiate one or more extension, but the server didn't return them in the response message " +
                    "of the WebSocket handshake. <br /> Specifically, the extensions that are needed are: " + reason;
        } else if (code == 1011) {
            reason = "A server is terminating the connection because it encountered an unexpected condition that " +
                    "prevented it from fulfilling the request.";
        } else if (code == 1015) {
            reason = "The connection was closed due to a failure to perform a TLS handshake (e.g., the server " +
                    "certificate can't be verified) {.";
        } else {
            reason = "Unknown reason (code: " + code + ")";
        }

        return reason;
    }
}
