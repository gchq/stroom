package stroom.ui.config.shared;

import stroom.docref.DocRef;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticUiDefaultConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    @JsonPropertyDescription("The default node to execute the analytic on.")
    private final String defaultNode;
    @JsonProperty
    @JsonPropertyDescription("The default feed to send analytic errors to.")
    private final DocRef defaultErrorFeed;
    @JsonProperty
    @JsonPropertyDescription("The default feed to send analytic results to.")
    private final DocRef defaultDestinationFeed;

    public AnalyticUiDefaultConfig() {
        defaultNode = null;
        defaultErrorFeed = null;
        defaultDestinationFeed = null;
    }

    @JsonCreator
    public AnalyticUiDefaultConfig(@JsonProperty("defaultNode") final String defaultNode,
                                   @JsonProperty("defaultErrorFeed") final DocRef defaultErrorFeed,
                                   @JsonProperty("defaultDestinationFeed") final DocRef defaultDestinationFeed) {
        this.defaultNode = defaultNode;
        this.defaultErrorFeed = defaultErrorFeed;
        this.defaultDestinationFeed = defaultDestinationFeed;
    }

    public String getDefaultNode() {
        return defaultNode;
    }

    public DocRef getDefaultErrorFeed() {
        return defaultErrorFeed;
    }

    public DocRef getDefaultDestinationFeed() {
        return defaultDestinationFeed;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticUiDefaultConfig that = (AnalyticUiDefaultConfig) o;
        return Objects.equals(defaultNode, that.defaultNode) &&
                Objects.equals(defaultErrorFeed, that.defaultErrorFeed) &&
                Objects.equals(defaultDestinationFeed, that.defaultDestinationFeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultNode, defaultErrorFeed, defaultDestinationFeed);
    }

    @Override
    public String toString() {
        return "AnalyticConfig{" +
                "defaultNode='" + defaultNode + '\'' +
                ", defaultErrorFeed=" + defaultErrorFeed +
                ", defaultDestinationFeed=" + defaultDestinationFeed +
                '}';
    }
}
