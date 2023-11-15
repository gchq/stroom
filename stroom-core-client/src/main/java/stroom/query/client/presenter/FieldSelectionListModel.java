package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.item.client.SelectionListModel;

import java.util.function.Consumer;

public interface FieldSelectionListModel extends SelectionListModel<FieldInfo, FieldInfoSelectionItem> {
    void findFieldByName(String fieldName, Consumer<FieldInfo> consumer);
}
