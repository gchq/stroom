package stroom.websocket.client;

/**
 * <pre>
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
 * data: "welcome"
 * defaultPrevented: false
 * eventPhase: 0
 * lastEventId: ""
 * origin: "ws://localhost:8080"
 * path: []
 * ports: []
 * returnValue: true
 * source: null
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
 * timeStamp: 3926.800000000745
 * type: "message"
 * userActivation: null
 * </pre>
 */
public final class MessageEvent extends WebSocketEvent {

    /**
     * Required constructor for GWT compiler to function.
     */
    protected MessageEvent() {
    }

    public native String getData()
    /*-{
        return this.data;
    }-*/;
}
