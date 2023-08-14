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

    public static SearchRequestSource createBasic() {
        return builder().sourceType(SourceType.DASHBOARD_UI).build();
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
                "sourceType=" + sourceType +
                ", ownerDocUuid='" + ownerDocUuid + '\'' +
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
        ANALYTIC_RULE("Analytic Rule", true, true, true),
        BATCH_ANALYTIC_RULE("Batch Analytic Rule", false, false, false),
        ANALYTIC_RULE_UI("Analytic Rule UI", true, true, true),
        DASHBOARD_UI("Dashboard UI", false, false, false),
        QUERY_UI("Query UI", false, false, false),
        API("API Request", false, false, false),
        BATCH_SEARCH("Batch Search", false, false, false);

        private final boolean requireTimeValue;
        private final boolean requireStreamIdValue;
        private final boolean requireEventIdValue;
        private final String displayValue;

        SourceType(final String displayValue,
                   final boolean requireTimeValue,
                   final boolean requireStreamIdValue,
                   final boolean requireEventIdValue) {
            this.displayValue = displayValue;
            this.requireTimeValue = requireTimeValue;
            this.requireStreamIdValue = requireStreamIdValue;
            this.requireEventIdValue = requireEventIdValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        public boolean isRequireTimeValue() {
            return requireTimeValue;
        }

        public boolean isRequireStreamIdValue() {
            return requireStreamIdValue;
        }

        public boolean isRequireEventIdValue() {
            return requireEventIdValue;
        }
    }
}
