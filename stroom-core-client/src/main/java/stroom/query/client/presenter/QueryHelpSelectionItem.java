package stroom.query.client.presenter;

import stroom.item.client.SelectionItem;
import stroom.query.shared.QueryHelpRow;
import stroom.svg.shared.SvgImage;

public class QueryHelpSelectionItem implements SelectionItem {

    private final QueryHelpRow fieldInfo;

    public QueryHelpSelectionItem(final QueryHelpRow fieldInfo) {
        this.fieldInfo = fieldInfo;
    }

    @Override
    public String getLabel() {
        return fieldInfo.getTitle();
    }

    @Override
    public SvgImage getIcon() {
        return fieldInfo.getIcon();
    }

    @Override
    public boolean isHasChildren() {
        return fieldInfo.isHasChildren();
    }

    public QueryHelpRow getQueryHelpRow() {
        return fieldInfo;
    }
}
