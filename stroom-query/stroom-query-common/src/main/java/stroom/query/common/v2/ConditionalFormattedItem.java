package stroom.query.common.v2;

import stroom.query.language.functions.Val;

public class ConditionalFormattedItem implements Item {

    private final Item item;
    private final String ruleId;

    public ConditionalFormattedItem(final Item item, final String ruleId) {
        this.item = item;
        this.ruleId = ruleId;
    }

    @Override
    public Key getKey() {
        return item.getKey();
    }

    @Override
    public Val getValue(final int index) {
        return item.getValue(index);
    }

    @Override
    public int size() {
        return item.size();
    }

    @Override
    public Val[] toArray() {
        return item.toArray();
    }

    public String getRuleId() {
        return ruleId;
    }

    public Item getItem() {
        return item;
    }
}
