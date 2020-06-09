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
import stroom.util.client.DataGridUtil;
import stroom.util.client.SafeHtmlUtil;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.SafeHtmlHeader;
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

    private static final DataRetentionRulesResource RETENTION_RULES_RESOURCE = GWT.create(DataRetentionRulesResource.class);

    private final ListDataProvider<DataRetentionImpactRow> dataProvider = new ListDataProvider<>();
    private final RestFactory restFactory;

    private DataRetentionRules dataRetentionRules;
    private List<DataRetentionDeleteSummary> sourceData;
    private FindDataRetentionImpactCriteria criteria = new FindDataRetentionImpactCriteria();
    private DataRetentionImpactTreeAction treeAction = new DataRetentionImpactTreeAction();

    @Inject
    public DataRetentionImpactPresenter(final EventBus eventBus,
                                        final RestFactory restFactory,
                                        final NodeCache nodeCache) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.restFactory = restFactory;

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

        final List<DataRetentionImpactRow> rows = Optional.ofNullable(this.sourceData)
                .map(summaries ->
                        DataRetentionImpactRow.buildTree(
                                dataRetentionRules.getRules(),
                                summaries,
                                treeAction,
                                criteria))
                .orElse(Collections.emptyList());

        dataProvider.setCompleteList(rows);
    }

    public void setDataRetentionRules(final DataRetentionRules dataRetentionRules) {
        this.dataRetentionRules = dataRetentionRules;
    }

    private Header<SafeHtml> createRightAlignedHeader(final String name) {
        Header<SafeHtml> header = new SafeHtmlHeader(SafeHtmlUtil.getSafeHtml(name));
        header.setHeaderStyleNames("alignRight");
        return header;
    }

    private SafeHtml getIndentedCountCellText(final DataRetentionImpactRow row) {
        if (row != null) {
            final SafeHtmlBuilder indent = new SafeHtmlBuilder();
            indent.appendEscaped(Integer.toString(row.getCount()));
            for (int i = 0; i < row.getExpander().getDepth(); i++) {
//                indent.appendEscaped("<-");
                indent.appendHtmlConstant("&ensp;&ensp;");
            }
            return indent.toSafeHtml();
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
                        .build(),
                createRightAlignedHeader(DataRetentionImpactRow.FIELD_NAME_RULE_NO),
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
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_META_TYPE)
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
                DataRetentionImpactRow.FIELD_NAME_DELETE_COUNT,
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

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        // TODO implement sorting for Name and Source
    }

}
