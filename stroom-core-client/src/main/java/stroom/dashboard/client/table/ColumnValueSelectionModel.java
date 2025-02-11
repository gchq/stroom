package stroom.dashboard.client.table;

import stroom.cell.tickbox.shared.TickBoxState;

public interface ColumnValueSelectionModel {

    TickBoxState getState(String columnValue);
}
