package stroom.dashboard.client.table;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.ColumnValueSelection;

public interface FilterCellManager {

    void setValueFilter(Column column, String valueFilter);

    void setValueSelection(Column column, ColumnValueSelection columnValueSelection);
}
