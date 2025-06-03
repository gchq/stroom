package stroom.dashboard.client.table;

import stroom.query.api.Column;
import stroom.query.api.ColumnValueSelection;

public interface FilterCellManager {

    void setValueFilter(Column column, String valueFilter);

    void setValueSelection(Column column, ColumnValueSelection columnValueSelection);
}
