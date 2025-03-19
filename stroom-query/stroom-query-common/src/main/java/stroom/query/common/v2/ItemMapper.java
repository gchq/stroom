package stroom.query.common.v2;

public interface ItemMapper<R> {

    R create(Item item);

    default boolean hidesRows() {
        return false;
    }
}
