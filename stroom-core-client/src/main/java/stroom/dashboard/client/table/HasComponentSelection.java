package stroom.dashboard.client.table;

import stroom.query.api.v2.ColumnRef;

import java.util.List;
import java.util.Set;

public interface HasComponentSelection {

    List<ColumnRef> getColumns();

    List<ComponentSelection> getSelection();

    Set<String> getHighlights();
}
