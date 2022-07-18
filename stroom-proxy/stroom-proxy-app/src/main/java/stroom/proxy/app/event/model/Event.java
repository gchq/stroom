package stroom.proxy.app.event.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "version",
        "event-id",
        "proxy-id",
        "feed",
        "type",
        "receive-time",
        "headers",
        "detail"
})
public class Event {

    @JsonProperty("version")
    private final int version;
    @JsonProperty("event-id")
    private final String eventId;
    @JsonProperty("proxy-id")
    private final String proxyId;
    @JsonProperty("feed")
    private final String feed;
    @JsonProperty("type")
    private final String type;
    @JsonProperty("receive-time")
    private final String receiveTime;
    @JsonProperty("headers")
    private final List<Header> headers;
    @JsonProperty("detail")
    private final String detail;

    @JsonCreator
    public Event(@JsonProperty("version") final int version,
                 @JsonProperty("event-id") final String eventId,
                 @JsonProperty("proxy-id") final String proxyId,
                 @JsonProperty("feed") final String feed,
                 @JsonProperty("type") final String type,
                 @JsonProperty("receive-time") final String receiveTime,
                 @JsonProperty("headers") final List<Header> headers,
                 @JsonProperty("detail") final String detail) {
        this.version = version;
        this.eventId = eventId;
        this.proxyId = proxyId;
        this.feed = feed;
        this.type = type;
        this.receiveTime = receiveTime;
        this.headers = headers;
        this.detail = detail;
    }

    public int getVersion() {
        return version;
    }

    public String getEventId() {
        return eventId;
    }

    public String getProxyId() {
        return proxyId;
    }

    public String getFeed() {
        return feed;
    }

    public String getType() {
        return type;
    }

    public String getReceiveTime() {
        return receiveTime;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public String getDetail() {
        return detail;
    }
}
