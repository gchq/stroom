package stroom.analytics.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StreamingAnalyticProcessConfig.class, name = "streaming"),
        @JsonSubTypes.Type(value = TableBuilderAnalyticProcessConfig.class, name = "table_builder"),
        @JsonSubTypes.Type(value = ScheduledQueryAnalyticProcessConfig.class, name = "scheduled_query"),
})
public abstract class AnalyticProcessConfig {

    @JsonProperty
    boolean enabled;
    @JsonProperty
    final String node;
    @JsonProperty
    final DocRef errorFeed;

    public AnalyticProcessConfig(@JsonProperty("enabled") final boolean enabled,
                                 @JsonProperty("node") final String node,
                                 @JsonProperty("errorFeed") final DocRef errorFeed) {
        this.enabled = enabled;
        this.node = node;
        this.errorFeed = errorFeed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getNode() {
        return node;
    }

    public DocRef getErrorFeed() {
        return errorFeed;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalyticProcessConfig)) {
            return false;
        }
        final AnalyticProcessConfig that = (AnalyticProcessConfig) o;
        return enabled == that.enabled &&
                Objects.equals(node, that.node) &&
                Objects.equals(errorFeed, that.errorFeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, node, errorFeed);
    }

    @Override
    public String toString() {
        return "AnalyticProcessConfig{" +
                "enabled=" + enabled +
                ", node=" + node +
                ", errorFeed=" + errorFeed +
                '}';
    }
}
