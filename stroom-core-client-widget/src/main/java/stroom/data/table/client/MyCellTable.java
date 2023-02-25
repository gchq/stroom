package stroom.data.table.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.resources.client.CssResource.ImportedWithPrefix;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.view.client.CellPreviewEvent.Handler;
import com.google.gwt.view.client.SelectionModel;

public class MyCellTable<T> extends CellTable<T> {

    private static final Resources RESOURCES = GWT.create(DefaultResources.class);

    public MyCellTable(int pageSize) {
        super(pageSize, RESOURCES);
        addStyleName("w-100");
        setLoadingIndicator(null);
        setSelectionModel(null);
        setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);
        getRowContainer().getStyle().setCursor(Cursor.DEFAULT);
    }

    @Override
    public void setSelectionModel(final SelectionModel<? super T> selectionModel) {
        setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
        // We need to set this to prevent default keyboard behaviour.
        setKeyboardSelectionHandler(event -> {
        });
        getRowContainer().getStyle().setCursor(Cursor.POINTER);

        super.setSelectionModel(selectionModel);
    }

    @Override
    public void setSelectionModel(final SelectionModel<? super T> selectionModel,
                                  final Handler<T> selectionEventManager) {
        setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
        // We need to set this to prevent default keyboard behaviour.
        setKeyboardSelectionHandler(event -> {
        });
        getRowContainer().getStyle().setCursor(Cursor.POINTER);

        super.setSelectionModel(selectionModel, selectionEventManager);
    }

    @ImportedWithPrefix("gwt-CellTable")
    public interface DefaultStyle extends Style {

        String DEFAULT_CSS = "stroom/data/table/client/DefaultCellTable.css";
    }

    public interface DefaultResources extends Resources {

        @Override
        @Source(DefaultStyle.DEFAULT_CSS)
        DefaultStyle cellTableStyle();
    }
}
