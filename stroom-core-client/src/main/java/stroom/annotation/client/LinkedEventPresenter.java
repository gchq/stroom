package stroom.annotation.client;

import stroom.annotation.client.LinkedEventPresenter.LinkedEventView;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.data.client.presenter.ClassificationWrappedDataPresenter;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.DisplayMode;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

public class LinkedEventPresenter extends MyPresenterWidget<LinkedEventView> {

    private final MyDataGrid<EventId> dataGrid;
    private final MultiSelectionModelImpl<EventId> selectionModel;

    private final RestFactory restFactory;

    private final ButtonView addEventButton;
    private final ButtonView removeEventButton;

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
                                final PagerView pagerView,
                                final RestFactory restFactory,
                                final ClassificationWrappedDataPresenter dataPresenter,
                                final AddEventLinkPresenter addEventLinkPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        this.dataPresenter = dataPresenter;
        dataPresenter.setNavigationControlsVisible(false);
        // It is not in its own dialog but is part of this one and this will determine how the
        // source view is opened if the user clicks that
        dataPresenter.setDisplayMode(DisplayMode.DIALOG);

        this.addEventLinkPresenter = addEventLinkPresenter;

        addEventButton = view.addButton(SvgPresets.ADD);
        addEventButton.setTitle("Add Event");
        removeEventButton = view.addButton(SvgPresets.DELETE);
        removeEventButton.setEnabled(false);

        view.setEventListView(pagerView);
        view.setDataView(dataPresenter.getView());

        dataGrid.addResizableColumn(new Column<EventId, String>(new TextCell()) {
            @Override
            public String getValue(final EventId eventId) {
                return eventId.toString();
            }
        }, "Id", ColumnSizeConstants.SMALL_COL);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(e -> onSelection()));

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

        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Linked Events")
                .onShow(e -> addEventButton.focus())
                .onHide(e -> consumer.accept(dirty))
                .fire();
    }

    private void setData(final List<EventId> data) {
        this.currentData = data;
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());

        // Change the selection if we need to.
        if (data.size() > 0) {
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

        removeEventButton.setEnabled(selected != null);
    }

    public boolean isDirty() {
        return dirty;
    }

    public interface LinkedEventView extends View {

        ButtonView addButton(Preset preset);

        void setEventListView(View view);

        void setDataView(View view);
    }
}
