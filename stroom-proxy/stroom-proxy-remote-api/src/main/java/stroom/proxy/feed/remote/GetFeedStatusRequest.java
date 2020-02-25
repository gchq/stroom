package stroom.proxy.feed.remote;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.proxy.remote.RemoteRequest;

import java.util.Objects;

@JsonInclude(Include.NON_DEFAULT)
public class GetFeedStatusRequest extends RemoteRequest {
    private static final long serialVersionUID = -4083508707616388035L;

    @JsonProperty
    private String feedName;
    @JsonProperty
    private String senderDn;

    public GetFeedStatusRequest() {
    }

    public GetFeedStatusRequest(final String feedName) {
        this.feedName = feedName;
    }

    @JsonCreator
    public GetFeedStatusRequest(@JsonProperty("feedName") final String feedName,
                                @JsonProperty("senderDn") final String senderDn) {
        this.feedName = feedName;
        this.senderDn = senderDn;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getSenderDn() {
        return senderDn;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GetFeedStatusRequest that = (GetFeedStatusRequest) o;
        return Objects.equals(feedName, that.feedName) &&
                Objects.equals(senderDn, that.senderDn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, senderDn);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("request ");
        builder.append(feedName);
        if (senderDn != null) {
            builder.append(" - ");
            builder.append(senderDn);
        }
        return builder.toString();
    }

}
