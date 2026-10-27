/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.structure.client.component;

import stroom.data.client.component.DocRefCell;
import stroom.docref.DocRef;
import stroom.docref.DocRef.DisplayType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// A pipeline element's properties, a row each (its name, its value, which can open a document, and
/// its description), each coloured by where its value comes from, with Edit; one can be selected, and
/// a double click edits it. When read only, Edit is disabled and says so.
public class PropertyList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_SUFFIX = " (read only)";

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton editButton;
    @UiField
    DataGrid<PipelineProperty> dataGrid;

    private final String editTitle;
    private final MultiSelectionModelImpl<PipelineProperty> selectionModel;
    private Consumer<PipelineProperty> onEdit = property -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function, Function, DocRefCell.Builder)].
    public PropertyList() {
        initWidget(BINDER.createAndBindUi(this));
        editTitle = editButton.getTitle();
        // Rows keyed by element and property name: an edit replaces the row
        dataGrid.setKeyProvider(property -> property.getElement() + ":" + property.getName());
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        editButton.setOnClick(() -> onEdit.accept(selectionModel.getSelected()));
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit.accept(selectionModel.getSelected());
            }
        });
    }

    /// Adds the columns, once.
    ///
    /// @param state       Where a property's value comes from, which colours it.
    /// @param description Gives a property's description.
    /// @param valueCell   The value cell's builder, configured to open the documents it shows.
    public void addColumns(final Function<PipelineProperty, PropertyState> state,
                           final Function<PipelineProperty, String> description,
                           final DocRefCell.Builder<PipelineProperty> valueCell) {
        // Name.
        dataGrid.addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                return getSafeHtmlWithState(property.getName(), state.apply(property).getClassName());
            }
        }, "Property Name", 300);

        // Value.
        // Value could be a docRef, in which case it gets a hover link to open it) or a simple string
        valueCell
                .showIcon(true)
                .canOpenFunction(property -> property.getValue() != null &&
                                             !property.getValue().isEmbedded())
                // A removed property's value is the default one
                .cssClassFunction(property -> state.apply(property).getValueClassName())
                .cellTextFunction(property -> {
                    final PipelinePropertyValue propertyValue = NullSafe.get(
                            property, PipelineProperty::getValue);
                    if (propertyValue != null) {
                        final DocRef entity = propertyValue.getEntity();
                        if (entity != null) {
                            return SafeHtmlUtils.fromString(entity.getDisplayValue(DisplayType.AUTO));
                        } else {
                            return SafeHtmlUtil.getSafeHtml(NullSafe.toString(propertyValue));
                        }
                    } else {
                        return SafeHtmlUtils.EMPTY_SAFE_HTML;
                    }
                })
                .docRefFunction(pipelineProperty -> NullSafe.get(
                        pipelineProperty,
                        PipelineProperty::getValue,
                        PipelinePropertyValue::getEntity));
        final Column<PipelineProperty, PipelineProperty> valueCol = DataGridUtil.docRefColumnBuilder(valueCell)
                .build();
        dataGrid.addAutoResizableColumn(valueCol, "Value", 30, 200);

        // Description.
        dataGrid.addAutoResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                return SafeHtmlUtil.getSafeHtml(description.apply(property));
            }
        }, "Description", 70, 200);
    }

    /// Shows properties, with none selected.
    ///
    /// @param propertyList The properties.
    public void setData(final List<PipelineProperty> propertyList) {
        selectionModel.clear();
        dataGrid.setRowData(0, propertyList);
        dataGrid.setRowCount(propertyList.size());
        enableButtons();
    }

    /// @param tableName The list's name, for assistive technology.
    public void setTableName(final String tableName) {
        dataGrid.setTableName(tableName);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @param onEdit Given the selected property, or `null`, when Edit is pressed or a property is
    ///               double clicked.
    public void setOnEdit(final Consumer<PipelineProperty> onEdit) {
        this.onEdit = onEdit;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        editButton.setEnabled(!readOnly && selectionModel.getSelected() != null);
        editButton.setTitle(readOnly
                ? editTitle + READ_ONLY_SUFFIX
                : editTitle);
    }

    private static SafeHtml getSafeHtmlWithState(final String string, final String className) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.append(SafeHtmlUtils.fromTrustedString("<div class=\"" + className + "\">"));
        builder.appendEscaped(string);
        builder.append(SafeHtmlUtils.fromTrustedString("</div>"));

        return builder.toSafeHtml();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PropertyList> {

    }


    /// Where a property's value comes from, which colours it.
    public enum PropertyState {
        /// Set on this pipeline.
        ADDED("pipeline-property pipeline-property--added"),
        /// Set back to its default on this pipeline, hiding an inherited value.
        REMOVED("pipeline-property pipeline-property--removed"),
        /// Inherited from a parent pipeline.
        INHERITED("pipeline-property pipeline-property--inherited"),
        /// Its default value.
        DEFAULT("pipeline-property pipeline-property--default");

        private final String className;

        PropertyState(final String className) {
            this.className = className;
        }

        /// @return The class that colours its name.
        public String getClassName() {
            return className;
        }

        /// @return The class that colours its value: a removed property's value is its default.
        public String getValueClassName() {
            return this == REMOVED
                    ? DEFAULT.className
                    : className;
        }
    }
}
