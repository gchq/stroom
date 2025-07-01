package stroom.query.common.v2;

import java.util.List;

public interface ItemMapper<R> {

    List<R> create(Item item);

    default boolean hidesRows() {
        return false;
    }
}
