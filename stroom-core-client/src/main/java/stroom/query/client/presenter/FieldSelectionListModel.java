package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.item.client.SelectionListModel;

import java.util.function.Consumer;

public interface FieldSelectionListModel extends SelectionListModel {
    void fetchFieldByName(String fieldName, Consumer<FieldInfo> consumer);
}
