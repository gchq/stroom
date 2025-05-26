package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum HashLength implements HasDisplayValue {
    INTEGER("Integer"), // 4 bytes
    LONG("Long"); // 8 bytes

    public static final List<HashLength> ORDERED_LIST = List.of(
            INTEGER,
            LONG);

    private final String displayValue;

    HashLength(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
