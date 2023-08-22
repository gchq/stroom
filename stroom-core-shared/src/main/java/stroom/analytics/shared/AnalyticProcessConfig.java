package stroom.analytics.shared;

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
public abstract class AnalyticProcessConfig<B extends AbstractAnalyticProcessConfigBuilder<?, ?>> {

    @JsonProperty
    final boolean enabled;
    @JsonProperty
    final String node;

    public AnalyticProcessConfig(@JsonProperty("enabled") final boolean enabled,
                                 @JsonProperty("node") final String node) {
        this.enabled = enabled;
        this.node = node;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNode() {
        return node;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalyticProcessConfig)) {
            return false;
        }
        final AnalyticProcessConfig<?> that = (AnalyticProcessConfig<?>) o;
        return enabled == that.enabled &&
                Objects.equals(node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, node);
    }

    @Override
    public String toString() {
        return "AnalyticProcessConfig{" +
                "enabled=" + enabled +
                ", node=" + node +
                '}';
    }

    public abstract B copy();
}
