package stroom.explorer.client.presenter;

import stroom.data.table.client.MyCellTable;
import stroom.explorer.client.event.FocusEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;

public class FindCellTable<T> extends MyCellTable<T> {

    private final MultiSelectionModelImpl<T> selectionModel;
    private final EventBus eventBus = new SimpleEventBus();


    public FindCellTable() {
        this(100);
    }

    public FindCellTable(final int pageSize) {
        super(pageSize);
        addStyleName("FindCellTable");

        selectionModel = new MultiSelectionModelImpl<>();
        final SelectionEventManager<T> selectionEventManager = new SelectionEventManager<>(
                this,
                selectionModel,
                doc -> {
                    final SelectionType selectionType = new SelectionType(
                            true,
                            false,
                            false,
                            false,
                            false);
                    selectionModel.setSelected(doc, true, selectionType, true);
                },
                null);
        setSelectionModel(selectionModel, selectionEventManager);
    }

    @Override
    protected void onBrowserEvent2(final Event event) {
        super.onBrowserEvent2(event);
        if (event.getTypeInt() == Event.ONKEYDOWN && event.getKeyCode() == KeyCodes.KEY_UP) {
            if (getKeyboardSelectedRow() == 0) {
                FocusEvent.fire(this);
            }
        }
    }

    public HandlerRegistration addFocusHandler(final FocusEvent.Handler handler) {
        return addHandler(handler, FocusEvent.getType());
    }

    @Override
    public MultiSelectionModelImpl<T> getSelectionModel() {
        return selectionModel;
    }
}
