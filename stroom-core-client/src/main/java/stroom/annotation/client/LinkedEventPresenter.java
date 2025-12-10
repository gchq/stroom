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

package stroom.annotation.client;

import stroom.annotation.client.LinkedEventPresenter.LinkedEventView;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.LinkEvents;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UnlinkEvents;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.DataPresenter;
import stroom.data.client.presenter.DisplayMode;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

public class LinkedEventPresenter
        extends DocumentEditPresenter<LinkedEventView, Annotation> {

    private final MyDataGrid<EventId> dataGrid;
    private final MultiSelectionModelImpl<EventId> selectionModel;

    private final AnnotationResourceClient annotationResourceClient;

    private final ButtonView addEventButton;
    private final ButtonView removeEventButton;

    private final DataPresenter dataPresenter;
    private final AddEventLinkPresenter addEventLinkPresenter;

    private DocRef annotationRef;

    private List<EventId> currentData;
    private EventId nextSelection;
    private boolean dirty;
    private AnnotationPresenter parent;

    @Inject
    public LinkedEventPresenter(final EventBus eventBus,
                                final LinkedEventView view,
                                final PagerView pagerView,
                                final AnnotationResourceClient annotationResourceClient,
                                final DataPresenter dataPresenter,
                                final AddEventLinkPresenter addEventLinkPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        this.annotationResourceClient = annotationResourceClient;
        this.dataPresenter = dataPresenter;
        dataPresenter.setNavigationControlsVisible(false);
        // It is not in its own dialog but is part of this one and this will determine how the
        // source view is opened if the user clicks that
        dataPresenter.setDisplayMode(DisplayMode.DIALOG);

        this.addEventLinkPresenter = addEventLinkPresenter;

        addEventButton = pagerView.addButton(SvgPresets.ADD);
        addEventButton.setTitle("Add Event Link");
        addEventButton.setEnabled(false);
        removeEventButton = pagerView.addButton(SvgPresets.DELETE);
        removeEventButton.setTitle("Remove Event Link");
        removeEventButton.setEnabled(false);

        view.setEventListView(pagerView);
        view.setDataView(dataPresenter.getView());

        dataGrid.addAutoResizableColumn(
                DataGridUtil.copyTextColumnBuilder(EventId::toString, getEventBus())
                        .build(),
                "Id",
                ColumnSizeConstants.MEDIUM_COL);
    }

    private void linkEvent(final EventId eventId) {
        annotationResourceClient.change(new SingleAnnotationChangeRequest(annotationRef,
                new LinkEvents(Collections.singletonList(eventId))), this::onChange, this);
    }

    private void unlinkEvent(final EventId eventId) {
        annotationResourceClient.change(new SingleAnnotationChangeRequest(annotationRef,
                new UnlinkEvents(Collections.singletonList(eventId))), this::onChange, this);
    }

    private void onChange(final Boolean success) {
        if (success != null && success) {
            AnnotationChangeEvent.fire(this, annotationRef);
            parent.updateHistory();
        }
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(e -> onSelection()));

        registerHandler(addEventButton.addClickHandler(e -> addEventLinkPresenter.show(eventId -> {
            if (eventId != null) {
                dirty = true;
                linkEvent(eventId);
            }
        })));

        registerHandler(removeEventButton.addClickHandler(e -> {
            final EventId selected = selectionModel.getSelected();
            if (selected != null) {
                dirty = true;

                nextSelection = null;
                if (currentData != null && currentData.size() > 1) {
                    int index = currentData.indexOf(selected);
                    index--;
                    index = Math.max(0, index);
                    nextSelection = currentData.get(index);
                }

                unlinkEvent(selected);
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final Annotation annotation, final boolean readOnly) {
        this.annotationRef = docRef;
        dirty = false;
        annotationResourceClient.getLinkedEvents(docRef, this::setData, this);
        enableButtons();
    }

    @Override
    protected Annotation onWrite(final Annotation document) {
        return null;
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

    public boolean isDirty() {
        return dirty;
    }

    public void setParent(final AnnotationPresenter parent) {
        this.parent = parent;
    }

    private void enableButtons() {
        addEventButton.setEnabled(!isReadOnly());
        removeEventButton.setEnabled(!isReadOnly() && selectionModel.getSelected() != null);
    }

    public interface LinkedEventView extends View {

        void setEventListView(View view);

        void setDataView(View view);
    }
}
