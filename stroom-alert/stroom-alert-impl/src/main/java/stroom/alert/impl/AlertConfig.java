package stroom.alert.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import javax.validation.constraints.Pattern;

@Singleton
public class AlertConfig extends AbstractConfig implements IsStroomConfig {
    @JsonPropertyDescription("Name of timezone (ZoneId) that will be used during alert generation.")
    private String timezone = "UTC";

    @JsonPropertyDescription("Comma delimited list of the Stroom folder explorer paths used " +
            "to hold dashboards that will be run as rules, in order to create alerts during indexing.")
    private List<String> rulesFolderList = new ArrayList<>();

    @JsonPropertyDescription("Should alerts include all fields from extraction pipeline," +
            " in addition to those defined in the dashboard")
    private boolean reportAllExtractedFieldsEnabled = false;

    @JsonPropertyDescription("When reporting fields defined in the extraction pipeline, a prefix can be added to " +
            "the field names in order to differentiate these from the fields defined in the dashboard")
    private String additionalFieldsPrefix = "_";


    @JsonProperty
    public List<String> getRulesFolderList() {
        return rulesFolderList;
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
    public void setAdditionalFieldsPrefix(final String additionalFieldsPrefix) {
        this.additionalFieldsPrefix = additionalFieldsPrefix;
    }

    @JsonProperty
    public void setReportAllExtractedFieldsEnabled(final boolean reportAllExtractedFieldsEnabled) {
        this.reportAllExtractedFieldsEnabled = reportAllExtractedFieldsEnabled;
    }

    @JsonProperty
    public void setRulesFolderList(final List<String> rulesFolderList) {
        this.rulesFolderList = rulesFolderList;
    }

    @JsonProperty
    public void setTimezone(final String timezone) {
        this.timezone = timezone;
    }

    @Override
    public String toString() {
        return "AlertConfig{" +
                "timezone='" + timezone + '\'' +
                ", rulesFolderList=[" + rulesFolderList.stream().map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(", ")) + ']' +
                ", reportAllExtractedFields='" + reportAllExtractedFieldsEnabled + '\'' +
                ", additionalFieldsPrefix='" + additionalFieldsPrefix + '\'' +
                '}';
    }
}
