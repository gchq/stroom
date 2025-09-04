package stroom.query.common.v2;

import java.util.stream.Stream;

public interface ItemMapper {

    Stream<Item> create(Item item);

    default boolean hidesRows() {
        return false;
    }
}
