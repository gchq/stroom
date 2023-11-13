package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.item.client.SelectionItem;
import stroom.svg.shared.SvgImage;

public class FieldInfoSelectionItem implements SelectionItem {

    private final FieldInfo fieldInfo;

    public FieldInfoSelectionItem(final FieldInfo fieldInfo) {
        this.fieldInfo = fieldInfo;
    }

    @Override
    public String getLabel() {
        return fieldInfo.getTitle();
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public boolean isHasChildren() {
        return fieldInfo.isHasChildren();
    }

    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }
}
