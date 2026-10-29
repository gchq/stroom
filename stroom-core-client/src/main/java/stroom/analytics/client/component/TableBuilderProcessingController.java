/*
 * Copyright 2023 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.AnalyticTrackerData;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.analytics.shared.TableBuilderAnalyticTrackerData;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.node.client.NodeClient;
import stroom.preferences.client.DateTimeFormatter;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A rule's table builder processing settings ([TableBuilderProcessing]): offers the nodes, and
/// fetches and shows the table builder's progress.
public class TableBuilderProcessingController extends Controller {

    private static final AnalyticProcessResource ANALYTIC_PROCESS_RESOURCE =
            GWT.create(AnalyticProcessResource.class);

    private final TableBuilderProcessing view;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;

    private DocRef ruleDocRef;

    /// Made by GIN, with its settings.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The settings.
    /// @param dateTimeFormatter             Formats the progress' times.
    /// @param restFactory                   Fetches the progress.
    /// @param nodeClient                    Lists the nodes.
    /// @param dateTimeBoxControllerProvider Makes the stream create times' controllers.
    @Inject
    public TableBuilderProcessingController(final EventBus eventBus,
                                            final TableBuilderProcessing view,
                                            final DateTimeFormatter dateTimeFormatter,
                                            final RestFactory restFactory,
                                            final NodeClient nodeClient,
                                            final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        this.dateTimeFormatter = dateTimeFormatter;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getMinMetaCreateTimeBox());
        make(dateTimeBoxControllerProvider).attach(view.getMaxMetaCreateTimeBox());
        // Read only as its document is
        watch(Contexts.READ_ONLY, view::setReadOnly);
        view.setOnRefresh(this::refreshTracker);
    }

    /// Shows the table builder processing settings of a rule, and its progress.
    ///
    /// @param ruleDocRef The rule the settings belong to.
    /// @param config     The settings to show.
    public void read(final DocRef ruleDocRef, final TableBuilderAnalyticProcessConfig config) {
        nodeClient.listAllNodes(
                list -> {
                    if (!isDisposed() && list != null && !list.isEmpty()) {
                        view.setNodes(list);
                    }
                },
                throwable -> {
                    if (!isDisposed()) {
                        AlertEvent.fireError(this, "Error", throwable.getMessage(), null);
                    }
                },
                taskMonitor());

        this.ruleDocRef = ruleDocRef;
        view.read(config);
        refreshTracker();
    }

    /// @return The settings shown.
    public TableBuilderAnalyticProcessConfig write() {
        return view.write();
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        view.setOnChange(onChange);
    }

    private void refreshTracker() {
        if (ruleDocRef != null && ruleDocRef.getUuid() != null) {
            restFactory
                    .create(ANALYTIC_PROCESS_RESOURCE)
                    .method(res -> res.getTracker(ruleDocRef.getUuid()))
                    .onSuccess(result -> view.setInfo(getInfo(result)))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }


    private SafeHtml getInfo(final AnalyticTracker tracker) {
        final TableBuilder tb = new TableBuilder();

        if (tracker != null) {
            final AnalyticTrackerData trackerData = tracker.getAnalyticTrackerData();
            if (trackerData instanceof TableBuilderAnalyticTrackerData) {
                final TableBuilderAnalyticTrackerData td =
                        (TableBuilderAnalyticTrackerData) trackerData;

                addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
                tb.row(SafeHtmlUtil.from("Last Stream Count"), SafeHtmlUtil.from(td.getLastStreamCount()));
                tb.row(SafeHtmlUtil.from("Last Stream Id"), SafeHtmlUtil.from(td.getLastStreamId()));
                tb.row(SafeHtmlUtil.from("Last Event Id"), SafeHtmlUtil.from(td.getLastEventId()));
                addRowDateString(tb, "Last Event Time", td.getLastEventTime());
                tb.row(SafeHtmlUtil.from("Total Streams Processed"), SafeHtmlUtil.from(td.getTotalStreamCount()));
                tb.row(SafeHtmlUtil.from("Total Events Processed"), SafeHtmlUtil.from(td.getTotalEventCount()));
                //noinspection SizeReplaceableByIsEmpty
                if (td.getMessage() != null && td.getMessage().length() > 0) {
                    tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));
                }
            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("info-table"));
        return htmlBuilder.toSafeHtml();
    }

    private void addRowDateString(final TableBuilder tb, final String label, final Long ms) {
        if (ms != null) {
            if (ms == 0) {
                tb.row(label, "---");
            } else {
                tb.row(label, dateTimeFormatter.formatWithDuration(ms));
            }
        }
    }
}
