package stroom.query.common.v2;

public class IdentityItemMapper implements ItemMapper<Item> {

    public static final IdentityItemMapper INSTANCE = new IdentityItemMapper();

    private IdentityItemMapper() {
        // Single instance.
    }

    @Override
    public Item create(final Item item) {
        return item;
    }
}
