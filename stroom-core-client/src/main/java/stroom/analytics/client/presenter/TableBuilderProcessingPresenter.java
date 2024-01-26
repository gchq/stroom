package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.AnalyticTrackerData;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.analytics.shared.TableBuilderAnalyticTrackerData;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.node.client.NodeManager;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public class TableBuilderProcessingPresenter
        extends MyPresenterWidget<TableBuilderProcessingPresenter.TableBuilderProcessingView>
        implements ProcessingStatusUiHandlers, HasDirtyHandlers {

    private static final AnalyticProcessResource ANALYTIC_PROCESS_RESOURCE =
            GWT.create(AnalyticProcessResource.class);

    private final EntityDropDownPresenter errorFeedPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private DocRef currentErrorFeed;
    private boolean isErrorFeedInitialised = false;

    private DocRef ruleDocRef;

    @Inject
    public TableBuilderProcessingPresenter(final EventBus eventBus,
                                           final TableBuilderProcessingView view,
                                           final EntityDropDownPresenter errorFeedPresenter,
                                           final DateTimeFormatter dateTimeFormatter,
                                           final RestFactory restFactory,
                                           final NodeManager nodeManager) {
        super(eventBus, view);
        this.errorFeedPresenter = errorFeedPresenter;
        this.dateTimeFormatter = dateTimeFormatter;
        this.restFactory = restFactory;

        errorFeedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        errorFeedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);

        getView().setErrorFeedView(errorFeedPresenter.getView());

        nodeManager.listAllNodes(
                list -> {
                    if (list != null && list.size() > 0) {
                        getView().setNodes(list);
                    }
                },
                throwable -> AlertEvent
                        .fireError(this,
                                "Error",
                                throwable.getMessage(),
                                null));
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> {
            final DocRef selectedEntityReference = errorFeedPresenter.getSelectedEntityReference();
            // Don't want to fire dirty event when the entity is first set
            if (isErrorFeedInitialised) {
                if (!Objects.equals(selectedEntityReference, currentErrorFeed)) {
                    currentErrorFeed = selectedEntityReference;
                    onDirty();
                }
            } else {
                isErrorFeedInitialised = true;
            }
        }));
    }

    public void read(final DocRef ruleDocRef,
                     final TableBuilderAnalyticProcessConfig tableBuilderAnalyticProcessConfig) {
        this.ruleDocRef = ruleDocRef;
        this.currentErrorFeed = tableBuilderAnalyticProcessConfig.getErrorFeed();
        errorFeedPresenter.setSelectedEntityReference(currentErrorFeed);

        getView().setEnabled(tableBuilderAnalyticProcessConfig.isEnabled());
        getView().setNode(tableBuilderAnalyticProcessConfig.getNode());
        getView().setMinMetaCreateTimeMs(tableBuilderAnalyticProcessConfig.getMinMetaCreateTimeMs());
        getView().setMaxMetaCreateTimeMs(tableBuilderAnalyticProcessConfig.getMaxMetaCreateTimeMs());
        getView().setTimeToWaitForData(tableBuilderAnalyticProcessConfig.getTimeToWaitForData());
        getView().setDataRetention(tableBuilderAnalyticProcessConfig.getDataRetention());

        refreshTracker();
    }

    public TableBuilderAnalyticProcessConfig write() {
        return TableBuilderAnalyticProcessConfig
                .builder()
                .enabled(getView().isEnabled())
                .node(getView().getNode())
                .errorFeed(currentErrorFeed)
                .minMetaCreateTimeMs(getView().getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(getView().getMaxMetaCreateTimeMs())
                .timeToWaitForData(getView().getTimeToWaitForData())
                .dataRetention(getView().getDataRetention())
                .build();
    }

    @Override
    public void onRefreshProcessingStatus() {
        refreshTracker();
    }

    private void refreshTracker() {
        if (ruleDocRef != null && ruleDocRef.getUuid() != null) {
            final Rest<AnalyticTracker> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        final SafeHtml safeHtml = getInfo(result);
                        getView().setInfo(safeHtml);
                    })
                    .call(ANALYTIC_PROCESS_RESOURCE)
                    .getTracker(ruleDocRef.getUuid());
        }
    }

    public SafeHtml getInfo(final AnalyticTracker tracker) {
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
                if (td.getMessage() != null && td.getMessage().length() > 0) {
                    tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));
                }

            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
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

    @Override
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface TableBuilderProcessingView extends View, HasUiHandlers<ProcessingStatusUiHandlers> {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        void setNodes(List<String> nodes);

        String getNode();

        void setNode(String node);

        void setErrorFeedView(View view);

        Long getMinMetaCreateTimeMs();

        void setMinMetaCreateTimeMs(Long minMetaCreateTimeMs);

        Long getMaxMetaCreateTimeMs();

        void setMaxMetaCreateTimeMs(Long maxMetaCreateTimeMs);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);

        SimpleDuration getDataRetention();

        void setDataRetention(SimpleDuration dataRetention);

        void setInfo(SafeHtml info);
    }
}
