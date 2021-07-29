/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.explorer.client.presenter;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.table.client.MyCellTable;
import stroom.explorer.client.presenter.TypeFilterPresenter.TypeFilterView;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.explorer.shared.DocumentTypes;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.HorizontalLocation;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TypeFilterPresenter extends MyPresenterWidget<TypeFilterView>
        implements HasDataSelectionHandlers<TypeFilterPresenter>,
        DocumentTypeSelectionModel {

    private final Set<String> selected = new HashSet<>();
    private final DefaultPopupUiHandlers popupUiHandlers;
    private List<DocumentType> visibleTypes;

    private static final String SELECT_ALL_OR_NONE_TEXT = "All/none";
    private static final String SELECT_ALL_OR_NONE_ICON = "svgIcon-document svgIcon-document-SelectAllOrNone";
    private static final DocumentType SELECT_ALL_OR_NONE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SYSTEM, SELECT_ALL_OR_NONE_TEXT, SELECT_ALL_OR_NONE_TEXT, SELECT_ALL_OR_NONE_ICON);

    private final CellTable<DocumentType> cellTable;

    private final MySingleSelectionModel<DocumentType> selectionModel = new MySingleSelectionModel<>();
    private int mouseOverRow = -1;

    @Inject
    public TypeFilterPresenter(final EventBus eventBus, final TypeFilterView view) {
        super(eventBus, view);

        popupUiHandlers = new DefaultPopupUiHandlers(this) {
            @Override
            public void onShow() {
                super.onShow();
                selectFirstItem();
            }
        };

        cellTable = new MyCellTable<>(DataGridViewImpl.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

        cellTable.getElement().getStyle().setProperty("minWidth", 50 + "px");
        cellTable.getElement().getStyle().setProperty("maxWidth", 600 + "px");

        cellTable.addColumn(getTickBoxColumn());
        cellTable.setSkipRowHoverCheck(true);

        cellTable.setSelectionModel(selectionModel, new TypeFilterSelectionEventManager());

        view.setWidget(cellTable);
    }

    private boolean isSelectable(final DocumentType item) {
        return true;
    }

    public void escape() {
        hideSelf();
    }

    public void show(final Element element) {
        final PopupPosition popupPosition = new PopupPosition(
                element.getAbsoluteRight() + 5,
                element.getAbsoluteRight() + 5,
                element.getAbsoluteTop() - 5,
                element.getAbsoluteTop() - 5,
                HorizontalLocation.RIGHT,
                VerticalLocation.BELOW);

        ShowPopupEvent.fire(this, this, PopupType.POPUP,
                popupPosition, popupUiHandlers, element);
    }

    private void hideSelf() {
        popupUiHandlers.hide();
    }

    public void execute(final DocumentType documentType) {
        if (documentType != null) {
            toggle(documentType);
            refreshView();
        }
    }

    public void setData(final List<DocumentType> items) {
        cellTable.setRowData(0, items);
        cellTable.setRowCount(items.size());
    }

    public void selectFirstItem() {
        int row = getFirstSelectableRow();
        if (row >= 0) {
            final List<DocumentType> items = cellTable.getVisibleItems();
            final DocumentType item = items.get(row);
            selectRow(row);
        }
    }

    public void focus() {
        int row = getFirstSelectableRow();
        if (row >= 0) {
            selectRow(row);
        }
    }

    private void selectRow(final int row) {
        final List<DocumentType> items = cellTable.getVisibleItems();
        if (row >= 0 && row < items.size()) {
            final DocumentType item = items.get(row);
            selectionModel.setSelected(item, true);
            cellTable.setKeyboardSelectedRow(row, true);
        }
    }

    private int getFirstSelectableRow() {
        return 0;
    }


    public void setDocumentTypes(final DocumentTypes documentTypes) {
        visibleTypes = documentTypes.getVisibleTypes();
        showAll();
        refreshView();
    }

    public Set<String> getIncludedTypes() {
        return selected;
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<TypeFilterPresenter> handler) {
        return getEventBus().addHandlerToSource(DataSelectionEvent.getType(), this, handler);
    }

    private void showAll() {
        for (final DocumentType documentType : visibleTypes) {
            selected.add(documentType.getType());
        }
    }

    private void hideAll() {
        selected.clear();
    }

    private void refreshView() {
        // We want to add in the 'All/none' DocumentType, at the top.
        List<DocumentType> selectableTypes = new ArrayList<>(visibleTypes);
        selectableTypes.add(0, SELECT_ALL_OR_NONE_DOCUMENT_TYPE);

        // To refresh the view we need to set the row data again.
        cellTable.setRowData(0, selectableTypes);
        cellTable.setRowCount(selectableTypes.size());
    }

    private void toggle(final DocumentType type) {
        if (type.equals(SELECT_ALL_OR_NONE_DOCUMENT_TYPE)) {
            if (selected.size() == visibleTypes.size()) {
                hideAll();
            } else {
                showAll();
            }
        } else {
            if (selected.contains(type.getType())) {
                selected.remove(type.getType());
            } else {
                selected.add(type.getType());
            }
        }

        refreshView();
        DataSelectionEvent.fire(
                TypeFilterPresenter.this,
                TypeFilterPresenter.this,
                false);
    }

    @Override
    public TickBoxState getState(final DocumentType type) {
        if (type.equals(SELECT_ALL_OR_NONE_DOCUMENT_TYPE)) {
            if (selected.size() == 0) {
                return TickBoxState.UNTICK;
            } else if (selected.size() == visibleTypes.size()) {
                return TickBoxState.TICK;
            } else {
                return TickBoxState.HALF_TICK;
            }
        } else if (selected.contains(type.getType())) {
            return TickBoxState.TICK;
        }
        return TickBoxState.UNTICK;
    }

    private Column<DocumentType, DocumentType> getTickBoxColumn() {
        return new Column<DocumentType, DocumentType>(new DocumentTypeCell(this)) {
            @Override
            public DocumentType getValue(final DocumentType documentType) {
                return documentType;
            }
        };
    }

    public interface TypeFilterView extends View {

        void setWidget(Widget widget);
    }

    private class TypeFilterSelectionEventManager implements CellPreviewEvent.Handler<DocumentType> {

        @Override
        public void onCellPreview(final CellPreviewEvent<DocumentType> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final String type = nativeEvent.getType();
            if ("keydown".equals(type)) {
                // Stop space affecting the scroll position.
                nativeEvent.preventDefault();

                final List<DocumentType> items = cellTable.getVisibleItems();

                if (items.size() > 0) {
                    final DocumentType selected = selectionModel.getSelectedObject();
                    int originalRow = -1;
                    if (selected != null) {
                        originalRow = items.indexOf(selected);
                    }

                    int row = originalRow;
                    final int keyCode = nativeEvent.getKeyCode();
                    switch (keyCode) {
                        case KeyCodes.KEY_UP:
                            for (int i = row - 1; i >= 0; i--) {
                                final DocumentType item = items.get(i);
                                if (isSelectable(item)) {
                                    row = i;
                                    break;
                                }
                            }
                            break;

                        case KeyCodes.KEY_DOWN:
                            for (int i = row + 1; i < items.size(); i++) {
                                final DocumentType item = items.get(i);
                                if (isSelectable(item)) {
                                    row = i;
                                    break;
                                }
                            }
                            break;

                        case KeyCodes.KEY_ESCAPE:
                            escape();
                            row = -1;
                            break;

                        case KeyCodes.KEY_ENTER:
                        case KeyCodes.KEY_SPACE:
                            execute(selected);
                            row = -1;
                            break;
                    }

                    if (row >= 0) {
                        if (row != originalRow) {
                            selectRow(row);
                        }
                    }
                }

            } else if ("click".equals(type)) {
                final DocumentType item = e.getValue();
                if (isSelectable(item)) {
                    final int row = cellTable.getVisibleItems().indexOf(item);
                    selectRow(row);
                    execute(item);
                }

            } else if ("mousemove".equals(type)) {
                final DocumentType item = e.getValue();
                if (isSelectable(item)) {
                    final int row = cellTable.getVisibleItems().indexOf(item);
                    if (row != mouseOverRow) {
                        selectRow(row);
                        mouseOverRow = row;
                    }
                }
            } else if ("blur".equals(type)) {
                final DocumentType item = e.getValue();
                if (isSelectable(item)) {
                    mouseOverRow = -1;
                }
            }
        }
    }
}
