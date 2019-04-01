package stroom.index.shared;

import stroom.docref.HasDisplayValue;

public enum AnalyzerType implements HasDisplayValue {
    KEYWORD("Keyword"),
    ALPHA("Alpha"),
    NUMERIC("Numeric"),
    ALPHA_NUMERIC("Alpha numeric"),
    WHITESPACE("Whitespace"),
    STOP("Stop words"),
    STANDARD("Standard");

    private final String displayValue;

    AnalyzerType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}