package stroom.pathways.client.presenter;

import stroom.data.grid.client.DefaultResources;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pathways.client.presenter.TracesPresenter.TracesView;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class TracesPresenter extends MyPresenterWidget<TracesView> implements TabData {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    private final TracesListPresenter listPresenter;
    private final TraceOverviewWidget traceOverviewWidget;
    private final RestFactory restFactory;
    private DocRef dataSourceRef;

    @Inject
    public TracesPresenter(final EventBus eventBus,
                           final TracesView view,
                           final TracesListPresenter listPresenter,
                           final DefaultResources resources,
                           final RestFactory restFactory) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.restFactory = restFactory;
        traceOverviewWidget = new TraceOverviewWidget(resources);
        view.setTopWidget(listPresenter.getView());
        view.setBottomWidget(traceOverviewWidget);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(e -> {
            final TraceRoot traceRoot = listPresenter.getSelectionModel().getSelected();
            final GetTraceRequest request = new GetTraceRequest(
                    dataSourceRef,
                    traceRoot.getTraceId(),
                    SimpleDuration.ZERO);
            restFactory
                    .create(TRACES_RESOURCE)
                    .method(res -> res.findTrace(request))
                    .onSuccess(traceOverviewWidget::setTrace)
                    .taskMonitorFactory(listPresenter.getView())
                    .exec();
        }));
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DOCUMENT_TRACES;
    }

    @Override
    public String getLabel() {
        return "Traces";
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getType() {
        return "Traces";
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
        listPresenter.setDataSourceRef(dataSourceRef);
    }

    public void setFilter(final String filter) {
        listPresenter.setFilter(filter);
    }

    public void setPathway(final Pathway pathway) {
        listPresenter.setPathway(pathway);
    }

    public void refresh() {
        listPresenter.refresh();
    }

    public interface TracesView extends View {

        void setLabel(String label);

        void setTopWidget(View view);

        void setBottomWidget(Widget view);
    }
}
