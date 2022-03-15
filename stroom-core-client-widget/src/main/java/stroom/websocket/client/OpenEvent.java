package stroom.websocket.client;

/**
 * <pre>
 * {
 * isTrusted: true
 * bubbles: false
 * cancelBubble: false
 * cancelable: false
 * composed: false
 * currentTarget: WebSocket {
 *   url: 'ws://localhost:8080/active-queries-ws',
 *   readyState: 1,
 *   bufferedAmount: 0,
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
 *   readyState: 1,
 *   bufferedAmount: 0,
 *   onopen: ƒ,
 *   onerror: ƒ,
 *   …
 * }
 * target: WebSocket {
 *   url: 'ws://localhost:8080/active-queries-ws',
 *   readyState: 1,
 *   bufferedAmount: 0,
 *   onopen: ƒ,
 *   onerror: ƒ,
 *   …
 * }
 * timeStamp: 3880.10000000149
 * type: "open"
 * }
 * </pre>
 */
public final class OpenEvent extends WebSocketEvent {

    /**
     * Required constructor for GWT compiler to function.
     */
    protected OpenEvent() {
    }

    private native boolean isTrusted() /*-{
        return this.isTrusted;
    }-*/;

    private native boolean bubbles() /*-{
        return this.bubbles;
    }-*/;

    private native boolean cancelBubble() /*-{
        return this.cancelBubble;
    }-*/;

    private native boolean cancelable() /*-{
        return this.cancelable;
    }-*/;

    private native boolean composed() /*-{
        return this.composed;
    }-*/;

    private native double timeStamp() /*-{
        return this.timeStamp;
    }-*/;

    private native String type() /*-{
        return this.type;
    }-*/;
}
