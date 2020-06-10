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
import stroom.data.client.presenter.ColumnSizeConstants;
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
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DataRetentionImpactPresenter
        extends MyPresenterWidget<DataGridView<DataRetentionImpactRow>> {
//        implements ColumnSortEvent.Handler {

    private static final DataRetentionRulesResource RETENTION_RULES_RESOURCE = GWT.create(DataRetentionRulesResource.class);
    private static final NumberFormat COMMA_INTEGER_FORMAT = NumberFormat.getFormat("#,##0");
    private static final String BTN_TITLE_RUN_QUERY = "Run Query";
    private static final String BTN_TITLE_SET_FILTER = "Set Filter";
    private static final String BTN_TITLE_FLAT_TABLE = "View Flat Results";
    private static final String BTN_TITLE_NESTED_TABLE = "View Nested Results";

    private final ListDataProvider<DataRetentionImpactRow> dataProvider = new ListDataProvider<>();
    private final RestFactory restFactory;

    private DataRetentionRules dataRetentionRules;
    private List<DataRetentionDeleteSummary> sourceData;
    private FindDataRetentionImpactCriteria criteria = new FindDataRetentionImpactCriteria();
    private DataRetentionImpactTreeAction treeAction = new DataRetentionImpactTreeAction();

    private ButtonView runButton;
    private ButtonView filterButton;
    private ButtonView flatViewButton;
    private ButtonView nestedViewButton;

    private boolean isTableNested = true;
    private List<Column<DataRetentionImpactRow, ?>> columns = new ArrayList<>();

    @Inject
    public DataRetentionImpactPresenter(final EventBus eventBus,
                                        final RestFactory restFactory,
                                        final NodeCache nodeCache) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.restFactory = restFactory;

        runButton = getView().addButton(SvgPresets.RUN.with(BTN_TITLE_RUN_QUERY, true));
        filterButton = getView().addButton(SvgPresets.FILTER.with(BTN_TITLE_SET_FILTER, true));
        nestedViewButton = getView().addButton(SvgPresets.TABLE_NESTED.with(BTN_TITLE_NESTED_TABLE, false));
        flatViewButton = getView().addButton(SvgPresets.TABLE.with(BTN_TITLE_FLAT_TABLE, true));

        initColumns();

        dataProvider.addDataDisplay(getView().getDataDisplay());
        dataProvider.setListUpdater(this::refreshSourceData);
    }

    private void refreshSourceData(final Range range) {
        // Get the summary data from the rest service, this could
        // take a looooong time
        // Need to assign it to a variable for the generics typing
        final Rest<DataRetentionDeleteSummaryResponse> rest = restFactory.create();
        rest
                .onSuccess(dataRetentionDeleteSummary -> {
                    this.sourceData = dataRetentionDeleteSummary.getValues();
                    // Changed data so clear out the expander states
                    treeAction.reset();
                    refreshVisibleData();
                })
                .onFailure(throwable ->
                        AlertEvent.fireErrorFromException(this, throwable, null))
                .call(RETENTION_RULES_RESOURCE)
                .getRetentionDeletionSummary(dataRetentionRules);
    }

    private void refreshVisibleData() {
        // Rebuild the rows from the source data, e.g. when sorting has changed
        // or it is toggled from nest/flat
        final List<DataRetentionImpactRow> rows = Optional.ofNullable(this.sourceData)
                .map(summaries -> {
                            if (isTableNested) {
                                return DataRetentionImpactRow.buildNestedTable(
                                        dataRetentionRules.getRules(),
                                        summaries,
                                        treeAction,
                                        criteria);
                            } else {
                                return DataRetentionImpactRow.buildFlatTable(
                                        dataRetentionRules.getRules(),
                                        summaries,
                                        criteria);
                            }
                        }
                )
                .orElse(Collections.emptyList());
        dataProvider.setCompleteList(rows);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(runButton.addClickHandler(event -> {
            // Get the user's rules without our default one
            refreshSourceData(new Range(0, Integer.MAX_VALUE));
        }));

        registerHandler(nestedViewButton.addClickHandler(event -> {
            // Get the user's rules without our default one
            isTableNested = true;
            nestedViewButton.setEnabled(false);
            flatViewButton.setEnabled(true);
            refreshVisibleData();
        }));

        registerHandler(flatViewButton.addClickHandler(event -> {
            // Get the user's rules without our default one
            isTableNested = false;
            nestedViewButton.setEnabled(true);
            flatViewButton.setEnabled(false);
            refreshVisibleData();
        }));
    }

    public void setDataRetentionRules(final DataRetentionRules dataRetentionRules) {
        this.dataRetentionRules = dataRetentionRules;
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
                }
            }
            return countCellText.toSafeHtml();
        } else {
            return null;
        }
    }

    private void initColumns() {

        DataGridUtil.addExpanderColumn(
                getView(),
                DataRetentionImpactRow::getExpander,
                treeAction,
                this::refreshVisibleData,
                36); // Need space for three expander levels

        getView().addResizableColumn(
                        DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getRuleNumber, Object::toString)
                        .rightAligned()
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_RULE_NO, () -> !isTableNested)
                        .build(),
                DataGridUtil.createRightAlignedHeader(DataRetentionImpactRow.FIELD_NAME_RULE_NO),
                ColumnSizeConstants.SMALL_COL);

        getView().addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getRuleName)
                        .build(),
                DataRetentionImpactRow.FIELD_NAME_RULE_NAME,
                200);

        getView().addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getRuleAge)
                        .build(),
                DataRetentionImpactRow.FIELD_NAME_RULE_AGE,
                ColumnSizeConstants.MEDIUM_COL);

        getView().addResizableColumn(
                        DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getMetaType)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_META_TYPE, () -> !isTableNested)
                        .build(),
                DataRetentionImpactRow.FIELD_NAME_META_TYPE,
                ColumnSizeConstants.MEDIUM_COL);

        getView().addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getFeedName)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_FEED_NAME)
                        .build(),
                DataRetentionImpactRow.FIELD_NAME_FEED_NAME,
                ColumnSizeConstants.BIG_COL);

        getView().addResizableColumn(
                DataGridUtil.htmlColumnBuilder(this::getIndentedCountCellText)
                        .rightAligned()
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_DELETE_COUNT)
                        .build(),
                DataGridUtil.createRightAlignedHeader(DataRetentionImpactRow.FIELD_NAME_DELETE_COUNT),
                150);

        DataGridUtil.addEndColumn(getView());

        DataGridUtil.addColumnSortHandler(getView(), criteria, this::refreshVisibleData);
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

//    @Override
//    public void onColumnSort(final ColumnSortEvent event) {
//        // TODO implement sorting for Name and Source
//    }

//    public ButtonView add(final SvgPreset preset) {
//        return getView().addButton(preset);
//    }

}
