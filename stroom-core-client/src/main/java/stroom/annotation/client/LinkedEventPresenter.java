package stroom.annotation.client;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.annotation.client.LinkedEventPresenter.LinkedEventView;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.data.client.presenter.ClassificationWrappedDataPresenter;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Consumer;

public class LinkedEventPresenter extends MyPresenterWidget<LinkedEventView> {
    private final RestFactory restFactory;

    private final ButtonView addEventButton;
    private final ButtonView removeEventButton;

    private final DataGridView<EventId> eventList;
    private final ClassificationWrappedDataPresenter dataPresenter;
    private final AddEventLinkPresenter addEventLinkPresenter;

    private Annotation annotation;

    private List<EventId> currentData;
    private EventId nextSelection;
    private boolean dirty;
    private Consumer<Boolean> consumer;

    @Inject
    public LinkedEventPresenter(final EventBus eventBus,
                                final LinkedEventView view,
                                final RestFactory restFactory,
                                final ClassificationWrappedDataPresenter dataPresenter,
                                final AddEventLinkPresenter addEventLinkPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dataPresenter = dataPresenter;
        this.addEventLinkPresenter = addEventLinkPresenter;

        addEventButton = view.addButton(SvgPresets.ADD);
        addEventButton.setTitle("Add Event");
        removeEventButton = view.addButton(SvgPresets.DELETE);
        removeEventButton.setEnabled(false);

        eventList = new DataGridViewImpl<>(true);
        view.setEventListView(eventList);
        view.setDataView(dataPresenter.getView());

        eventList.addResizableColumn(new Column<EventId, String>(new TextCell()) {
            @Override
            public String getValue(final EventId eventId) {
                return eventId.toString();
            }
        }, "Id", ColumnSizeConstants.SMALL_COL);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(eventList.getSelectionModel().addSelectionHandler(e -> onSelection()));

        registerHandler(addEventButton.addClickHandler(e -> addEventLinkPresenter.show(eventId -> {
            if (eventId != null) {
                dirty = true;

                final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                final Rest<List<EventId>> rest = restFactory.create();
                rest.onSuccess(this::setData)
                        .call(annotationResource)
                        .link(new EventLink(annotation.getId(), eventId));
            }
        })));

        registerHandler(removeEventButton.addClickHandler(e -> {
            final EventId selected = eventList.getSelectionModel().getSelected();
            if (selected != null) {
                dirty = true;

                nextSelection = null;
                if (currentData != null && currentData.size() > 1) {
                    int index = currentData.indexOf(selected);
                    index--;
                    index = Math.max(0, index);
                    nextSelection = currentData.get(index);
                }

                final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                final Rest<List<EventId>> rest = restFactory.create();
                rest.onSuccess(this::setData)
                        .call(annotationResource)
                        .unlink(new EventLink(annotation.getId(), selected));
            }
        }));
    }

    public void edit(final Annotation annotation, final Consumer<Boolean> consumer) {
        this.annotation = annotation;
        this.consumer = consumer;
        dirty = false;

        final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
        final Rest<List<EventId>> rest = restFactory.create();
        rest.onSuccess(this::show).call(annotationResource).getLinkedEvents(annotation.getId());
    }

    private void show(final List<EventId> data) {
        setData(data);
        final PopupSize popupSize = new PopupSize(800, 600, 800, 600, true);
        ShowPopupEvent.fire(this, this, PopupType.CLOSE_DIALOG, popupSize, "Linked Events", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                HidePopupEvent.fire(LinkedEventPresenter.this, LinkedEventPresenter.this);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                consumer.accept(dirty);
            }
        });
    }

    private void setData(final List<EventId> data) {
        this.currentData = data;
        eventList.setRowData(0, data);
        eventList.setRowCount(data.size());

        // Change the selection if we need to.
        if (data.size() > 0) {
            final EventId currentSelection = eventList.getSelectionModel().getSelected();
            if (nextSelection != null && data.contains(nextSelection)) {
                eventList.getSelectionModel().setSelected(nextSelection);
            } else if (currentSelection == null) {
                eventList.getSelectionModel().setSelected(data.get(0));
            } else if (!data.contains(currentSelection)) {
                eventList.getSelectionModel().setSelected(data.get(0));
            }
        } else {
            eventList.getSelectionModel().clear();
        }
        nextSelection = null;

        onSelection();
    }

    private void onSelection() {
        final EventId selected = eventList.getSelectionModel().getSelected();
        if (selected != null) {
            final SourceLocation sourceLocation = new SourceLocation(selected.getStreamId(), null, 1L, selected.getEventId(), null);
            dataPresenter.fetchData(sourceLocation);
        } else {
            dataPresenter.clear();
        }

        removeEventButton.setEnabled(selected != null);
    }

    public boolean isDirty() {
        return dirty;
    }

    public interface LinkedEventView extends View {
        ButtonView addButton(SvgPreset preset);

        void setEventListView(View view);

        void setDataView(View view);
    }
}
