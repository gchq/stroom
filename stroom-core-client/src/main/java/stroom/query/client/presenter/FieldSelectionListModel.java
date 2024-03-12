package stroom.query.client.presenter;

import stroom.datasource.api.v2.QueryField;
import stroom.item.client.SelectionListModel;

import java.util.function.Consumer;

public interface FieldSelectionListModel extends SelectionListModel<QueryField, FieldInfoSelectionItem> {
    void findFieldByName(String fieldName, Consumer<QueryField> consumer);
}
