package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.AnalyticTrackerData;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessConfig;
import stroom.analytics.shared.ScheduledQueryAnalyticTrackerData;
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

public class ScheduledQueryProcessingPresenter
        extends MyPresenterWidget<ScheduledQueryProcessingPresenter.ScheduledQueryProcessingView>
        implements ProcessingStatusUiHandlers, HasDirtyHandlers {

    private static final AnalyticProcessResource ANALYTIC_PROCESSOR_FILTER_RESOURCE =
            GWT.create(AnalyticProcessResource.class);

    private final EntityDropDownPresenter errorFeedPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;

    private DocRef ruleDocRef;

    @Inject
    public ScheduledQueryProcessingPresenter(final EventBus eventBus,
                                             final ScheduledQueryProcessingView view,
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
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
    }

    public void read(final DocRef ruleDocRef,
                     final ScheduledQueryAnalyticProcessConfig scheduledQueryAnalyticProcessConfig) {
        this.ruleDocRef = ruleDocRef;
        errorFeedPresenter.setSelectedEntityReference(scheduledQueryAnalyticProcessConfig.getErrorFeed());
        getView().setEnabled(scheduledQueryAnalyticProcessConfig.isEnabled());
        getView().setNode(scheduledQueryAnalyticProcessConfig.getNode());
        getView().setMinEventTimeMs(scheduledQueryAnalyticProcessConfig.getMinEventTimeMs());
        getView().setMaxEventTimeMs(scheduledQueryAnalyticProcessConfig.getMaxEventTimeMs());
        getView().setTimeToWaitForData(scheduledQueryAnalyticProcessConfig.getTimeToWaitForData());
        getView().setQueryFrequency(scheduledQueryAnalyticProcessConfig.getQueryFrequency());

        refreshTracker();
    }

    public ScheduledQueryAnalyticProcessConfig write() {
        return ScheduledQueryAnalyticProcessConfig
                .builder()
                .enabled(getView().isEnabled())
                .node(getView().getNode())
                .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                .minEventTimeMs(getView().getMinEventTimeMs())
                .maxEventTimeMs(getView().getMaxEventTimeMs())
                .timeToWaitForData(getView().getTimeToWaitForData())
                .queryFrequency(getView().getQueryFrequency())
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
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .getTracker(ruleDocRef.getUuid());
        }
    }

    public SafeHtml getInfo(final AnalyticTracker tracker) {
        final TableBuilder tb = new TableBuilder();

        if (tracker != null) {
            final AnalyticTrackerData trackerData = tracker.getAnalyticTrackerData();
            if (trackerData instanceof ScheduledQueryAnalyticTrackerData) {
                final ScheduledQueryAnalyticTrackerData td =
                        (ScheduledQueryAnalyticTrackerData) trackerData;

                addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
                addRowDateString(tb, "Last Window Start Time", td.getLastWindowStartTimeMs());
                addRowDateString(tb, "Last Window End Time", td.getLastWindowEndTimeMs());
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

    public interface ScheduledQueryProcessingView extends View, HasUiHandlers<ProcessingStatusUiHandlers> {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        void setNodes(List<String> nodes);

        String getNode();

        void setNode(String node);

        void setErrorFeedView(View view);

        Long getMinEventTimeMs();

        void setMinEventTimeMs(Long minEventTimeMs);

        Long getMaxEventTimeMs();

        void setMaxEventTimeMs(Long maxEventTimeMs);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);

        SimpleDuration getQueryFrequency();

        void setQueryFrequency(SimpleDuration queryFrequency);

        void setInfo(SafeHtml info);
    }
}
