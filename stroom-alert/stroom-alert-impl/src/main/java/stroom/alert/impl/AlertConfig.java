package stroom.alert.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.Pattern;

@Singleton
public class AlertConfig extends AbstractConfig {
    private static final String PATH_LIST_PATTERN = "^[^,]+(,[ ]?[^,]+)*$";

    @JsonPropertyDescription("Name of timezone (ZoneId) that will be used during alert generation.")
    private String timezone = "UTC";

    @JsonPropertyDescription("Comma delimited list of the Stroom folder explorer paths used " +
            "to hold dashboards that will be run as rules, in order to create alerts during indexing.")
    @Pattern(regexp = PATH_LIST_PATTERN, message = "Value must be a comma delimited string of paths (separate folders with / character)")
    private String rulesFolderList = "";

    @JsonPropertyDescription("Should alerts include all fields from extraction pipeline, in addition to those defined in the dashboard")
    private boolean reportAllExtractedFieldsEnabled = false;

    @JsonPropertyDescription("When reporting fields defined in the extraction pipeline, a prefix can be added to " +
            "the field names in order to differentiate these from the fields defined in the dashboard")
    private String additionalFieldsPrefix = "_";


    public String getRulesFolderList() {
        return rulesFolderList;
    }


    public boolean isReportAllExtractedFieldsEnabled() {
        return reportAllExtractedFieldsEnabled;
    }


    public String getAdditionalFieldsPrefix() {
        return additionalFieldsPrefix;
    }

    public String getTimezone() {
        return timezone;
    }

    @Override
    public String toString() {
        return "AlertConfig{" +
                "timezone='" + timezone + '\'' +
                ", rulesFolderList='" + rulesFolderList + '\'' +
                ", reportAllExtractedFields='" + reportAllExtractedFieldsEnabled + '\'' +
                ", additionalFieldsPrefix='" + additionalFieldsPrefix + '\'' +
                '}';
    }
}
