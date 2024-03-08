package stroom.query.common.v2;

import stroom.query.api.v2.Column;

import java.util.List;

public interface ItemMapper<R> {

    R create(List<Column> columns,
             Item item);

    boolean hidesRows();
}
