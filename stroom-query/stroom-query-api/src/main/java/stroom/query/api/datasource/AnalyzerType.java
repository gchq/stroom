package stroom.query.api.datasource;

import stroom.docref.HasDisplayValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum AnalyzerType implements HasDisplayValue {
    KEYWORD("Keyword"),
    ALPHA("Alpha"),
    NUMERIC("Numeric"),
    ALPHA_NUMERIC("Alpha numeric"),
    WHITESPACE("Whitespace"),
    STOP("Stop words"),
    STANDARD("Standard");

    private static final Map<String, AnalyzerType> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put(KEYWORD.displayValue.toLowerCase(Locale.ROOT), KEYWORD);
        TYPE_MAP.put(ALPHA.displayValue.toLowerCase(Locale.ROOT), ALPHA);
        TYPE_MAP.put(NUMERIC.displayValue.toLowerCase(Locale.ROOT), NUMERIC);
        TYPE_MAP.put(ALPHA_NUMERIC.displayValue.toLowerCase(Locale.ROOT), ALPHA_NUMERIC);
        TYPE_MAP.put(WHITESPACE.displayValue.toLowerCase(Locale.ROOT), WHITESPACE);
        TYPE_MAP.put(STOP.displayValue.toLowerCase(Locale.ROOT), STOP);
        TYPE_MAP.put(STANDARD.displayValue.toLowerCase(Locale.ROOT), STANDARD);
    }

    public static AnalyzerType fromDisplayValue(final String displayValue) {
        return TYPE_MAP.get(displayValue.toLowerCase(Locale.ROOT));
    }

    private final String displayValue;

    AnalyzerType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
