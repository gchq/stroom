package stroom.proxy.feed.remote;

import stroom.proxy.remote.RemoteRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class GetFeedStatusRequestV2 extends RemoteRequest {

    private static final long serialVersionUID = -4083508707616388035L;

    @JsonProperty
    private String feedName;
    @JsonProperty
    private String subjectId;
    @JsonProperty
    private Map<String, String> attributeMap;

    /**
     * @param feedName Name of the feed being checked
     * @param subjectId The subjectId of the identity that sent the data that has
     *                  triggered this feed check.
     * @param attributeMap The map of headers from the request. Keys are case-insensitive.
     */
    @JsonCreator
    public GetFeedStatusRequestV2(@JsonProperty("feedName") final String feedName,
                                  @JsonProperty("senderDn") final String subjectId,
                                  @JsonProperty("attributeMap") final Map<String, String> attributeMap) {
        this.feedName = feedName;
        this.subjectId = subjectId;
        this.attributeMap = attributeMap;
    }

    /**
     * @return The name of
     */
    public String getFeedName() {
        return feedName;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public Map<String, String> getAttributeMap() {
        return attributeMap;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final GetFeedStatusRequestV2 that = (GetFeedStatusRequestV2) object;
        return Objects.equals(feedName, that.feedName) && Objects.equals(subjectId,
                that.subjectId) && Objects.equals(attributeMap, that.attributeMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, subjectId, attributeMap);
    }

    @Override
    public String toString() {
        return "GetFeedStatusRequestV2{" +
                "feedName='" + feedName + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", attributeMap=" + attributeMap +
                '}';
    }
}
