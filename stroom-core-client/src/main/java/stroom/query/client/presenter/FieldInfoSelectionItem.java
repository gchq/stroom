package stroom.query.client.presenter;

import stroom.item.client.SelectionItem;
import stroom.query.api.datasource.QueryField;
import stroom.svg.shared.SvgImage;

import java.util.Objects;

public class FieldInfoSelectionItem implements SelectionItem {

    private final QueryField field;

    public FieldInfoSelectionItem(final QueryField field) {
        this.field = field;
    }

    @Override
    public String getLabel() {
        if (field == null) {
            return "[ none ]";
        }
        return field.getFldName();
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public boolean isHasChildren() {
        return false;
    }

    public QueryField getField() {
        return field;
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
        return Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field);
    }

    @Override
    public String toString() {
        return "FieldInfoSelectionItem{" +
                "field=" + field +
                '}';
    }
}
