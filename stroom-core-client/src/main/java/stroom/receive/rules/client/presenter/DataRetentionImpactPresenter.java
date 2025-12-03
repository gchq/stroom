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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.cell.expander.client.ExpanderCell;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionDeleteSummaryRequest;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.ToggleButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DataRetentionImpactPresenter
        extends MyPresenterWidget<PagerView> {

    private static final DataRetentionRulesResource RETENTION_RULES_RESOURCE =
            GWT.create(DataRetentionRulesResource.class);
    private static final NumberFormat COMMA_INTEGER_FORMAT = NumberFormat.getFormat("#,##0");

    private static final String BTN_TITLE_RUN_QUERY = "Run Query";
    private static final String BTN_TITLE_STOP_QUERY = "Abort Query";
    private static final String BTN_TITLE_SET_FILTER = "Set Query Filter";
    private static final String BTN_TITLE_FLAT_TABLE = "View Flat Results";
    private static final String BTN_TITLE_NESTED_TABLE = "View Nested Results";
    private static final String BTN_TITLE_EXPAND_ALL = "Expand all";
    private static final String BTN_TITLE_COLLAPSE_ALL = "Collapse all";

    private static final List<QueryField> FILTERABLE_FIELDS = new ArrayList<>();

    static {
        FILTERABLE_FIELDS.add(MetaFields.FEED);
        FILTERABLE_FIELDS.add(MetaFields.TYPE);
    }

    private final MyDataGrid<DataRetentionImpactRow> dataGrid;
    private final MultiSelectionModelImpl<DataRetentionImpactRow> selectionModel;
    private final ListDataProvider<DataRetentionImpactRow> dataProvider = new ListDataProvider<>();
    private final RestFactory restFactory;
    private final Provider<EditExpressionPresenter> editExpressionPresenterProvider;

    private final ButtonView runButton;
    private final ButtonView stopButton;
    private final ButtonView filterButton;
    private final ButtonView expandAllButton;
    private final ButtonView collapseAllButton;
    private final ToggleButtonView flatNestedToggleButton;

    private final FindDataRetentionImpactCriteria criteria;
    private final DataRetentionImpactTreeAction treeAction = new DataRetentionImpactTreeAction();

    private DataRetentionRules dataRetentionRules = null;
    private List<DataRetentionDeleteSummary> sourceData;
    private boolean isTableNested = true;
    private boolean isQueryRunning = false;
    private String currentQueryId = null;

    @Inject
    public DataRetentionImpactPresenter(final EventBus eventBus,
                                        final PagerView view,
                                        final RestFactory restFactory,
                                        final Provider<EditExpressionPresenter> editExpressionPresenterProvider) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        this.editExpressionPresenterProvider = editExpressionPresenterProvider;

        runButton = view.addButton(SvgPresets.RUN.title(BTN_TITLE_RUN_QUERY));
        stopButton = view.addButton(SvgPresets.STOP.title(BTN_TITLE_STOP_QUERY));
        filterButton = view.addButton(SvgPresets.FILTER.title(BTN_TITLE_SET_FILTER));
        flatNestedToggleButton = view.addToggleButton(
                SvgPresets.TABLE.title(BTN_TITLE_FLAT_TABLE),
                SvgPresets.TABLE_NESTED.title(BTN_TITLE_NESTED_TABLE));
        expandAllButton = view.addButton(SvgPresets.EXPAND_ALL.title(BTN_TITLE_EXPAND_ALL));
        collapseAllButton = view.addButton(SvgPresets.COLLAPSE_ALL.title(BTN_TITLE_COLLAPSE_ALL));

        updateButtonStates();

        criteria = new FindDataRetentionImpactCriteria();
        criteria.setExpression(ExpressionOperator.builder().build());

        initColumns();

        dataProvider.addDataDisplay(dataGrid);
        dataProvider.setListUpdater(this::refreshSourceData);
    }

    private void clearTable() {
        sourceData = null;
        dataProvider.setCompleteList(Collections.emptyList());
        treeAction.reset();
        dataGrid.clearColumnSortList();
        if (criteria != null && criteria.getSortList() != null) {
            criteria.getSortList().clear();
            dataGrid.redrawHeaders();
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
                .taskMonitorFactory(this)
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
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void updateButtonStates() {
        runButton.setEnabled(!isQueryRunning);
        stopButton.setEnabled(isQueryRunning);
        filterButton.setEnabled(!isQueryRunning);
        flatNestedToggleButton.setState(isTableNested);

        expandAllButton.setEnabled(!isQueryRunning
                                   && isTableNested
                                   && treeAction.hasCollapsedRows());
        collapseAllButton.setEnabled(!isQueryRunning
                                     && isTableNested
                                     && treeAction.hasExpandedRows());
    }

    private void refreshVisibleData() {
        CriteriaUtil.setSortList(criteria, dataGrid.getColumnSortList());

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

    @Override
    protected void onBind() {
        super.onBind();

        // Get the user's rules without our default one
        registerHandler(runButton.addClickHandler(event ->
                refreshSourceData(new Range(0, Integer.MAX_VALUE))));

        // Get the user's rules without our default one
        registerHandler(stopButton.addClickHandler(event ->
                cancelQuery()));

        registerHandler(filterButton.addClickHandler(event ->
                openFilterPresenter()));

        registerHandler(flatNestedToggleButton.addClickHandler(e -> {
            // Get the user's rules without our default one
            isTableNested = flatNestedToggleButton.getState();
            refreshVisibleData();
        }));
        registerHandler(expandAllButton.addClickHandler(event -> {
            treeAction.expandAll();
            refreshVisibleData();
        }));
        registerHandler(collapseAllButton.addClickHandler(event -> {
            treeAction.collapseAll();
            refreshVisibleData();
        }));
        registerHandler(dataGrid.addColumnSortHandler(event -> refreshVisibleData()));
    }

    private void openFilterPresenter() {
        final EditExpressionPresenter editExpressionPresenter = editExpressionPresenterProvider.get();
        editExpressionPresenter.read(criteria.getExpression());
        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        fieldSelectionBoxModel.addItems(FILTERABLE_FIELDS);
        editExpressionPresenter.init(restFactory, MetaFields.STREAM_STORE_DOC_REF, fieldSelectionBoxModel);

        final PopupSize popupSize = PopupSize.resizable(800, 400);
        ShowPopupEvent.builder(editExpressionPresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Query Filter")
                .onShow(e -> editExpressionPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        criteria.setExpression(editExpressionPresenter.write());
                    }
                    e.hide();
                })
                .fire();
    }

    public void setDataRetentionRules(final DataRetentionRules dataRetentionRules) {
        this.dataRetentionRules = dataRetentionRules;
        // Clear out any existing data ready for user to hit run
        clearTable();
    }

    private SafeHtml getIndentedCountCellText(final DataRetentionImpactRow row) {
        if (row != null) {
            final SafeHtmlBuilder countCellText = new SafeHtmlBuilder();
            final String countStr = COMMA_INTEGER_FORMAT.format(row.getCount());
            countCellText.appendEscaped(countStr);

            if (row.getExpander() != null) {
                final String singleIndent = "&ensp;&ensp;";

                switch (row.getExpander().getDepth()) {
                    case 0:
                        countCellText.appendHtmlConstant(singleIndent);
                        countCellText.appendHtmlConstant(singleIndent);
                        break;
                    case 1:
                        countCellText.appendHtmlConstant(singleIndent);
                        break;
                    default:
                        break;  // no indent for the detail level
                }
            }
            return countCellText.toSafeHtml();
        } else {
            return null;
        }
    }

    private void initColumns() {

        DataGridUtil.addExpanderColumn(
                dataGrid,
                DataRetentionImpactRow::getExpander,
                treeAction,
                this::refreshVisibleData,
                ExpanderCell.getColumnWidth(3)); // Need space for three expander levels

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((DataRetentionImpactRow row) ->
                                NullSafe.toString(row.getRuleNumber()))
                        .rightAligned()
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_RULE_NO)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_RULE_NO)
                        .rightAligned()
                        .withToolTip("The lower the rule number, the higher priority when matching streams. " +
                                     "A stream's retention will be governed by the matching rule with the " +
                                     "highest priority.")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getRuleName)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_RULE_NAME)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_RULE_NAME)
                        .withToolTip("The name of the rule.")
                        .build(),
                200);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getRuleAgeStr)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_RULE_AGE)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_RULE_AGE)
                        .withToolTip("The retention age of this rule.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getType)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_TYPE)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_TYPE)
                        .withToolTip("The stream type.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getFeed)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_FEED)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_FEED)
                        .withToolTip("The feed name.")
                        .build(),
                ColumnSizeConstants.BIG_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(this::getIndentedCountCellText)
                        .rightAligned()
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_DELETE_COUNT)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_DELETE_COUNT)
                        .rightAligned()
                        .withToolTip("The number of streams that would be deleted by this rule.")
                        .build(),
                150);

        DataGridUtil.addEndColumn(dataGrid);
    }

    public ButtonView addButton(final Preset preset) {
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
        return selectionModel.getSelected();
    }

}
