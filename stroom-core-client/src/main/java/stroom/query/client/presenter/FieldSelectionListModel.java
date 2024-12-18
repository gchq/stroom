package stroom.query.client.presenter;

import stroom.datasource.api.v2.QueryField;
import stroom.item.client.SelectionListModel;
import stroom.task.client.HasTaskMonitorFactory;

import java.util.function.Consumer;

public interface FieldSelectionListModel extends
        SelectionListModel<QueryField, FieldInfoSelectionItem>,
        HasTaskMonitorFactory {
    void findFieldByName(String fieldName, Consumer<QueryField> consumer);
}
