package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.item.client.SelectionItem;
import stroom.svg.shared.SvgImage;

import java.util.Objects;

public class FieldInfoSelectionItem implements SelectionItem {

    private final FieldInfo fieldInfo;

    public FieldInfoSelectionItem(final FieldInfo fieldInfo) {
        this.fieldInfo = fieldInfo;
    }

    @Override
    public String getLabel() {
        if (fieldInfo == null) {
            return "[ none ]";
        }
        return fieldInfo.getFldName();
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public boolean isHasChildren() {
        return false;
    }

    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldInfoSelectionItem)) {
            return false;
        }
        final FieldInfoSelectionItem that = (FieldInfoSelectionItem) o;
        return Objects.equals(fieldInfo, that.fieldInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldInfo);
    }

    @Override
    public String toString() {
        return "FieldInfoSelectionItem{" +
                "fieldInfo=" + fieldInfo +
                '}';
    }
}
