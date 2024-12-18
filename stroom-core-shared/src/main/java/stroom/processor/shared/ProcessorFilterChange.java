package stroom.processor.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum ProcessorFilterChange implements HasDisplayValue {
    ENABLE(
            "Enable",
            "Enable."),
    DISABLE(
            "Disable",
            "Disable."),
    DELETE(
            "Delete",
            "Delete."),
    SET_RUN_AS_USER(
            "Set run as user",
            "Set a run asuser.");

    public static final List<ProcessorFilterChange> LIST = List.of(
            ENABLE,
            DISABLE,
            DELETE,
            SET_RUN_AS_USER
    );

    private final String displayValue;
    private final String description;

    ProcessorFilterChange(final String displayValue,
                          final String description) {
        this.displayValue = displayValue;
        this.description = description;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getDescription() {
        return description;
    }
}
