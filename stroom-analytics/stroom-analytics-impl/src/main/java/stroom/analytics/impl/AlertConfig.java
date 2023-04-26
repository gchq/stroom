package stroom.analytics.impl;

import stroom.query.common.v2.AnalyticStoreConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class AlertConfig extends AbstractConfig implements IsStroomConfig {

    @JsonPropertyDescription("Name of timezone (ZoneId) that will be used during alert generation.")
    private final String timezone;

    @JsonPropertyDescription("Should alerts include all fields from extraction pipeline," +
            " in addition to those defined in the dashboard")
    private final boolean reportAllExtractedFieldsEnabled;

    @JsonPropertyDescription("When reporting fields defined in the extraction pipeline, a prefix can be added to " +
            "the field names in order to differentiate these from the fields defined in the dashboard")
    private final String additionalFieldsPrefix;

    @JsonPropertyDescription("Configuration for the data store used for analytics.")
    private final AnalyticStoreConfig analyticStoreConfig;

    public AlertConfig() {
        timezone = "UTC";
        reportAllExtractedFieldsEnabled = false;
        additionalFieldsPrefix = "_";
        analyticStoreConfig = new AnalyticStoreConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AlertConfig(@JsonProperty("timezone") final String timezone,
                       @JsonProperty("reportAllExtractedFieldsEnabled") final boolean reportAllExtractedFieldsEnabled,
                       @JsonProperty("additionalFieldsPrefix") final String additionalFieldsPrefix,
                       @JsonProperty("analyticStoreConfig") final AnalyticStoreConfig analyticStoreConfig) {
        this.timezone = timezone;
        this.reportAllExtractedFieldsEnabled = reportAllExtractedFieldsEnabled;
        this.additionalFieldsPrefix = additionalFieldsPrefix;
        this.analyticStoreConfig = analyticStoreConfig;
    }

    @JsonProperty
    public boolean isReportAllExtractedFieldsEnabled() {
        return reportAllExtractedFieldsEnabled;
    }

    @JsonProperty
    public String getAdditionalFieldsPrefix() {
        return additionalFieldsPrefix;
    }

    @JsonProperty
    public String getTimezone() {
        return timezone;
    }

    @JsonProperty
    public AnalyticStoreConfig getAnalyticStoreConfig() {
        return analyticStoreConfig;
    }

    @Override
    public String toString() {
        return "AlertConfig{" +
                "timezone='" + timezone + '\'' +
                ", reportAllExtractedFields='" + reportAllExtractedFieldsEnabled + '\'' +
                ", additionalFieldsPrefix='" + additionalFieldsPrefix + '\'' +
                ", analyticStoreConfig='" + analyticStoreConfig + '\'' +
                '}';
    }
}
