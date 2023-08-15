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
    @JsonPropertyDescription("The default feed to send analytic results to.")
    private final DocRef defaultFeed;

    public AnalyticUiDefaultConfig() {
        defaultNode = null;
        defaultFeed = null;
    }

    @JsonCreator
    public AnalyticUiDefaultConfig(@JsonProperty("defaultNode") final String defaultNode,
                                   @JsonProperty("defaultFeed") final DocRef defaultFeed) {
        this.defaultNode = defaultNode;
        this.defaultFeed = defaultFeed;
    }

    public String getDefaultNode() {
        return defaultNode;
    }

    public DocRef getDefaultFeed() {
        return defaultFeed;
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
        return Objects.equals(defaultNode, that.defaultNode) && Objects.equals(defaultFeed,
                that.defaultFeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultNode, defaultFeed);
    }

    @Override
    public String toString() {
        return "AnalyticConfig{" +
                "defaultNode='" + defaultNode + '\'' +
                ", defaultFeed=" + defaultFeed +
                '}';
    }
}
