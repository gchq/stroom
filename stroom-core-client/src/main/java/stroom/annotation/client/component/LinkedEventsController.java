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

import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.client.event.AnnotationChangeEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.LinkEvents;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UnlinkEvents;
import stroom.data.client.DisplayMode;
import stroom.data.client.component.DataViewerController;
import stroom.docref.DocRef;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.client.DataGridUtil;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// An annotation's Linked Events tab ([LinkedEvents]): the events linked to it, which the user can
/// link and unlink, and the data of the one selected.
@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
public class LinkedEventsController extends Controller {

    private final DataGrid<EventId> dataGrid;
    private final MultiSelectionModelImpl<EventId> selectionModel;

    private final AnnotationResourceClient annotationResourceClient;

    private final LinkedEvents view;

    private final DataViewerController dataController;
    private final Provider<AddEventLinkController> addEventLinkProvider;

    private DocRef annotationRef;

    private Runnable onChange = () -> {
    };
    private boolean readOnly;

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param annotationResourceClient Links, unlinks and lists the events.
    /// @param dataController           The data of the event selected.
    /// @param addEventLinkProvider     Makes the dialog that asks which event to link.
    @Inject
    public LinkedEventsController(final EventBus eventBus,
                                  final LinkedEvents view,
                                  final AnnotationResourceClient annotationResourceClient,
                                  final DataViewerController dataController,
                                  final Provider<AddEventLinkController> addEventLinkProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        dataGrid = view.getDataGrid();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        // The preview beside it shows the selected event (UI-GUIDE.md, "What a list selects")
        selectionModel.setSelectFirst(true);

        this.annotationResourceClient = annotationResourceClient;
        this.dataController = own(dataController).attach(view.getData());
        dataController.setNavigationControlsVisible(false);
        // It is not in its own dialog but is part of this one and this will determine how the
        // source view is opened if the user clicks that
        dataController.setDisplayMode(DisplayMode.DIALOG);

        this.addEventLinkProvider = addEventLinkProvider;

        view.setOnAction(action -> {
            switch (action) {
                case ADD -> onAddEvent();
                case REMOVE -> onRemoveEvent();
            }
        });

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


    private void linkEvent(final EventId eventId) {
        annotationResourceClient.change(new SingleAnnotationChangeRequest(annotationRef,
                new LinkEvents(Collections.singletonList(eventId))), guard(success -> {
                    if (success != null && success) {
                        // The event added, once shown (UI-GUIDE.md, "What a list selects")
                        selectionModel.selectWhenShown(eventId::equals);
                    }
                    onChangeComplete(success);
                }), taskMonitor());
    }

    private void unlinkEvent(final EventId eventId) {
        annotationResourceClient.change(new SingleAnnotationChangeRequest(annotationRef,
                new UnlinkEvents(Collections.singletonList(eventId))), guard(success -> {
                    if (success != null && success) {
                        // The event in its place once the events are shown again (it was once the
                        // one before)
                        selectionModel.selectNextAfterRemove(List.of(eventId));
                    }
                    onChangeComplete(success);
                }), taskMonitor());
    }

    private void onChangeComplete(final Boolean success) {
        if (success != null && success) {
            AnnotationChangeEvent.fire(this, annotationRef);
            onChange.run();
            refreshData();
        }
    }

    private void onRemoveEvent() {
        final EventId selected = selectionModel.getSelected();
        if (selected != null) {
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
        annotationResourceClient.getLinkedEvents(annotationRef, guard(this::setData), taskMonitor());
    }

    private void setData(final List<EventId> data) {
        // The selection settles on the rows as they are shown: the event selected, the one in place
        // of one removed, else the first
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
        if (selectionModel.getSelected() == null) {
            // Nothing to settle on (e.g. no events): the preview is cleared now, as no selection
            // event will come
            onSelection();
        } else {
            enableButtons();
        }
    }

    private void onSelection() {
        final EventId selected = selectionModel.getSelected();
        if (selected != null) {
            final SourceLocation sourceLocation = SourceLocation.builder(selected.getStreamId())
                    .withPartIndex(0L)
                    .withRecordIndex(selected.getEventId() - 1) // EventId obj is one based, segment index is 0 based
                    .build();

            dataController.fetchData(sourceLocation);
        } else {
            dataController.clear();
        }
        enableButtons();
    }

    /// @param onChange What to run once an event is linked or unlinked, e.g. to show it in the
    ///                 annotation's history.
    public void setOnChange(final Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    private void enableButtons() {
        view.setActionsEnabled(!readOnly, !readOnly && selectionModel.getSelected() != null);
    }
}
