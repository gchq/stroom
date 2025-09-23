package stroom.data.client.presenter;

import stroom.data.grid.client.HasContextMenus;
import stroom.widget.menu.client.presenter.Item;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.List;

public class HasContextMenusCell<T_CELL> extends AbstractCell<T_CELL> implements HasContextMenus<T_CELL> {

    private final HasContextMenus<T_CELL> hasContextMenus;

    public HasContextMenusCell(final HasContextMenus<T_CELL> hasContextMenus) {
        this.hasContextMenus = hasContextMenus;
    }

    @Override
    public void render(final Context context, final T_CELL value, final SafeHtmlBuilder sb) {
        if (value != null) {
            if (value instanceof final SafeHtml safeHtml) {
                sb.append(safeHtml);
            } else {
                sb.append(SafeHtmlUtils.fromString(value.toString()));
            }
        }
    }

    @Override
    public List<Item> getContextMenuItems(final Context context, final T_CELL value) {
        return hasContextMenus.getContextMenuItems(context, value);
    }
}
