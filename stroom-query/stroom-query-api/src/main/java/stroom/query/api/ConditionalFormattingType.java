package stroom.query.api;

import stroom.docref.HasDisplayValue;

import java.util.ArrayList;
import java.util.List;

public enum ConditionalFormattingType implements HasDisplayValue {
    BACKGROUND("Background"),
    TEXT("Text"),
    CUSTOM("Custom"),
    ;

    public static final List<ConditionalFormattingType> LIST = new ArrayList<>();

    static {
        LIST.add(BACKGROUND);
        LIST.add(TEXT);
        LIST.add(CUSTOM);
    }

    private final String displayValue;

    ConditionalFormattingType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
