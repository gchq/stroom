package stroom.websocket.client;

/**
 * <pre>
 * isTrusted: true
 * bubbles: false
 * cancelBubble: false
 * cancelable: false
 * code: 1006
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
 * reason: ""
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
 * timeStamp: 192002.69999999925
 * type: "close"
 * wasClean: false
 * </pre>
 */
public final class CloseEvent extends WebSocketEvent {

    /**
     * Required constructor for GWT compiler to function.
     */
    protected CloseEvent() {
    }

    private native String getCode() /*-{
        return this.code;
    }-*/;
}
