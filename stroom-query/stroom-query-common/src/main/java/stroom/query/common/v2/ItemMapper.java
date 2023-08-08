package stroom.query.common.v2;

import stroom.query.api.v2.Field;

import java.util.List;

public interface ItemMapper<R> {

    R create(List<Field> fields,
             Item item);

    boolean hidesRows();
}
