package stroom.security.shared;

import stroom.docref.HasDisplayValue;

import java.util.ArrayList;
import java.util.List;

public enum PermissionShowLevel implements HasDisplayValue {
    SHOW_EXPLICIT("Show Explicit"),
    SHOW_EFFECTIVE("Show Effective"),
    SHOW_ALL("Show All");

    public static final List<PermissionShowLevel> ITEMS = new ArrayList<>();

    static {
        ITEMS.add(SHOW_EXPLICIT);
        ITEMS.add(SHOW_EFFECTIVE);
        ITEMS.add(SHOW_ALL);
    }

    private final String displayValue;

    PermissionShowLevel(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
