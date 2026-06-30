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

package stroom.pathways.client.presenter;

import stroom.data.grid.client.DefaultResources;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.pathways.client.presenter.TracesListTabPresenter.TracesView;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.shared.PlanBDoc;
import stroom.util.shared.time.SimpleDuration;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class TracesListTabPresenter extends DocPresenter<TracesView, TracesDoc> {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    private final TracesListPresenter listPresenter;
    private final TraceOverviewWidget traceOverviewWidget;
    private final RestFactory restFactory;
    private DocRef dataSourceRef;

    @Inject
    public TracesListTabPresenter(final EventBus eventBus,
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
    protected void onRead(final DocRef docRef, final TracesDoc document, final boolean readOnly) {
        if (docRef != null) {
            setDataSourceRef(docRef);
            refresh();
        }
    }

    @Override
    protected TracesDoc onWrite(final TracesDoc document) {
        return document;
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
