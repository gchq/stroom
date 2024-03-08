package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.item.client.AbstractSelectionListModel;

import java.util.function.Consumer;

public class SimpleFieldSelectionListModel
        extends AbstractSelectionListModel<FieldInfo, FieldInfoSelectionItem>
        implements FieldSelectionListModel {

    @Override
    public void findFieldByName(final String fieldName, final Consumer<FieldInfo> consumer) {
        if (fieldName != null) {
            items.stream()
                    .filter(fieldInfo -> fieldInfo.getLabel().equals(fieldName))
                    .findFirst()
                    .ifPresent(item -> consumer.accept(item.getFieldInfo()));
        } else {
            items.stream()
                    .findFirst()
                    .ifPresent(item -> consumer.accept(item.getFieldInfo()));
        }
    }

    @Override
    public FieldInfoSelectionItem wrap(final FieldInfo item) {
        return new FieldInfoSelectionItem(item);
    }

    @Override
    public FieldInfo unwrap(final FieldInfoSelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getFieldInfo();
    }
}
