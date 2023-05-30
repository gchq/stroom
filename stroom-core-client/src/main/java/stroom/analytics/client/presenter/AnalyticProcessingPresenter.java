/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.client.presenter.AnalyticProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticProcessorFilterResource;
import stroom.analytics.shared.AnalyticProcessorFilterRow;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.FindAnalyticProcessorFilterCriteria;
import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.data.shared.StreamTypeNames;
import stroom.datasource.api.v2.AbstractField;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.meta.shared.MetaFields;
import stroom.node.client.NodeManager;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Optional;

public class AnalyticProcessingPresenter
        extends DocumentEditPresenter<AnalyticProcessingView, AnalyticRuleDoc>
        implements DirtyUiHandlers {

    private static final AnalyticProcessorFilterResource ANALYTIC_PROCESSOR_FILTER_RESOURCE =
            GWT.create(AnalyticProcessorFilterResource.class);

    private final EditExpressionPresenter editExpressionPresenter;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private AnalyticProcessorFilter loadedFilter;

    private String analyticRuleUuid;

    @Inject
    public AnalyticProcessingPresenter(final EventBus eventBus,
                                       final AnalyticProcessingView view,
                                       final EditExpressionPresenter editExpressionPresenter,
                                       final RestFactory restFactory,
                                       final DateTimeFormatter dateTimeFormatter,
                                       final NodeManager nodeManager) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        view.setExpressionView(editExpressionPresenter.getView());
        view.setUiHandlers(this);

        nodeManager.listAllNodes(
                list -> {
                    if (list != null && list.size() > 0) {
                        view.setNodes(list);
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
        registerHandler(editExpressionPresenter.addDirtyHandler(e -> onDirty(true)));
    }

    private void read(final AnalyticProcessorFilterRow row) {
        loadedFilter = Optional.ofNullable(row)
                .map(AnalyticProcessorFilterRow::getAnalyticProcessorFilter)
                .orElse(null);
        read(
                Optional.ofNullable(loadedFilter)
                        .orElse(AnalyticProcessorFilter
                                .builder()
                                .analyticUuid(analyticRuleUuid)
                                .minMetaCreateTimeMs(System.currentTimeMillis())
                                .expression(ExpressionOperator
                                        .builder()
                                        .addTerm(MetaFields.FIELD_TYPE, Condition.EQUALS, StreamTypeNames.EVENTS)
                                        .build())
                                .build())
        );

        if (row != null) {
            readTracker(row.getAnalyticProcessorFilterTracker());
        }
    }

    private void read(final AnalyticProcessorFilter filter) {
        read(
                filter.isEnabled(),
                filter.getExpression(),
                MetaFields.STREAM_STORE_DOC_REF,
                MetaFields.getAllFields(),
                filter.getMinMetaCreateTimeMs(),
                filter.getMaxMetaCreateTimeMs(),
                filter.getNode());
    }

    private void readTracker(final AnalyticProcessorFilterTracker tracker) {
        final SafeHtml safeHtml = getInfo(tracker);
        getView().setInfo(safeHtml);
    }

    public SafeHtml getInfo(final AnalyticProcessorFilterTracker tracker) {
        final TableBuilder tb = new TableBuilder();

        if (tracker != null) {
            addRowDateString(tb, "Last Poll Time", tracker.getLastPollMs());
            tb.row(SafeHtmlUtil.from("Last Poll Task Count"), SafeHtmlUtil.from(tracker.getLastPollTaskCount()));
            tb.row(SafeHtmlUtil.from("Last Meta Id"), SafeHtmlUtil.from(tracker.getLastMetaId()));
            tb.row(SafeHtmlUtil.from("Last Event Id"), SafeHtmlUtil.from(tracker.getLastEventId()));
            addRowDateString(tb, "Last Event Time", tracker.getLastEventTime());
            tb.row(SafeHtmlUtil.from("Total Streams Processed"), SafeHtmlUtil.from(tracker.getMetaCount()));
            tb.row(SafeHtmlUtil.from("Total Events Processed"), SafeHtmlUtil.from(tracker.getEventCount()));
            tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(tracker.getMessage()));
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private void addRowDateString(final TableBuilder tb, final String label, final Long ms) {
        if (ms != null) {
            tb.row(label, dateTimeFormatter.format(ms) + " (" + ms + ")");
        }
    }

    private void read(final boolean enabled,
                      final ExpressionOperator expression,
                      final DocRef dataSource,
                      final List<AbstractField> fields,
                      final Long minMetaCreateTimeMs,
                      final Long maxMetaCreateTimeMs,
                      final String node) {
        editExpressionPresenter.init(restFactory, dataSource, fields);
        editExpressionPresenter.read(Optional.ofNullable(expression).orElse(ExpressionOperator.builder().build()));

        getView().setEnabled(enabled);
        getView().setMinMetaCreateTimeMs(minMetaCreateTimeMs);
        getView().setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);
        getView().setNode(node);
    }

    private AnalyticProcessorFilter write(final AnalyticProcessorFilter filter) {
        return filter.copy()
                .enabled(getView().isEnabled())
                .expression(editExpressionPresenter.write())
                .minMetaCreateTimeMs(getView().getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(getView().getMaxMetaCreateTimeMs())
                .node(getView().getNode())
                .build();
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc analyticRuleDoc, final boolean readOnly) {
        analyticRuleUuid = analyticRuleDoc.getUuid();
        refresh(analyticRuleUuid);
    }

    private void refresh(final String analyticDocUuid) {
        final FindAnalyticProcessorFilterCriteria criteria = new FindAnalyticProcessorFilterCriteria();
        criteria.setAnalyticDocUuid(analyticDocUuid);
        final Rest<ResultPage<AnalyticProcessorFilterRow>> rest = restFactory.create();
        rest
                .onSuccess(result -> read(result.getFirst()))
                .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                .find(criteria);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc analyticRuleDoc) {
        if (loadedFilter != null) {
            loadedFilter = write(loadedFilter);
            final Rest<AnalyticProcessorFilter> rest = restFactory.create();
            rest
                    .onSuccess(result -> refresh(analyticRuleUuid))
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .update(loadedFilter.getUuid(), loadedFilter);
        } else {
            final AnalyticProcessorFilter newFilter = write(AnalyticProcessorFilter
                    .builder()
                    .analyticUuid(analyticRuleUuid)
                    .build());
            final Rest<AnalyticProcessorFilter> rest = restFactory.create();
            rest
                    .onSuccess(result -> refresh(analyticRuleUuid))
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .create(newFilter);
        }
        return analyticRuleDoc;
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    public String getType() {
        return AnalyticRuleDoc.DOCUMENT_TYPE;
    }

    public interface AnalyticProcessingView extends View, HasUiHandlers<DirtyUiHandlers> {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        void setExpressionView(View view);

        Long getMinMetaCreateTimeMs();

        void setMinMetaCreateTimeMs(Long minMetaCreateTimeMs);

        Long getMaxMetaCreateTimeMs();

        void setMaxMetaCreateTimeMs(Long maxMetaCreateTimeMs);

        void setNodes(final List<String> nodes);

        String getNode();

        void setNode(final String node);

        void setInfo(SafeHtml info);
    }
}
