package stroom.policy.shared;

import stroom.util.shared.HasDisplayValue;

public enum DataReceiptAction implements HasDisplayValue {
    RECEIVE("Receive"), REJECT("Reject"), DROP("Drop");

    private final String displayValue;

    DataReceiptAction(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}