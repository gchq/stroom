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
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.pipeline.shared.SourceLocation;
import stroom.streamstore.client.presenter.ClassificationWrappedDataPresenter;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import javax.inject.Inject;
import java.util.List;

public class LinkedEventPresenter extends MyPresenterWidget<LinkedEventView> {
    private final RestFactory restFactory;

    private final ButtonView addEventButton;
    private final ButtonView removeEventButton;

    private final DataGridView<EventId> eventList;
    private final ClassificationWrappedDataPresenter dataPresenter;
    private final AddEventLinkPresenter addEventLinkPresenter;

    private Annotation annotation;

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
                final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                final Rest<List<EventId>> rest = restFactory.create();
                rest.onSuccess(this::setData).call(annotationResource).link(new EventLink(annotation.getId(), eventId));
            }
        })));

        registerHandler(removeEventButton.addClickHandler(e -> {
            final EventId selected = eventList.getSelectionModel().getSelected();
            if (selected != null) {
                final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                final Rest<List<EventId>> rest = restFactory.create();
                rest.onSuccess(this::setData).call(annotationResource).unlink(new EventLink(annotation.getId(), selected));
            }
        }));
    }

    public void edit(final Annotation annotation) {
        this.annotation = annotation;

        final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
        final Rest<List<EventId>> rest = restFactory.create();
        rest.onSuccess(this::show).call(annotationResource).getLinkedEvents(annotation.getId());
    }

    private void show(final List<EventId> data) {
        setData(data);
        final PopupSize popupSize = new PopupSize(800, 600, 800, 600, true);
        ShowPopupEvent.fire(this, this, PopupType.CLOSE_DIALOG, popupSize, "Linked Events", null);
    }

    private void setData(final List<EventId> data) {
        eventList.getSelectionModel().clear();
        eventList.setRowData(0, data);
        eventList.setRowCount(data.size());
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

    public interface LinkedEventView extends View {
        ButtonView addButton(SvgPreset preset);

        void setEventListView(View view);

        void setDataView(View view);
    }
}
