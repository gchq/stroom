/*
 * Copyright 2020 Crown Copyright
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

package stroom.receive.rules.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.client.ListDataProvider;
import stroom.data.client.CriteriaUtil;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionDeleteSummaryRequest;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.SimpleFieldSelectionListModel;
import stroom.query.client.component.ExpressionEditorController;
import stroom.receive.rules.client.component.DataRetentionImpact.ImpactAction;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// The data retention rules' Impact Summary ([DataRetentionImpact]): asks the server how many
/// streams each of the rules given ([#setDataRetentionRules]) would delete, optionally filtered by
/// feed and type, and shows the answer nested by rule, type and feed, or flat.
public class DataRetentionImpactController extends Controller {

    private static final DataRetentionRulesResource RETENTION_RULES_RESOURCE =
            GWT.create(DataRetentionRulesResource.class);

    private static final List<QueryField> FILTERABLE_FIELDS = new ArrayList<>();

    static {
        FILTERABLE_FIELDS.add(MetaFields.FEED);
        FILTERABLE_FIELDS.add(MetaFields.TYPE);
    }

    private final DataRetentionImpact view;
    private final ListDataProvider<DataRetentionImpactRow> dataProvider = new ListDataProvider<>();
    private final RestFactory restFactory;
    private final Provider<ExpressionEditorController> expressionEditorProvider;

    private final FindDataRetentionImpactCriteria criteria;
    private final DataRetentionImpactTreeAction treeAction = new DataRetentionImpactTreeAction();

    private DataRetentionRules dataRetentionRules = null;
    private List<DataRetentionDeleteSummary> sourceData;
    private boolean isTableNested = true;
    private boolean isQueryRunning = false;
    private String currentQueryId = null;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param restFactory              Runs and stops the query.
    /// @param expressionEditorProvider Makes the dialog that sets the query's filter.
    @Inject
    public DataRetentionImpactController(final EventBus eventBus,
                                         final DataRetentionImpact view,
                                         final RestFactory restFactory,
                                         final Provider<ExpressionEditorController> expressionEditorProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.expressionEditorProvider = expressionEditorProvider;
        initWidget(view);

        view.setOnAction(this::onAction);
        view.setOnNestedChange(this::setTableNested);
        view.setOnSortChange(this::refreshVisibleData);

        updateButtonStates();

        criteria = new FindDataRetentionImpactCriteria();
        criteria.setExpression(ExpressionOperator.builder().build());

        view.addColumns(treeAction, this::refreshVisibleData);

        dataProvider.addDataDisplay(view.getDataDisplay());
        dataProvider.setListUpdater(this::refreshSourceData);
    }

    private void onAction(final ImpactAction action) {
        switch (action) {
            case RUN -> refreshSourceData(new Range(0, Integer.MAX_VALUE));
            case STOP -> cancelQuery();
            case FILTER -> openFilterController();
            case EXPAND_ALL -> onExpandAll();
            case COLLAPSE_ALL -> onCollapseAll();
        }
    }

    private void clearTable() {
        sourceData = null;
        dataProvider.setCompleteList(Collections.emptyList());
        treeAction.reset();
        if (criteria != null && criteria.getSortList() != null) {
            criteria.getSortList().clear();
            view.clearSort();
        } else {
            view.getColumnSortList().clear();
        }
    }

    private void refreshSourceData(final Range range) {
        clearTable();
        isQueryRunning = true;
        updateButtonStates();

        final String queryId = UUID.randomUUID().toString();
        final DataRetentionDeleteSummaryRequest request = new DataRetentionDeleteSummaryRequest(
                queryId, dataRetentionRules, criteria);
        currentQueryId = queryId;

        // Get the summary data from the rest service, this could
        // take a looooong time
        // Need to assign it to a variable for the generics typing
        restFactory
                .create(RETENTION_RULES_RESOURCE)
                .method(res -> res.getRetentionDeletionSummary(request))
                .onSuccess(response -> {
                    // check we are expecting the results
                    if (isQueryRunning && currentQueryId.equals(response.getQueryId())) {
                        this.sourceData = response.getValues() != null
                                ? response.getValues()
                                : Collections.emptyList();
                        // Changed data so clear out the expander states
                        treeAction.reset();
                        isQueryRunning = false;
                        refreshVisibleData();
//                        GWT.log("Query finished (success)");
                    } else {
//                        GWT.log("Query finished (different queryId)");
                        clearTable();
                        isQueryRunning = false;
                        refreshVisibleData();
                    }
                })
                .onFailure(error -> {
                    isQueryRunning = false;
                    updateButtonStates();
                    AlertEvent.fireErrorFromException(this, error.getException(), null);
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void cancelQuery() {
        if (currentQueryId != null) {
            restFactory
                    .create(RETENTION_RULES_RESOURCE)
                    .method(res -> res.cancelQuery(currentQueryId))
                    .onSuccess(success -> {
                        isQueryRunning = false;
                        clearTable();
                        refreshVisibleData();
                        updateButtonStates();
//                        GWT.log("Cancel finished (success)");
                    })
                    .onFailure(error -> {
                        // Have to assume it is still running
                        isQueryRunning = true;
                        updateButtonStates();
                        AlertEvent.fireErrorFromException(this, error.getException(), null);
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void updateButtonStates() {
        view.setButtonStates(isQueryRunning,
                isTableNested,
                !isQueryRunning && isTableNested && treeAction.hasCollapsedRows(),
                !isQueryRunning && isTableNested && treeAction.hasExpandedRows());
    }

    private void refreshVisibleData() {
        CriteriaUtil.setSortList(criteria, view.getColumnSortList());

        // Rebuild the rows from the source data, e.g. when sorting has changed
        // or it is toggled from nest/flat
        final List<DataRetentionImpactRow> rows = Optional.ofNullable(this.sourceData)
                .map(summaries -> {
                    if (isTableNested) {
                        return DataRetentionImpactRow.buildNestedTable(
                                dataRetentionRules.getActiveRules(),
                                summaries,
                                treeAction,
                                criteria);
                    } else {
                        return DataRetentionImpactRow.buildFlatTable(
                                dataRetentionRules.getActiveRules(),
                                summaries,
                                criteria);
                    }
                })
                .orElse(Collections.emptyList());
        dataProvider.setCompleteList(rows);
//        rows.forEach(row -> GWT.log(row.toString()));
        updateButtonStates();
    }

    private void onExpandAll() {
        treeAction.expandAll();
        refreshVisibleData();
    }

    private void onCollapseAll() {
        treeAction.collapseAll();
        refreshVisibleData();
    }

    private void setTableNested(final boolean nested) {
        isTableNested = nested;
        refreshVisibleData();
    }

    private void openFilterController() {
        final ExpressionEditorController expressionEditor = expressionEditorProvider.get();
        expressionEditor.read(criteria.getExpression());
        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        fieldSelectionBoxModel.addItems(FILTERABLE_FIELDS);
        expressionEditor.init(MetaFields.STREAM_STORE_DOC_REF, fieldSelectionBoxModel);

        final PopupSize popupSize = PopupSize.resizable(800, 400);
        ControllerDialog.showOnce(this, expressionEditor)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Query Filter")
                .onShow(e -> expressionEditor.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        criteria.setExpression(expressionEditor.write());
                    }
                    e.hide();
                })
                .fire();
    }

    /// Shows the impact of a set of rules, saved or not, when the query is next run.
    ///
    /// @param dataRetentionRules The rules.
    public void setDataRetentionRules(final DataRetentionRules dataRetentionRules) {
        this.dataRetentionRules = dataRetentionRules;
        // Clear out any existing data ready for user to hit run
        clearTable();
    }

}
