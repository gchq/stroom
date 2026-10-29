/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.FindTracesWithHistogramCriteria;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.TimeRange;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.component.client.Controller;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The traces in a Plan B document ([TracesList]) found by its quick filter and the pathway and time
/// range its owner gives, a page at a time, with a histogram of them over time.
public class TracesListController extends Controller {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    private static final int HISTOGRAM_BUCKETS = 80;

    private TracesList view;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final UiConfigCache uiConfigCache;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private RestDataProvider<TraceRoot, ResultPage<TraceRoot>> dataProvider;

    private DocRef dataSourceRef;
    private String filter;
    private Pathway pathway;
    private TimeRange timeRange;

    /// Made by GIN; [#attach(TracesList)] gives it its list.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param restFactory                      Finds the traces.
    /// @param dateTimeFormatter                Formats the times shown.
    /// @param uiConfigCache                    Gives the quick filter's help link.
    /// @param quickFilterContextHandlerFactory Makes the quick filter's recent filters.
    @Inject
    public TracesListController(final EventBus eventBus,
                                final RestFactory restFactory,
                                final DateTimeFormatter dateTimeFormatter,
                                final UiConfigCache uiConfigCache,
                                final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.uiConfigCache = uiConfigCache;
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public TracesListController attach(final TracesList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setHistogramDateTimeFormatter(dateTimeFormatter);
        view.addColumns(dateTimeFormatter::format);
        view.setOnFilterChange(this::onFilterChange);
        view.setOnSortChange(this::refresh);

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Traces Quick Filter",
                        FindTraceCriteria.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, view.getTaskMonitor());
        return this;
    }

    /// @param zoomHandler Given the start and end, in milliseconds since the epoch, of a part of
    ///                    the histogram the user zooms into.
    public void setHistogramZoomHandler(final BiConsumer<Long, Long> zoomHandler) {
        view.setOnHistogramZoom(zoomHandler);
    }

    private void onFilterChange(final String text) {
        setFilter(text);
        refresh();
    }

    /// @return The selection.
    public MultiSelectionModelImpl<TraceRoot> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// Finds the traces again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<TraceRoot, ResultPage<TraceRoot>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<TraceRoot>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (dataSourceRef == null) {
                        view.setHistogramData(null);
                        dataConsumer.accept(ResultPage.empty());
                    } else {
                        // The histogram rides back on the page so the bars and the rows are counted
                        // from one read of the store rather than from two requests that can see
                        // different data.
                        final FindTracesWithHistogramCriteria criteria =
                                new FindTracesWithHistogramCriteria(new FindTraceCriteria(
                                        CriteriaUtil.createPageRequest(range),
                                        CriteriaUtil.createSortList(view.getSortList()),
                                        dataSourceRef,
                                        filter,
                                        pathway,
                                        SimpleDuration.ZERO,
                                        timeRange),
                                        HISTOGRAM_BUCKETS);

                        restFactory
                                .create(TRACES_RESOURCE)
                                .method(res -> res.findTracesWithHistogram(criteria))
                                .onSuccess(page -> {
                                    // A rejected filter comes back as an empty page like any
                                    // other, so surface the reason on the filter box. See
                                    // ResultPage.filterError.
                                    view.setFilterError(
                                            NullSafe.get(page.getFilterError(), TokenError::getText));
                                    view.setHistogramData(page.getHistogram());
                                    dataConsumer.accept(page);
                                })
                                .onFailure(error -> {
                                    view.setHistogramData(null);
                                    errorHandler.onError(error);
                                })
                                .owner(TracesListController.this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    }
                }
            };
            view.setDataProvider(dataProvider);

        } else {
            dataProvider.refresh();
        }
    }

    /// @param dataSourceRef The Plan B document whose traces are listed, or `null` for none.
    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
        // Operations and trace ids are particular to one Plan B document, so its recent filters
        // are too.
        view.setQuickFilterContextHandler(dataSourceRef == null
                ? null
                : quickFilterContextHandlerFactory.create(
                        FindTraceCriteria.QUICK_FILTER_CONTEXT.withDataSource(dataSourceRef), this,
                        view.getTaskMonitor()));
    }

    /// @param filter The quick filter's text, used at the next [#refresh()].
    public void setFilter(final String filter) {
        this.filter = filter;
    }

    /// @param pathway The pathway the traces must follow, or `null` for any.
    public void setPathway(final Pathway pathway) {
        this.pathway = pathway;
    }

    /// @param timeRange The time range the traces must start in.
    public void setTimeRange(final TimeRange timeRange) {
        this.timeRange = timeRange;
    }
}
