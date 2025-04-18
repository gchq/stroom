package stroom.query.api;

import stroom.docref.DocRef;
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
    private final DocRef ownerDocRef;
    @JsonProperty
    private final String componentId;

    @JsonCreator
    public SearchRequestSource(
            @JsonProperty("sourceType") final SourceType sourceType,
            @JsonProperty("ownerDocRef") final DocRef ownerDocRef,
            @JsonProperty("componentId") final String componentId) {
        this.sourceType = sourceType;
        this.ownerDocRef = ownerDocRef;
        this.componentId = componentId;
    }

    public static SearchRequestSource createBasic() {
        return builder().sourceType(SourceType.DASHBOARD_UI).build();
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public DocRef getOwnerDocRef() {
        return ownerDocRef;
    }

    public String getComponentId() {
        return componentId;
    }

    @Override
    public String toString() {
        return "SearchRequestSource{" +
                "sourceType=" + sourceType +
                ", ownerDocRef='" + ownerDocRef + '\'' +
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
        private DocRef ownerDocRef;
        private String componentId;

        private Builder() {
        }

        private Builder(final SearchRequestSource searchRequestSource) {
            this.sourceType = searchRequestSource.sourceType;
            this.ownerDocRef = searchRequestSource.ownerDocRef;
            this.componentId = searchRequestSource.componentId;
        }

        public Builder sourceType(final SourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder ownerDocRef(final DocRef ownerDocRef) {
            this.ownerDocRef = ownerDocRef;
            return this;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public SearchRequestSource build() {
            return new SearchRequestSource(
                    sourceType,
                    ownerDocRef,
                    componentId);
        }
    }

    public enum SourceType implements HasDisplayValue {
        TABLE_BUILDER_ANALYTIC("Table Builder Analytic", true, true, true),
        SCHEDULED_QUERY_ANALYTIC("Scheduled Query Analytic", false, false, false),
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
