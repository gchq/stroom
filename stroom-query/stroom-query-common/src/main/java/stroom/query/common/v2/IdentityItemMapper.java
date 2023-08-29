package stroom.query.common.v2;

import stroom.query.api.v2.Field;

import java.util.List;

public class IdentityItemMapper implements ItemMapper<Item> {

    public static final IdentityItemMapper INSTANCE = new IdentityItemMapper();

    private IdentityItemMapper() {
        // Single instance.
    }

    @Override
    public Item create(final List<Field> fields, final Item item) {
        return item;
    }

    @Override
    public boolean hidesRows() {
        return false;
    }
}
