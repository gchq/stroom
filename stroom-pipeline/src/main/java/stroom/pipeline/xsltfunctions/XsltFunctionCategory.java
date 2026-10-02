package stroom.pipeline.xsltfunctions;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonValue;

public enum XsltFunctionCategory implements HasDisplayValue {

    CONVERSION(
            "Conversion",
            "Functions for converting a value from one form to another, e.g. hexToString."),
    DATE(
            "Date",
            "Functions for date/time parsing and manipulation."),
    STRING(
            "String",
            "Functions for string parsing and manipulation."),
    URI(
            "URI",
            "Functions relating to parsing and manipulating URI/URLs."),
    VALUE(
            "Value",
            "Functions that simply supply a value."),
    NETWORK(
            "Functions relating to networking (host names, IP addresses, etc.) or making remote calls.",
            ""),
    PIPELINE(
            "Pipeline",
            "Functions for obtaining information about the current pipeline process."),
    OTHER(
            "Other",
            "Functions that don't fit into any other category."),
    ;

    private final String displayValue;
    private final String description;

    XsltFunctionCategory(final String displayValue,
                         final String description) {
        this.displayValue = displayValue;
        this.description = description;
    }

    XsltFunctionCategory(final String displayValue) {
        this.displayValue = displayValue;
        this.description = null;
    }

    @JsonValue
    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getDescription() {
        return description;
    }
}
