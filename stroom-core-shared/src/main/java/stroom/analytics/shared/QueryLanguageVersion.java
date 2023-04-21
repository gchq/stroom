package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

public enum QueryLanguageVersion implements HasDisplayValue {
    STROOM_QL_VERSION_0_1("Stroom Query Language v0.1"),
    SIGMA("Sigma");

    private final String displayValue;

    QueryLanguageVersion(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
