/*
 * Copyright 2019 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.client.AnnotationPresenter;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.LinkEvents;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UnlinkEvents;
import stroom.data.client.component.DataViewerController;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.DisplayMode;
import stroom.docref.DocRef;
import stroom.pipeline.shared.SourceLocation;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;

/// An annotation's Linked Events tab ([LinkedEvents]): the events linked to it, which the user can
/// link and unlink, and the data of the one selected.
@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
public class LinkedEventsController extends Controller {

    private final DataGrid<EventId> dataGrid;
    private final MultiSelectionModelImpl<EventId> selectionModel;

    private final AnnotationResourceClient annotationResourceClient;

    private final LinkedEvents view;

    private final DataViewerController dataPresenter;
    private final Provider<AddEventLinkController> addEventLinkProvider;

    private DocRef annotationRef;

    private List<EventId> currentData;
    private EventId nextSelection;
    private AnnotationPresenter parent;
    private boolean readOnly;

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param annotationResourceClient Links, unlinks and lists the events.
    /// @param dataPresenter            The data of the event selected.
    /// @param addEventLinkProvider     Makes the dialog that asks which event to link.
    @Inject
    public LinkedEventsController(final EventBus eventBus,
                                  final LinkedEvents view,
                                  final AnnotationResourceClient annotationResourceClient,
                                  final DataViewerController dataPresenter,
                                  final Provider<AddEventLinkController> addEventLinkProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(dataPresenter);

        dataGrid = view.getDataGrid();
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        this.annotationResourceClient = annotationResourceClient;
        this.dataPresenter = dataPresenter;
        dataPresenter.setNavigationControlsVisible(false);
        // It is not in its own dialog but is part of this one and this will determine how the
        // source view is opened if the user clicks that
        dataPresenter.setDisplayMode(DisplayMode.DIALOG);

        this.addEventLinkProvider = addEventLinkProvider;

        view.setOnAction(action -> {
            switch (action) {
                case ADD -> onAddEvent();
                case REMOVE -> onRemoveEvent();
            }
        });
        view.setData(dataPresenter);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.copyTextColumnBuilder(EventId::toString)
                        .build(),
                "Id",
                ColumnSizeConstants.MEDIUM_COL);

        own(selectionModel.addSelectionHandler(e -> onSelection()));
        watch(Contexts.READ_ONLY, readOnly -> {
            this.readOnly = readOnly;
            enableButtons();
        });
    }

    // Lazily, as it is first used after this is owned
    private TaskMonitorFactory taskMonitor() {
        return () -> {
            final TaskMonitorFactory provided = getProvidedTaskMonitor();
            return provided != null
                    ? provided.createTaskMonitor()
                    : new DefaultTaskMonitorFactory(this).createTaskMonitor();
        };
    }

    private void linkEvent(final EventId eventId) {
        annotationResourceClient.change(new SingleAnnotationChangeRequest(annotationRef,
                new LinkEvents(Collections.singletonList(eventId))), this::onChangeComplete, taskMonitor());
    }

    private void unlinkEvent(final EventId eventId) {
        annotationResourceClient.change(new SingleAnnotationChangeRequest(annotationRef,
                new UnlinkEvents(Collections.singletonList(eventId))), this::onChangeComplete, taskMonitor());
    }

    private void onChangeComplete(final Boolean success) {
        if (success != null && success) {
            AnnotationChangeEvent.fire(this, annotationRef);
            parent.updateHistory();
            refreshData();
        }
    }

    private void onRemoveEvent() {
        final EventId selected = selectionModel.getSelected();
        if (selected != null) {
            nextSelection = null;
            if (currentData != null && currentData.size() > 1) {
                int index = currentData.indexOf(selected);
                index--;
                index = Math.max(0, index);
                nextSelection = currentData.get(index);
            }

            unlinkEvent(selected);
        }
    }

    private void onAddEvent() {
        addEventLinkProvider.get().show(this, eventId -> {
            if (eventId != null) {
                linkEvent(eventId);
            }
        });
    }

    /// Shows an annotation's linked events.
    ///
    /// @param docRef     The annotation.
    /// @param annotation The annotation.
    public void read(final DocRef docRef, final Annotation annotation) {
        dataGrid.setTableName("Annotation '" + docRef.getName() + "' Events");
        this.annotationRef = docRef;
        refreshData();
        enableButtons();
    }

    private void refreshData() {
        annotationResourceClient.getLinkedEvents(annotationRef, this::setData, taskMonitor());
    }

    private void setData(final List<EventId> data) {
        this.currentData = data;
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());

        // Change the selection if we need to.
        if (!data.isEmpty()) {
            final EventId currentSelection = selectionModel.getSelected();
            if (nextSelection != null && data.contains(nextSelection)) {
                selectionModel.setSelected(nextSelection);
            } else if (currentSelection == null) {
                selectionModel.setSelected(data.get(0));
            } else if (!data.contains(currentSelection)) {
                selectionModel.setSelected(data.get(0));
            }
        } else {
            selectionModel.clear();
        }
        nextSelection = null;

        onSelection();
    }

    private void onSelection() {
        final EventId selected = selectionModel.getSelected();
        if (selected != null) {
            final SourceLocation sourceLocation = SourceLocation.builder(selected.getStreamId())
                    .withPartIndex(0L)
                    .withRecordIndex(selected.getEventId() - 1) // EventId obj is one based, segment index is 0 based
                    .build();

            dataPresenter.fetchData(sourceLocation);
        } else {
            dataPresenter.clear();
        }
        enableButtons();
    }

    /// @param parent The annotation's editor, whose history is updated when an event is linked.
    public void setParent(final AnnotationPresenter parent) {
        this.parent = parent;
    }

    private void enableButtons() {
        view.setActionsEnabled(!readOnly, !readOnly && selectionModel.getSelected() != null);
    }
}
