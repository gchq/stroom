/*
 * Copyright 2017 Crown Copyright
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

import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.Field;
import stroom.security.shared.DocumentPermissionNames;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BasicTextSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTextSettingsPresenter.BasicTextSettingsView> implements BasicTextSettingsUiHandlers {
    private final EntityDropDownPresenter pipelinePresenter;
    private List<Component> tableList;
    private boolean ignoreTableChange;
    private final List<Field> allFields = new ArrayList<>();

    @Inject
    public BasicTextSettingsPresenter(final EventBus eventBus, final BasicTextSettingsView view,
                                      final EntityDropDownPresenter pipelinePresenter) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;

        pipelinePresenter.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setPipelineView(pipelinePresenter.getView());
        view.setUiHandlers(this);
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
            allFields.clear();
            if (component == null) {
                if (tableList != null) {
                    tableList.forEach(c -> addFieldNames(c, allFields));
                }
            } else {
                addFieldNames(component, allFields);
            }

            getView().setFields(allFields);
        }
    }

    private void addFieldNames(final Component component, final List<Field> allFields) {
        if (component instanceof TablePresenter) {
            final TablePresenter tablePresenter = (TablePresenter) component;
            final List<Field> fields = tablePresenter.getSettings().getFields();
            if (fields != null && fields.size() > 0) {
                for (final Field field : fields) {
                    allFields.add(field);
                }
            }
        }
    }

    private DocRef getPipeline() {
        return pipelinePresenter.getSelectedEntityReference();
    }

    private void setPipeline(final DocRef pipeline) {
        pipelinePresenter.setSelectedEntityReference(pipeline);
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);

        final List<Component> list = getComponents().getComponentsByType(TablePresenter.TYPE.getId());
        setTableList(list);

        TextComponentSettings settings = (TextComponentSettings) componentData.getSettings();
        final TextComponentSettings.Builder builder = new TextComponentSettings.Builder(settings);
        setTableId(settings.getTableId());

        updateFieldNames(getView().getTable());

        // Make some best matches for fields.
        builder.streamIdField(getClosestField(settings.getStreamIdField()));
        builder.partNoField(getClosestField(settings.getPartNoField()));
        builder.recordNoField(getClosestField(settings.getRecordNoField()));
        builder.lineFromField(getClosestField(settings.getLineFromField()));
        builder.colFromField(getClosestField(settings.getColFromField()));
        builder.lineToField(getClosestField(settings.getLineToField()));
        builder.colToField(getClosestField(settings.getColToField()));

        settings = builder.build();

        getView().setStreamIdField(settings.getStreamIdField());
        getView().setPartNoField(settings.getPartNoField());
        getView().setRecordNoField(settings.getRecordNoField());
        getView().setLineFromField(settings.getLineFromField());
        getView().setColFromField(settings.getColFromField());
        getView().setLineToField(settings.getLineToField());
        getView().setColToField(settings.getColToField());

        setPipeline(settings.getPipeline());
        getView().setShowAsHtml(settings.isShowAsHtml());
        getView().setShowStepping(settings.isShowStepping());
    }

    private Field getClosestField(final Field field) {
        if (field == null) {
            return null;
        }
        Field bestMatch = null;
        for (final Field f : allFields) {
            if (f.getId().equals(field.getId())) {
                bestMatch = f;
                break;
            } else if (bestMatch == null && f.getName().equals(field.getName())) {
                bestMatch = f;
            }
        }

        if (bestMatch == null) {
            return field;
        }

        return bestMatch;
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        ComponentConfig result = super.write(componentConfig);
        final TextComponentSettings oldSettings = (TextComponentSettings) result.getSettings();
        final TextComponentSettings newSettings = writeSettings(oldSettings);
        return new ComponentConfig.Builder(result).settings(newSettings).build();
    }

    private TextComponentSettings writeSettings(final TextComponentSettings settings) {
        return new TextComponentSettings.Builder(settings)
                .tableId(getTableId())
                .streamIdField(getView().getStreamIdField())
                .partNoField(getView().getPartNoField())
                .recordNoField(getView().getRecordNoField())
                .lineFromField(getView().getLineFromField())
                .colFromField(getView().getColFromField())
                .lineToField(getView().getLineToField())
                .colToField(getView().getColToField())
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
                Field.equalsId(oldSettings.getStreamIdField(), newSettings.getStreamIdField()) &&
                Field.equalsId(oldSettings.getPartNoField(), newSettings.getPartNoField()) &&
                Field.equalsId(oldSettings.getRecordNoField(), newSettings.getRecordNoField()) &&
                Field.equalsId(oldSettings.getLineFromField(), newSettings.getLineFromField()) &&
                Field.equalsId(oldSettings.getColFromField(), newSettings.getColFromField()) &&
                Field.equalsId(oldSettings.getLineToField(), newSettings.getLineToField()) &&
                Field.equalsId(oldSettings.getColToField(), newSettings.getColToField()) &&
                Objects.equals(oldSettings.getPipeline(), newSettings.getPipeline()) &&
                Objects.equals(oldSettings.isShowAsHtml(), newSettings.isShowAsHtml()) &&
                Objects.equals(oldSettings.isShowStepping(), newSettings.isShowStepping());

        return !equal;
    }

    public interface BasicTextSettingsView extends
            BasicSettingsTabPresenter.SettingsView,
            HasUiHandlers<BasicTextSettingsUiHandlers> {
        void setTableList(List<Component> tableList);

        Component getTable();

        void setTable(Component table);

        void setFields(List<Field> fields);

        Field getStreamIdField();

        void setStreamIdField(Field field);

        Field getPartNoField();

        void setPartNoField(Field field);

        Field getRecordNoField();

        void setRecordNoField(Field field);

        Field getLineFromField();

        void setLineFromField(Field field);

        Field getColFromField();

        void setColFromField(Field field);

        Field getLineToField();

        void setLineToField(Field field);

        Field getColToField();

        void setColToField(Field field);

        void setPipelineView(View view);

        boolean isShowAsHtml();

        void setShowAsHtml(boolean showAsHtml);

        boolean isShowStepping();

        void setShowStepping(boolean showStepping);
    }
}
