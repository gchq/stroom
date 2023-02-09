package stroom.query.api.v2;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class SearchRequestSource {

    @JsonProperty
    private final SourceType sourceType;
    @JsonProperty
    private final String ownerDocUuid;
    @JsonProperty
    private final String componentId;

    @JsonCreator
    public SearchRequestSource(
            @JsonProperty("sourceType") final SourceType sourceType,
            @JsonProperty("ownerDocUuid") final String ownerDocUuid,
            @JsonProperty("componentId") final String componentId) {
        this.sourceType = sourceType;
        this.ownerDocUuid = ownerDocUuid;
        this.componentId = componentId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getOwnerDocUuid() {
        return ownerDocUuid;
    }

    public String getComponentId() {
        return componentId;
    }

    @Override
    public String toString() {
        return "SearchRequestSource{" +
                "dashboardUuid='" + ownerDocUuid + '\'' +
                ", componentId='" + componentId + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private SourceType sourceType;
        private String ownerDocUuid;
        private String componentId;

        private Builder() {
        }

        private Builder(final SearchRequestSource searchRequestSource) {
            this.sourceType = searchRequestSource.sourceType;
            this.ownerDocUuid = searchRequestSource.ownerDocUuid;
            this.componentId = searchRequestSource.componentId;
        }

        public Builder sourceType(final SourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder ownerDocUuid(final String ownerDocUuid) {
            this.ownerDocUuid = ownerDocUuid;
            return this;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public SearchRequestSource build() {
            return new SearchRequestSource(
                    sourceType,
                    ownerDocUuid,
                    componentId);
        }
    }

    public enum SourceType implements HasDisplayValue {
        ALERT_RULE("Alert Rule"),
        DASHBOARD_UI("Dashboard UI"),
        QUERY_UI("Query UI"),
        API("API Request"),
        BATCH_SEARCH("Batch Search");

        private final String displayValue;

        SourceType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
