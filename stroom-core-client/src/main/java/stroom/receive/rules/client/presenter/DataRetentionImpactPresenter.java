/*
 * Copyright 2016 Crown Copyright
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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionDeleteSummaryResponse;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeCache;
import stroom.svg.client.SvgPreset;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DataRetentionImpactPresenter
        extends MyPresenterWidget<DataGridView<DataRetentionImpactRow>>
        implements ColumnSortEvent.Handler {

    private static final int TIMER_DELAY_MS = 50;

    private static final DataRetentionRulesResource RETENTION_RULES_RESOURCE = GWT.create(DataRetentionRulesResource.class);

    private final ListDataProvider<DataRetentionImpactRow> dataProvider = new ListDataProvider<>();
    private final RestFactory restFactory;
    private final DataRetentionImpactTreeAction treeAction = new DataRetentionImpactTreeAction();

    private DataRetentionRules dataRetentionRules;
    private List<DataRetentionDeleteSummary> sourceData;

    @Inject
    public DataRetentionImpactPresenter(final EventBus eventBus,
                                        final RestFactory restFactory,
                                        final NodeCache nodeCache) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.restFactory = restFactory;

        initColumns();

        dataProvider.addDataDisplay(getView().getDataDisplay());
        dataProvider.setListUpdater(this::refreshSourceData);

//        dataProvider = new RestDataProvider<List<DataRetentionImpactRow>, DataRetentionDeleteSummaryResponse>(eventBus) {
//            @Override
//            protected void exec(final Consumer<List<DataRetentionImpactRow>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
//                final Rest<DataRetentionDeleteSummaryResponse> rest = restFactory.create();
//                rest
//                        .onSuccess(response -> {
//                            List<DataRetentionImpactRow> rows = response.getValues().stream()
//                                    .map(summary -> {
//                                        return new DataRetentionImpactRow(
//                                                summary.getRuleNumber(),
//                                                summary.getRuleName(),
//                                                summary.getFeedName(),
//                                                summary.getMetaType(),
//                                                summary.getCount());
//                                    })
//                                    .collect(Collectors.toList());
//
//                            dataConsumer.accept(rows);
//                        })
//                        .onFailure(throwableConsumer)
//                        .call(RETENTION_RULES_RESOURCE)
//                        .getRetentionDeletionSummary(dataRetentionRules);
//            }
//        };
    }

    private void refreshSourceData(final Range range) {

        // Need to assign it to a variable for the generics typing
        final Rest<DataRetentionDeleteSummaryResponse> rest = restFactory.create();
        rest
                .onSuccess(dataRetentionDeleteSummary -> {
                    this.sourceData = dataRetentionDeleteSummary.getValues();
//                    final List<DataRetentionImpactRow> rows = dataRetentionDeleteSummary.stream()
//                            .map(summary -> {
//                                // TODO expander
//                                return new DataRetentionImpactRow(
//                                        summary.getRuleNumber(),
//                                        summary.getRuleName(),
//                                        numberToAgeMap.get(summary.getRuleNumber()),
//                                        summary.getFeedName(),
//                                        summary.getMetaType(),
//                                        summary.getCount());
//                            })
//                            .collect(Collectors.toList());
//                    dataProvider.setCompleteList(rows);
                    refreshVisibleData();
                })
                .onFailure(throwable ->
                        AlertEvent.fireErrorFromException(this, throwable, null))
                .call(RETENTION_RULES_RESOURCE)
                .getRetentionDeletionSummary(dataRetentionRules);
    }

    private void refreshVisibleData() {

        final List<DataRetentionImpactRow> rows = Optional.ofNullable(this.sourceData)
                .map(summaries ->
                        DataRetentionImpactRow.buildTree(
                                dataRetentionRules.getRules(),
                                summaries,
                                treeAction))
                .orElse(Collections.emptyList());

        dataProvider.setCompleteList(rows);
    }

//    private String getRuleAge(final int ruleNumber) {
//        final DataRetentionRule rule = numberToRuleMap.get(ruleNumber);
//        if (rule == null) {
//            return null;
//        } else {
//            return rule.getAgeString();
//        }
//    }

    public void setDataRetentionRules(final DataRetentionRules dataRetentionRules) {
        this.dataRetentionRules = dataRetentionRules;
//        this.numberToRuleMap = Optional.ofNullable(dataRetentionRules)
//                .map(DataRetentionRules::getRules)
//                .map(rules ->
//                        rules.stream()
//                                .collect(Collectors.toMap(
//                                        DataRetentionRule::getRuleNumber,
//                                        Function.identity())))
//                .orElse(Collections.emptyMap());

//        this.numberToAgeMap = numberToRuleMap.values().stream()
//                .collect(Collectors.toMap(
//                        DataRetentionRule::getRuleNumber,
//                        DataRetentionRule::getAgeString));

//        this.dataProvider.refresh(true);
    }

    private void initColumns() {
        DataGridUtil.addExpanderColumn(getView(), DataRetentionImpactRow::getExpander, treeAction, this::refreshVisibleData);
        DataGridUtil.addResizableNumericTextColumn(getView(), DataRetentionImpactRow::getRuleNumber, "Rule No.", 60);
        DataGridUtil.addResizableTextColumn(getView(), DataRetentionImpactRow::getRuleName, "Rule Name", 250);
        DataGridUtil.addResizableTextColumn(getView(), DataRetentionImpactRow::getRuleAge, "Rule Age", 100);
        DataGridUtil.addResizableTextColumn(getView(), DataRetentionImpactRow::getFeedName, "Feed Name", 300);
        DataGridUtil.addResizableTextColumn(getView(), DataRetentionImpactRow::getMetaType, "Meta Type", 100);
        DataGridUtil.addResizableNumericTextColumn(getView(), DataRetentionImpactRow::getCount, "Delete Count", 100);
        DataGridUtil.addEndColumn(getView());
    }

    public ButtonView addButton(final SvgPreset preset) {
        return getView().addButton(preset);
    }

    @Override
    protected void onReveal() {
        super.onReveal();
        refresh();
    }

    public void refresh() {
        dataProvider.refresh(true);
    }

    public DataRetentionImpactRow getSelectedItem() {
        return getView().getSelectionModel().getSelected();
    }

    private void showError(final Throwable throwable, final String message) {
        AlertEvent.fireError(
                DataRetentionImpactPresenter.this,
                message + " - " + throwable.getMessage(),
                null,
                null);
    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        // TODO implement sorting for Name and Source
    }

}
