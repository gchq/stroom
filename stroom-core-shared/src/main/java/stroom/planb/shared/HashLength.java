package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

public enum HashLength implements HasDisplayValue {
    INTEGER("Integer (4 bytes)"),
    LONG("Long (8 bytes)");

    private final String displayValue;

    HashLength(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
