package stroom.query.common.v2;

import java.util.Collections;
import java.util.List;

public class IdentityItemMapper implements ItemMapper<Item> {

    public static final IdentityItemMapper INSTANCE = new IdentityItemMapper();

    private IdentityItemMapper() {
        // Single instance.
    }

    @Override
    public List<Item> create(final Item item) {
        return Collections.singletonList(item);
    }
}
