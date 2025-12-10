/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.data.grid.client.MyDataGrid;
import stroom.data.table.client.MyCellTable;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.explorer.client.presenter.TypeFilterPresenter.TypeFilterView;
import stroom.explorer.shared.DocumentTypes;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.CheckListSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.AbstractHasData;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TypeFilterPresenter extends MyPresenterWidget<TypeFilterView>
        implements HasDataSelectionHandlers<TypeFilterPresenter>,
        DocumentTypeSelectionModel {

    private final Set<String> selected = new HashSet<>();
    private List<DocumentType> visibleTypes;

    private static final String SELECT_ALL_OR_NONE_TEXT = "All / None";
    static final DocumentType SELECT_ALL_OR_NONE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SYSTEM,
            SELECT_ALL_OR_NONE_TEXT,
            SELECT_ALL_OR_NONE_TEXT,
            SvgImage.DOCUMENT_SELECT_ALL_OR_NONE);

    private final CellTable<DocumentType> cellTable;

    private final TypeFilterSelectionEventManager typeFilterSelectionEventManager;
    private Consumer<Boolean> filterStateConsumer = null;

    @Inject
    public TypeFilterPresenter(final EventBus eventBus, final TypeFilterView view) {
        super(eventBus, view);

        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

        cellTable.getElement().getStyle().setProperty("minWidth", 50 + "px");
        cellTable.getElement().getStyle().setProperty("maxWidth", 600 + "px");

        cellTable.addColumn(getTickBoxColumn());
        cellTable.setSkipRowHoverCheck(true);

        final MySingleSelectionModel<DocumentType> selectionModel = new MySingleSelectionModel<>();
        typeFilterSelectionEventManager = new TypeFilterSelectionEventManager(cellTable);
        cellTable.setSelectionModel(selectionModel, typeFilterSelectionEventManager);

        view.setWidget(cellTable);
    }

    public void escape() {
        hideSelf();
    }

    public void show(final Element element,
                     final Consumer<Boolean> filterStateConsumer) {
        this.filterStateConsumer = filterStateConsumer;
        Rect relativeRect = new Rect(element);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.RIGHT);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> selectFirstItem())
                .onHide(event -> {
                    if (filterStateConsumer != null) {
                        filterStateConsumer.accept(hasActiveFilter());
                    }
                })
                .fire();
    }

    private void hideSelf() {
        HidePopupRequestEvent.builder(this)
                .fire();
    }

    public void execute(final DocumentType documentType) {
        if (documentType != null) {
            toggle(documentType);
        }
    }

    public void setData(final List<DocumentType> items) {
        cellTable.setRowData(0, items);
        cellTable.setRowCount(items.size());
    }

    public void selectFirstItem() {
        typeFilterSelectionEventManager.selectFirstItem();
    }

    public void focus() {
        typeFilterSelectionEventManager.selectFirstItem();
    }

    public void setDocumentTypes(final DocumentTypes documentTypes) {
        visibleTypes = documentTypes.getVisibleTypes()
                .stream()
                .sorted(Comparator.comparing(DocumentType::getDisplayType))
                .collect(Collectors.toList());
        selectAll();
        refreshView();
    }

    /**
     * @return A set of types to include, or empty if the type filter is not active
     */
    public Optional<Set<String>> getIncludedTypes() {
        if (visibleTypes != null) {
            final Set<String> visibleTypeNames = visibleTypes.stream()
                    .map(DocumentType::getType)
                    .collect(Collectors.toSet());
            if (selected.containsAll(visibleTypeNames)) {
                // All selected so return null to save the back end pointlessly filtering on them.
                // The visible types are derived from finding the distinct types of all the entities
                // that a user has permission to read, so there is no point in filtering on all visible types.
                return Optional.empty();
            } else {
                return Optional.of(selected);
            }
        } else {
            return Optional.of(selected);
        }
    }

    public boolean hasActiveFilter() {
        return getIncludedTypes().isPresent();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<TypeFilterPresenter> handler) {
        return getEventBus().addHandlerToSource(DataSelectionEvent.getType(), this, handler);
    }

    private void selectAll() {
        for (final DocumentType documentType : visibleTypes) {
            selected.add(documentType.getType());
        }
    }

    private void selectNone() {
        selected.clear();
    }

    private void refreshView() {
        // We want to add in the 'All/none' DocumentType, at the top.
        final List<DocumentType> selectableTypes = new ArrayList<>(visibleTypes);
        selectableTypes.add(0, SELECT_ALL_OR_NONE_DOCUMENT_TYPE);

        // To refresh the view we need to set the row data again.
        cellTable.setRowData(0, selectableTypes);
        cellTable.setRowCount(selectableTypes.size());
    }

    private void toggleSelectAll() {
        if (selected.size() == visibleTypes.size()) {
            selectNone();
        } else {
            selectAll();
        }
    }

    private void toggle(final DocumentType type) {
        if (type.equals(SELECT_ALL_OR_NONE_DOCUMENT_TYPE)) {
            toggleSelectAll();
        } else {
            if (selected.contains(type.getType())) {
                selected.remove(type.getType());
            } else {
                selected.add(type.getType());
            }
        }

        if (filterStateConsumer != null) {
            filterStateConsumer.accept(hasActiveFilter());
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


    // --------------------------------------------------------------------------------


    public interface TypeFilterView extends View {

        void setWidget(Widget widget);
    }


    // --------------------------------------------------------------------------------


    private class TypeFilterSelectionEventManager extends CheckListSelectionEventManager<DocumentType> {

        public TypeFilterSelectionEventManager(final AbstractHasData<DocumentType> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onToggle(final DocumentType item) {
            execute(item);
        }

        @Override
        protected void onClose(final CellPreviewEvent<DocumentType> e) {
            escape();
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<DocumentType> e) {
            toggleSelectAll();
            refreshView();
            DataSelectionEvent.fire(
                    TypeFilterPresenter.this,
                    TypeFilterPresenter.this,
                    false);
        }
    }
}
