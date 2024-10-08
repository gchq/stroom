/*
 * Copyright 2024 Crown Copyright
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

package stroom.dashboard.client.text;

import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.Column;
import stroom.security.shared.DocumentPermissionNames;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BasicTextSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTextSettingsPresenter.BasicTextSettingsView>
        implements BasicTextSettingsUiHandlers, Focus {

    private final DocSelectionBoxPresenter pipelinePresenter;
    private List<Component> tableList;
    private boolean ignoreTableChange;
    private final List<Column> allColumns = new ArrayList<>();

    @Inject
    public BasicTextSettingsPresenter(final EventBus eventBus, final BasicTextSettingsView view,
                                      final DocSelectionBoxPresenter pipelinePresenter) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;

        pipelinePresenter.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setPipelineView(pipelinePresenter.getView());
        view.setUiHandlers(this);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    private void setTableList(final List<Component> list) {
        ignoreTableChange = true;
        this.tableList = list;
        getView().setTableList(list);
        ignoreTableChange = false;
    }

    @Override
    public void onTableChange() {
        updateFieldNames(getView().getTable());
    }

    private void updateFieldNames(final Component component) {
        if (!ignoreTableChange) {
            allColumns.clear();
            if (component == null) {
                if (tableList != null) {
                    tableList.forEach(c -> addColumnNames(c, allColumns));
                }
            } else {
                addColumnNames(component, allColumns);
            }

            getView().setColumns(allColumns);
        }
    }

    private void addColumnNames(final Component component, final List<Column> allColumns) {
        if (component instanceof TablePresenter) {
            final TablePresenter tablePresenter = (TablePresenter) component;
            final List<Column> columns = tablePresenter.getTableSettings().getColumns();
            if (columns != null && columns.size() > 0) {
                allColumns.addAll(columns);
            }
        }
    }

    private DocRef getPipeline() {
        return pipelinePresenter.getSelectedEntityReference();
    }

    private void setPipeline(final DocRef pipeline) {
        pipelinePresenter.setSelectedEntityReference(pipeline, true);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<Component> list = getComponents().getSortedComponentsByType(
                TablePresenter.TYPE.getId(),
                EmbeddedQueryPresenter.TYPE.getId());
        setTableList(list);

        TextComponentSettings settings = (TextComponentSettings) componentConfig.getSettings();
        final TextComponentSettings.Builder builder = settings.copy();
        setTableId(settings.getTableId());

        updateFieldNames(getView().getTable());

        // Make some best matches for fields.
        builder.streamIdField(getClosestColumn(settings.getStreamIdColumn()));
        builder.partNoField(getClosestColumn(settings.getPartNoColumn()));
        builder.recordNoField(getClosestColumn(settings.getRecordNoColumn()));
        builder.lineFromField(getClosestColumn(settings.getLineFromColumn()));
        builder.colFromField(getClosestColumn(settings.getColFromColumn()));
        builder.lineToField(getClosestColumn(settings.getLineToColumn()));
        builder.colToField(getClosestColumn(settings.getColToColumn()));

        settings = builder.build();

        getView().setStreamIdColumn(settings.getStreamIdColumn());
        getView().setPartNoColumn(settings.getPartNoColumn());
        getView().setRecordNoColumn(settings.getRecordNoColumn());
        getView().setLineFromColumn(settings.getLineFromColumn());
        getView().setColFromColumn(settings.getColFromColumn());
        getView().setLineToColumn(settings.getLineToColumn());
        getView().setColToColumn(settings.getColToColumn());

        setPipeline(settings.getPipeline());
        getView().setShowAsHtml(settings.isShowAsHtml());
        getView().setShowStepping(settings.isShowStepping());
    }

    private Column getClosestColumn(final Column column) {
        if (column == null) {
            return null;
        }
        Column bestMatch = null;
        for (final Column col : allColumns) {
            if (col.getId().equals(column.getId())) {
                bestMatch = col;
                break;
            } else if (bestMatch == null && col.getName().equals(column.getName())) {
                bestMatch = col;
            }
        }

        if (bestMatch == null) {
            return column;
        }

        return bestMatch;
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        ComponentConfig result = super.write(componentConfig);
        final TextComponentSettings oldSettings = (TextComponentSettings) result.getSettings();
        final TextComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private TextComponentSettings writeSettings(final TextComponentSettings settings) {
        return settings
                .copy()
                .tableId(getTableId())
                .streamIdField(getView().getStreamIdColumn())
                .partNoField(getView().getPartNoColumn())
                .recordNoField(getView().getRecordNoColumn())
                .lineFromField(getView().getLineFromColumn())
                .colFromField(getView().getColFromColumn())
                .lineToField(getView().getLineToColumn())
                .colToField(getView().getColToColumn())
                .pipeline(getPipeline())
                .showAsHtml(getView().isShowAsHtml())
                .showStepping(getView().isShowStepping())
                .build();
    }

    private String getTableId() {
        final Component table = getView().getTable();
        if (table == null) {
            return null;
        }

        return table.getId();
    }

    private void setTableId(final String tableId) {
        getView().setTable(getComponents().get(tableId));
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final TextComponentSettings oldSettings = (TextComponentSettings) componentConfig.getSettings();
        final TextComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getTableId(), newSettings.getTableId()) &&
                Column.equalsId(oldSettings.getStreamIdColumn(), newSettings.getStreamIdColumn()) &&
                Column.equalsId(oldSettings.getPartNoColumn(), newSettings.getPartNoColumn()) &&
                Column.equalsId(oldSettings.getRecordNoColumn(), newSettings.getRecordNoColumn()) &&
                Column.equalsId(oldSettings.getLineFromColumn(), newSettings.getLineFromColumn()) &&
                Column.equalsId(oldSettings.getColFromColumn(), newSettings.getColFromColumn()) &&
                Column.equalsId(oldSettings.getLineToColumn(), newSettings.getLineToColumn()) &&
                Column.equalsId(oldSettings.getColToColumn(), newSettings.getColToColumn()) &&
                Objects.equals(oldSettings.getPipeline(), newSettings.getPipeline()) &&
                Objects.equals(oldSettings.isShowAsHtml(), newSettings.isShowAsHtml()) &&
                Objects.equals(oldSettings.isShowStepping(), newSettings.isShowStepping());

        return !equal;
    }

    public interface BasicTextSettingsView extends
            BasicSettingsView,
            HasUiHandlers<BasicTextSettingsUiHandlers> {

        void setTableList(List<Component> tableList);

        Component getTable();

        void setTable(Component table);

        void setColumns(List<Column> column);

        Column getStreamIdColumn();

        void setStreamIdColumn(Column column);

        Column getPartNoColumn();

        void setPartNoColumn(Column column);

        Column getRecordNoColumn();

        void setRecordNoColumn(Column column);

        Column getLineFromColumn();

        void setLineFromColumn(Column column);

        Column getColFromColumn();

        void setColFromColumn(Column column);

        Column getLineToColumn();

        void setLineToColumn(Column column);

        Column getColToColumn();

        void setColToColumn(Column column);

        void setPipelineView(View view);

        boolean isShowAsHtml();

        void setShowAsHtml(boolean showAsHtml);

        boolean isShowStepping();

        void setShowStepping(boolean showStepping);
    }
}
