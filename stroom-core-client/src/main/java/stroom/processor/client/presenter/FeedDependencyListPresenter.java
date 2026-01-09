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

package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.shared.StreamTypeNames;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.processor.shared.FeedDependency;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FeedDependencyListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDirtyHandlers {

    private final MyDataGrid<FeedDependency> dataGrid;
    private final MultiSelectionModelImpl<FeedDependency> selectionModel;
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final List<FeedDependency> feedDependencies = new ArrayList<>();
    private final Provider<EditFeedDependencyPresenter> newFeedDependencyPresenter;

    private boolean readOnly = false;

    @Inject
    public FeedDependencyListPresenter(final EventBus eventBus,
                                       final PagerView view,
                                       final Provider<EditFeedDependencyPresenter> newFeedDependencyPresenter) {
        super(eventBus, view);

        view.asWidget().addStyleName("form-control-background form-control-border");
        dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        this.newFeedDependencyPresenter = newFeedDependencyPresenter;

        addButton = view.addButton(SvgPresets.NEW_ITEM);
        editButton = view.addButton(SvgPresets.EDIT);
        removeButton = view.addButton(SvgPresets.REMOVE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit(selectionModel.getSelected());
            }
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAdd(event);
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onEdit(selectionModel.getSelected());
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onRemove();
            }
        }));
    }

    public void focus() {
        addButton.focus();
    }

    public void setFeedDependencies(final List<FeedDependency> list) {
        this.feedDependencies.clear();
        if (list != null) {
            this.feedDependencies.addAll(list);
        }
        refresh();
    }

    public List<FeedDependency> getFeedDependencies() {
        return new ArrayList<>(feedDependencies);
    }

    //    public void show(final List<FeedDependency> list,
//                     final Consumer<List<FeedDependency>> consumer) {
//        this.feedDependencies.clear();
//        if (list != null) {
//            this.feedDependencies.addAll(list);
//        }
//        refresh();
//
//        // Show the feed dependencies dialog.
//        final PopupSize popupSize = PopupSize.resizable(800, 600);
//        ShowPopupEvent.builder(this)
//                .popupType(PopupType.OK_CANCEL_DIALOG)
//                .popupSize(popupSize)
//                .caption("Set Feed Dependencies")
//                .modal(true)
//                .onShow(e -> addButton.focus())
//                .onHideRequest(e -> {
//                    if (e.isOk()) {
//                        consumer.accept(new ArrayList<>(this.feedDependencies));
//                    } else {
//                        consumer.accept(list);
//                    }
//                    e.hide();
//                })
//                .fire();
//    }

    private void addColumns() {
        addFeedColumn();
        addStreamTypeColumn();
        addEndColumn();
    }

    void addFeedColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.feedRefColumnBuilder(FeedDependency::getFeedName,
                                getEventBus())
                        .build(),
                "Feed",
                ColumnSizeConstants.BIG_COL);
    }

    void addStreamTypeColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder(FeedDependency::getStreamType,
                                getEventBus())
                        .build(),
                "Type",
                200);
    }

    private void addEndColumn() {
        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    private void onAdd(final ClickEvent event) {
        final FeedDependency feedDependency = new FeedDependency(UUID.randomUUID().toString(),
                null,
                StreamTypeNames.REFERENCE);
        showEditor(feedDependency, true);
    }

    private void onEdit(final FeedDependency feedDependency) {
        if (feedDependency != null) {
            showEditor(feedDependency, false);
        }
    }

    private void showEditor(final FeedDependency feedDependency,
                            final boolean isNew) {
        if (feedDependency != null) {
            final EditFeedDependencyPresenter editor = newFeedDependencyPresenter.get();
            final HidePopupRequestEvent.Handler handler = e -> {
                if (e.isOk()) {
                    final FeedDependency updated = editor.write();

                    if (updated.getFeedName() == null) {
                        AlertEvent.fireError(FeedDependencyListPresenter.this,
                                "You must specify a feed to use.",
                                e::reset);
                    } else if (updated.getStreamType() == null) {
                        AlertEvent.fireError(FeedDependencyListPresenter.this,
                                "You must specify a stream type to use.", e::reset);
                    } else {
                        final int index = feedDependencies.indexOf(updated);
                        if (index == -1) {
                            feedDependencies.add(updated);
                        } else {
                            feedDependencies.remove(index);
                            feedDependencies.add(index, updated);
                        }

                        setDirty(isNew || editor.isDirty());
                        refresh();
                        e.hide();
                    }
                } else {
                    e.hide();
                }
            };

            final PopupSize popupSize = PopupSize.resizableX();
            ShowPopupEvent.builder(editor)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption(isNew
                            ? "New Feed Dependency"
                            : "Edit Feed Dependency")
                    .onShow(e -> {
                        editor.read(feedDependency);
                        editor.focus();
                    })
                    .onHideRequest(handler)
                    .fire();
        }
    }

    private void onRemove() {
        final FeedDependency selected = selectionModel.getSelected();
        if (selected != null) {
            feedDependencies.remove(selected);
            setDirty(true);
            refresh();
        }
    }

    private void refresh() {
        setData(feedDependencies);
    }

    private void setData(final List<FeedDependency> feedDependencies) {
        dataGrid.setRowData(0, feedDependencies);
        dataGrid.setRowCount(feedDependencies.size());
        enableButtons();
    }

    protected void enableButtons() {
        addButton.setEnabled(!readOnly);

        final FeedDependency selected = selectionModel.getSelected();

        editButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && selected != null);

        if (readOnly) {
            addButton.setTitle("New feed dependency disabled as filter is read only");
            editButton.setTitle("Edit feed dependency disabled as filter is read only");
            removeButton.setTitle("Remove feed dependency disabled as filter is read only");
        } else {
            addButton.setTitle("New Reference");
            editButton.setTitle("Edit Reference");
            removeButton.setTitle("Remove Reference");
        }
    }

    protected void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
