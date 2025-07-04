package stroom.query.common.v2;

import java.util.stream.Stream;

public class IdentityItemMapper implements ItemMapper {

    public static final IdentityItemMapper INSTANCE = new IdentityItemMapper();

    private IdentityItemMapper() {
        // Single instance.
    }

    @Override
    public Stream<Item> create(final Item item) {
        return Stream.of(item);
    }
}
