package stroom.alert.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.Pattern;

@Singleton
public class AlertConfig extends AbstractConfig {
    private static final String PATH_LIST_PATTERN = "^[^,]+(,[ ]?[^,]+)*$";

    private String timezone = "UTC";
    private String rulesFolderList = "";

    @JsonPropertyDescription("Comma delimited list of the Stroom folder explorer paths used " +
            "to hold dashboards that will be run as rules, in order to create alerts during indexing.")
    @Pattern(regexp = PATH_LIST_PATTERN, message = "Value must be a comma delimited string of paths (separate folders with / character)")
    public String getRulesFolderList() {
        return rulesFolderList;
    }

    @JsonPropertyDescription("Name of timezone (ZoneId) that will be used during alert generation.")
    public String getTimezone() {
        return timezone;
    }


    @Override
    public String toString() {
        return "AlertConfig{" +
                "timezone='" + timezone + '\'' +
                ", rulesFolderList='" + rulesFolderList + '\'' +
                '}';
    }
}
