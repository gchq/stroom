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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsBuilder;

import java.util.ArrayList;
import java.util.List;

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

        pipelinePresenter.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
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

        final TextComponentSettings settings = (TextComponentSettings) componentData.getSettings();
        setTableId(settings.getTableId());

        updateFieldNames(getView().getTable());

        // Make some best matches for fields.
        settings.setStreamIdField(getClosestField(settings.getStreamIdField()));
        settings.setPartNoField(getClosestField(settings.getPartNoField()));
        settings.setRecordNoField(getClosestField(settings.getRecordNoField()));
        settings.setLineFromField(getClosestField(settings.getLineFromField()));
        settings.setColFromField(getClosestField(settings.getColFromField()));
        settings.setLineToField(getClosestField(settings.getLineToField()));
        settings.setColToField(getClosestField(settings.getColToField()));

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
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        final TextComponentSettings settings = (TextComponentSettings) componentData.getSettings();
        settings.setTableId(getTableId());

        settings.setStreamIdField(getView().getStreamIdField());
        settings.setPartNoField(getView().getPartNoField());
        settings.setRecordNoField(getView().getRecordNoField());
        settings.setLineFromField(getView().getLineFromField());
        settings.setColFromField(getView().getColFromField());
        settings.setLineToField(getView().getLineToField());
        settings.setColToField(getView().getColToField());

        settings.setPipeline(getPipeline());
        settings.setShowAsHtml(getView().isShowAsHtml());
        settings.setShowStepping(getView().isShowStepping());
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
    public boolean isDirty(final ComponentConfig componentData) {
        if (super.isDirty(componentData)) {
            return true;
        }

        final TextComponentSettings settings = (TextComponentSettings) componentData.getSettings();

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(settings.getTableId(), getTableId());
        builder.appendSuper(Field.equalsId(settings.getStreamIdField(), getView().getStreamIdField()));
        builder.appendSuper(Field.equalsId(settings.getPartNoField(), getView().getPartNoField()));
        builder.appendSuper(Field.equalsId(settings.getRecordNoField(), getView().getRecordNoField()));
        builder.appendSuper(Field.equalsId(settings.getLineFromField(), getView().getLineFromField()));
        builder.appendSuper(Field.equalsId(settings.getColFromField(), getView().getColFromField()));
        builder.appendSuper(Field.equalsId(settings.getLineToField(), getView().getLineToField()));
        builder.appendSuper(Field.equalsId(settings.getColToField(), getView().getColToField()));
        builder.append(settings.getPipeline(), getPipeline());
        builder.append(settings.isShowAsHtml(), getView().isShowAsHtml());
        builder.append(settings.isShowStepping(), getView().isShowStepping());

        return !builder.isEquals();
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
