package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum MaxValueSize implements HasDisplayValue {
    ONE("Max 255"),
    TWO("Max 65,535"),
    THREE("Max 16,777,215"),
    FOUR("Max 4,294,967,295"),
    FIVE("Max 1,099,511,627,775"),
    SIX("Max 281,474,976,710,655"),
    SEVEN("Max 72,057,594,037,927,900"),
    EIGHT("Max 9,223,372,036,854,780,000");

    public static final List<MaxValueSize> ORDERED_LIST = List.of(
            ONE,
            TWO,
            THREE,
            FOUR,
            FIVE,
            SIX,
            SEVEN,
            EIGHT);

    private final String displayValue;

    MaxValueSize(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
