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

package stroom.search.elastic.client.component;

import stroom.explorer.client.component.DocSelectionController;
import stroom.search.elastic.client.model.FieldNameSelectionListModel;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.InputChecks;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.BaseSelectionBox;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.text.client.Label;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.HeadingElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The Settings panel of an Elastic index's Fields tab: the selected field's name and whether it is
/// enabled (with why it can't be changed, if it can't), then a `dense_vector` field's vector search
/// settings or a virtual field's groups. Its controller ([ElasticFieldSettingsController]) attaches
/// the model pickers and writes changes to the index.
public class ElasticFieldSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String VIEW_GROUP = "View Selected Group";

    @UiField
    HeadingElement fieldName;
    @UiField
    TickBox enabled;
    @UiField
    Label enabledNote;
    @UiField
    FlowPanel denseVectorSection;
    @UiField
    DropDownBox embeddingModel;
    @UiField
    ValueSpinner nearestNeighbourCount;
    @UiField
    DropDownBox rerankModel;
    @UiField
    ValueSpinner rerankBatchSize;
    @UiField
    BaseSelectionBox<String, SimpleSelectionItemWrapper<String>> rerankTextField;
    @UiField
    TextBox rerankScoreField;
    @UiField
    TextBox rerankScoreMinimum;
    @UiField
    FlowPanel virtualSection;
    @UiField
    ToolbarButton addGroup;
    @UiField
    ToolbarButton editGroup;
    @UiField
    ToolbarButton removeGroup;
    @UiField
    DataGrid<ElasticVirtualFieldGroup> groups;

    private final FieldNameSelectionListModel rerankTextFieldModel = new FieldNameSelectionListModel();
    private final MultiSelectionModelImpl<ElasticVirtualFieldGroup> groupsSelectionModel;
    private final String editGroupTitle;
    private List<ElasticVirtualFieldGroup> groupList = List.of();
    private boolean enabledEditable;
    private Runnable onEnabledChange = () -> {
    };
    private Runnable onDenseVectorChange = () -> {
    };
    private Consumer<GroupAction> onGroupAction = action -> {
    };

    /// Makes an empty panel.
    public ElasticFieldSettings() {
        initWidget(BINDER.createAndBindUi(this));
        editGroupTitle = editGroup.getTitle();
        UIObject.setVisible(fieldName, false);

        // Filterable and paged, as an index can have many text fields
        rerankTextField.setModel(rerankTextFieldModel);
        rerankTextFieldModel.setNonSelectString(DocSelectionController.NONE_DISPLAY_VALUE);
        // Changes aren't kept while it isn't a score, and Save refuses
        rerankScoreMinimum.setInvalidReason(InputChecks.numberFrom(0, 1));

        enabled.setOnChange(ticked -> onEnabledChange.run());
        nearestNeighbourCount.setOnChange(value -> onDenseVectorChange.run());
        rerankBatchSize.setOnChange(value -> onDenseVectorChange.run());
        rerankTextField.addValueChangeHandler(e -> onDenseVectorChange.run());
        rerankScoreField.addValueChangeHandler(e -> onDenseVectorChange.run());
        rerankScoreMinimum.addValueChangeHandler(e -> onDenseVectorChange.run());

        groups.addResizableColumn(DataGridUtil.textColumnBuilder(ElasticVirtualFieldGroup::getName).build(),
                "Name", 300);
        groupsSelectionModel = groups.addDefaultSelectionModel(false);
        groupsSelectionModel.addSelectionHandler(event -> {
            updateGroupButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onGroupAction.accept(GroupAction.EDIT);
            }
        });
        addGroup.setOnClick(() -> onGroupAction.accept(GroupAction.ADD));
        editGroup.setOnClick(() -> onGroupAction.accept(GroupAction.EDIT));
        removeGroup.setOnClick(() -> onGroupAction.accept(GroupAction.REMOVE));
        updateGroupButtons();
    }

    /// @return The embedding model's picker, for the controller to attach to.
    public DropDownBox getEmbeddingModelBox() {
        return embeddingModel;
    }

    /// @return The rerank model's picker, for the controller to attach to.
    public DropDownBox getRerankModelBox() {
        return rerankModel;
    }

    /// @param name The selected field's full name, which heads the panel, or null for none.
    public void setFieldName(final String name) {
        fieldName.setInnerText(NullSafe.string(name));
        UIObject.setVisible(fieldName, NullSafe.isNonBlankString(name));
    }

    /// Shows whether the field is enabled.
    ///
    /// @param ticked   Whether it is.
    /// @param editable Whether the user may change it.
    /// @param note     Why it can't be changed, or null.
    public void setEnabledState(final boolean ticked, final boolean editable, final String note) {
        enabledEditable = editable;
        enabled.setValue(ticked);
        enabled.setEnabled(editable && !isReadOnly());
        enabledNote.setText(NullSafe.string(note));
        enabledNote.setVisible(NullSafe.isNonBlankString(note));
    }

    /// @return Whether Enabled is ticked.
    public boolean isEnabledTicked() {
        return Boolean.TRUE.equals(enabled.getValue());
    }

    /// @param onEnabledChange Run when the user ticks or unticks Enabled.
    public void setOnEnabledChange(final Runnable onEnabledChange) {
        this.onEnabledChange = Objects.requireNonNull(onEnabledChange, "onEnabledChange");
    }

    /// @param visible Whether to show the `dense_vector` field's vector search settings.
    public void setDenseVectorVisible(final boolean visible) {
        denseVectorSection.setVisible(visible);
    }

    /// @param fieldNames The text fields that may be chosen as the rerank text field.
    public void setRerankTextFieldOptions(final List<String> fieldNames) {
        rerankTextFieldModel.clear();
        // Clearing removes 'None' too, so it is added back first
        rerankTextFieldModel.setNonSelectString(DocSelectionController.NONE_DISPLAY_VALUE);
        rerankTextFieldModel.addItems(fieldNames);
    }

    /// Shows the vector search settings, but for the models.
    ///
    /// @param nearestNeighbourCount The nearest neighbour count.
    /// @param rerankBatchSize       The rerank batch size.
    /// @param rerankTextField       The rerank text field, or null.
    /// @param rerankScoreField      The score field's name, or null.
    /// @param rerankScoreMinimum    The minimum rerank score, as text.
    public void setDenseVectorValues(final int nearestNeighbourCount,
                                     final int rerankBatchSize,
                                     final String rerankTextField,
                                     final String rerankScoreField,
                                     final String rerankScoreMinimum) {
        this.nearestNeighbourCount.setValue(nearestNeighbourCount);
        this.rerankBatchSize.setValue(rerankBatchSize);
        this.rerankTextField.setValue(rerankTextField);
        this.rerankScoreField.setValue(NullSafe.string(rerankScoreField));
        this.rerankScoreMinimum.setValue(NullSafe.string(rerankScoreMinimum));
    }

    /// @return The nearest neighbour count.
    public int getNearestNeighbourCount() {
        return nearestNeighbourCount.getIntValue();
    }

    /// @return The rerank batch size.
    public int getRerankBatchSize() {
        return rerankBatchSize.getIntValue();
    }

    /// @return The rerank text field, or null.
    public String getRerankTextField() {
        return rerankTextField.getValue();
    }

    /// @return The score field's name as entered.
    public String getRerankScoreField() {
        return rerankScoreField.getValue();
    }

    /// @return The minimum rerank score entered, or null while it isn't a score from 0 to 1 (when
    /// Save refuses).
    public Float getRerankScoreMinimum() {
        if (rerankScoreMinimum.getInvalidReason() != null) {
            return null;
        }
        final Double score = InputChecks.parseNumber(rerankScoreMinimum.getValue());
        return score == null
                ? null
                : Float.valueOf(score.floatValue());
    }

    /// @param onDenseVectorChange Run when the user changes a vector search setting, but for the
    ///                            models (whose pickers tell the controller).
    public void setOnDenseVectorChange(final Runnable onDenseVectorChange) {
        this.onDenseVectorChange = Objects.requireNonNull(onDenseVectorChange, "onDenseVectorChange");
    }

    /// @param visible Whether to show the virtual field's groups.
    public void setVirtualVisible(final boolean visible) {
        virtualSection.setVisible(visible);
    }

    /// Shows a virtual field's groups.
    ///
    /// @param groups        The groups.
    /// @param selectedIndex The group to select, or -1 for none.
    public void setGroups(final List<ElasticVirtualFieldGroup> groups, final int selectedIndex) {
        groupList = List.copyOf(groups);
        this.groups.setRowData(0, groupList);
        this.groups.setRowCount(groupList.size(), true);
        if (selectedIndex >= 0 && selectedIndex < groupList.size()) {
            groupsSelectionModel.setSelected(groupList.get(selectedIndex));
        } else {
            groupsSelectionModel.clear();
        }
        updateGroupButtons();
    }

    /// @return The position of the selected group, or -1 for none.
    public int getSelectedGroupIndex() {
        final ElasticVirtualFieldGroup selected = groupsSelectionModel.getSelected();
        return selected == null
                ? -1
                : groupList.indexOf(selected);
    }

    /// @param onGroupAction Given what to do to the groups: add one, or edit (view, if read only) or
    ///                      remove the selected one.
    public void setOnGroupAction(final Consumer<GroupAction> onGroupAction) {
        this.onGroupAction = Objects.requireNonNull(onGroupAction, "onGroupAction");
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enabled.setEnabled(enabledEditable && !readOnly);
        nearestNeighbourCount.setReadOnly(readOnly);
        rerankBatchSize.setReadOnly(readOnly);
        rerankTextField.setReadOnly(readOnly);
        rerankScoreField.setReadOnly(readOnly);
        rerankScoreMinimum.setReadOnly(readOnly);
        updateGroupButtons();
    }

    private void updateGroupButtons() {
        final boolean selected = groupsSelectionModel.getSelected() != null;
        addGroup.setEnabled(!isReadOnly());
        // A read-only index's groups can still be viewed
        editGroup.setEnabled(selected);
        editGroup.setTitle(isReadOnly()
                ? VIEW_GROUP
                : editGroupTitle);
        editGroup.setLabel(isReadOnly()
                ? "View"
                : "Edit");
        removeGroup.setEnabled(selected && !isReadOnly());
    }


    // --------------------------------------------------------------------------------


    /// What can be done to a virtual field's groups.
    public enum GroupAction {
        /// Add a group.
        ADD,
        /// Edit the selected group, or view it if the index is read only.
        EDIT,
        /// Remove the selected group.
        REMOVE
    }


    interface Binder extends UiBinder<Widget, ElasticFieldSettings> {

    }
}
