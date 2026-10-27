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

package stroom.dashboard.client.component;

import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ColumnRef;
import stroom.security.shared.DocumentPermission;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A Text pane's Basic settings tab ([BasicTextSettings]): its name, the table whose selected row it
/// shows, the columns that locate the row's data (each the closest match to the one saved), its
/// pipeline, and how it is shown.
public class BasicTextSettingsController extends BasicSettingsTabController<BasicTextSettings> {

    private final DocSelectionController pipeline;
    private List<DashboardPane> tableList;
    private boolean ignoreTableChange;
    private final List<ColumnRef> allColumns = new ArrayList<>();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the pipeline's picker.
    @Inject
    public BasicTextSettingsController(final EventBus eventBus,
                                       final BasicTextSettings view,
                                       final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus, view);
        pipeline = make(docSelectionProvider);
        pipeline.attach(view.getPipelineBox());
        pipeline.setIncludedTypes(PipelineDoc.TYPE);
        pipeline.setRequiredPermissions(DocumentPermission.USE);

        view.setOnTableChange(this::onTableChange);
    }

    private void setTableList(final List<DashboardPane> list) {
        ignoreTableChange = true;
        this.tableList = list;
        getView().setTableList(list);
        ignoreTableChange = false;
    }

    private void onTableChange() {
        updateFieldNames(getView().getTable());
    }

    private void updateFieldNames(final DashboardPane component) {
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

    private void addColumnNames(final DashboardPane component, final List<ColumnRef> allColumns) {
        if (component instanceof final HasComponentSelection hasSelectedRows) {
            final List<ColumnRef> columns = hasSelectedRows.getColumnRefs();
            if (columns != null && !columns.isEmpty()) {
                allColumns.addAll(columns);
            }
        }
    }

    private DocRef getPipeline() {
        return pipeline.getSelectedEntityReference();
    }

    private void setPipeline(final DocRef pipeline) {
        this.pipeline.setSelectedEntityReference(pipeline, true);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<DashboardPane> list = getDashboardContext().getComponents().getSortedComponentsByType(
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

    private ColumnRef getClosestColumn(final ColumnRef column) {
        if (column == null) {
            return null;
        }
        ColumnRef bestMatch = null;
        for (final ColumnRef col : allColumns) {
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
        final ComponentConfig result = super.write(componentConfig);
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
        final DashboardPane table = getView().getTable();
        if (table == null) {
            return null;
        }

        return table.getId();
    }

    private void setTableId(final String tableId) {
        getView().setTable(getDashboardContext().getComponents().get(tableId));
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final TextComponentSettings oldSettings = (TextComponentSettings) componentConfig.getSettings();
        final TextComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getTableId(), newSettings.getTableId()) &&
                              Objects.equals(oldSettings.getStreamIdColumn(), newSettings.getStreamIdColumn()) &&
                Objects.equals(oldSettings.getPartNoColumn(), newSettings.getPartNoColumn()) &&
                Objects.equals(oldSettings.getRecordNoColumn(), newSettings.getRecordNoColumn()) &&
                Objects.equals(oldSettings.getLineFromColumn(), newSettings.getLineFromColumn()) &&
                Objects.equals(oldSettings.getColFromColumn(), newSettings.getColFromColumn()) &&
                Objects.equals(oldSettings.getLineToColumn(), newSettings.getLineToColumn()) &&
                Objects.equals(oldSettings.getColToColumn(), newSettings.getColToColumn()) &&
                Objects.equals(oldSettings.getPipeline(), newSettings.getPipeline()) &&
                Objects.equals(oldSettings.isShowAsHtml(), newSettings.isShowAsHtml()) &&
                Objects.equals(oldSettings.isShowStepping(), newSettings.isShowStepping());

        return !equal;
    }
}
