package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.item.client.SimpleSelectionListModel;

import java.util.function.Consumer;

public class SimpleFieldSelectionListModel
        extends SimpleSelectionListModel<FieldInfo>
        implements FieldSelectionListModel {

    @Override
    public void findFieldByName(final String fieldName, final Consumer<FieldInfo> consumer) {
        if (fieldName != null) {
            items.stream()
                    .filter(fieldInfo -> fieldInfo.getLabel().equals(fieldName))
                    .findAny()
                    .ifPresent(item -> consumer.accept(((FieldInfoSelectionItem) consumer).getFieldInfo()));
        }
    }
}
