package stroom.widget.util.client;

import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.view.client.CellPreviewEvent;

public class BasicSelectionEventManager<T>
        extends AbstractSelectionEventManager<T> {

    private final AbstractHasData<T> cellTable;

    public BasicSelectionEventManager(final AbstractHasData<T> cellTable) {
        super(cellTable);
        this.cellTable = cellTable;
    }

    @Override
    protected void onSpace(final CellPreviewEvent<T> e) {
        // Do the same as on enter.
        onEnter(e);
    }

    @Override
    protected void onEnter(final CellPreviewEvent<T> e) {
        final T item = e.getValue();
        cellTable.getSelectionModel().setSelected(item, true);
    }
}
