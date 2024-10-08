package stroom.dashboard.client.table;

import stroom.query.api.v2.Column;
import stroom.query.client.presenter.TableRow;

import java.util.List;
import java.util.Set;

public interface HasSelectedRows {

    List<Column> getColumns();

    List<TableRow> getSelectedRows();

    Set<String> getHighlights();
}
