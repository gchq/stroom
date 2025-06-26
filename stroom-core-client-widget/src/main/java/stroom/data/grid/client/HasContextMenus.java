package stroom.data.grid.client;

import stroom.widget.menu.client.presenter.Item;

import com.google.gwt.cell.client.Cell.Context;

import java.util.List;

/**
 * Interface for cells to provide unique context menu items.
 * @param <C> The type of the cell  value.
 */
public interface HasContextMenus<C> {
    /**
     * Called by the data grid when a context menu is requested for the cell.
     * @param context The {@link Context} of the cell.
     * @param value The value of the cell.
     * @return A list of {@link Item}s to be added to the context menu, or null/empty if none.
     */
    List<Item> getContextMenuItems(Context context, C value);

}
