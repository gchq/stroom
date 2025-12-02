package stroom.index.shared;

import stroom.docref.HasDisplayValue;

public enum LuceneVersion implements HasDisplayValue {
    LUCENE_4_6_0("LUCENE_46"),
    LUCENE_5_5_3("5.5.3"),
    LUCENE_9_8_0("9.8.0"),
    LUCENE_10_3_1("10.3.1");

    private final String displayValue;

    LuceneVersion(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
