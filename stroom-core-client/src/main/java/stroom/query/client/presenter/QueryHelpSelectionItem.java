package stroom.query.client.presenter;

import stroom.item.client.SelectionItem;
import stroom.query.shared.QueryHelpRow;
import stroom.svg.shared.SvgImage;

import java.util.Objects;

public class QueryHelpSelectionItem implements SelectionItem {

    private final QueryHelpRow queryHelpRow;

    public QueryHelpSelectionItem(final QueryHelpRow queryHelpRow) {
        this.queryHelpRow = queryHelpRow;
    }

    @Override
    public String getLabel() {
        return queryHelpRow.getTitle();
    }

    @Override
    public SvgImage getIcon() {
        return queryHelpRow.getIcon();
    }

    @Override
    public boolean isHasChildren() {
        return queryHelpRow.isHasChildren();
    }

    public QueryHelpRow getQueryHelpRow() {
        return queryHelpRow;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryHelpSelectionItem)) {
            return false;
        }
        final QueryHelpSelectionItem that = (QueryHelpSelectionItem) o;
        return Objects.equals(queryHelpRow, that.queryHelpRow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryHelpRow);
    }

    @Override
    public String toString() {
        return "QueryHelpSelectionItem{" +
                "fieldInfo=" + queryHelpRow +
                '}';
    }
}
