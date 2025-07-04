package stroom.query.common.v2;

import stroom.query.api.Row;

public interface RowCreator {

    Row create(Item item);
}
