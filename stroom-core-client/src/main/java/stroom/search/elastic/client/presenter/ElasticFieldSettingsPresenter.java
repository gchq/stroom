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

package stroom.search.elastic.client.presenter;

import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.search.elastic.client.presenter.ElasticFieldRow.Kind;
import stroom.search.elastic.client.presenter.ElasticFieldSettingsPresenter.ElasticFieldSettingsView;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.user.cellview.client.Header;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

/// The Settings tab of the Elasticsearch index Fields tab, for editing the selected field's settings.
///
/// The selected field's name heads the tab. Every field has an Enabled setting. `dense_vector` fields also have
/// vector search settings, and virtual fields have a list of named groups, each with an expression, edited in a
/// dialog. Virtual fields are renamed from the field list.
/// Edits are written to the shared [ElasticFieldListModel] and reported to the [ElasticFieldSettingsListener];
/// nothing is sent to the server.
public class ElasticFieldSettingsPresenter
        extends MyPresenterWidget<ElasticFieldSettingsView>
        implements ElasticFieldSettingsUiHandlers {

    static final String NOTE_MISSING = "This field is missing from all source indices, so it can't be enabled or " +
                                       "disabled. Remove its settings if they are no longer needed.";
    static final String NOTE_CONFLICT = "The source indices map this field to different types, so it is always " +
                                        "hidden from users.";
    static final String NOTE_CLASHING = "This virtual field is hidden by the mapped field of the same name. Rename " +
                                        "it to use it.";
    static final String NOTE_MANDATORY = "Stroom needs this field to search the index, so it can't be disabled.";
    static final String NOTE_PARENT_DISABLED_PREFIX = "Disabled because the field it belongs to is disabled: ";
    static final String ERROR_SCORE_MINIMUM = "Enter a number, e.g. 0.8.";

    private static final String ADD_GROUP = "Add group";
    private static final String EDIT_GROUP = "Edit group";
    private static final String VIEW_GROUP = "View group";

    private final DocSelectionBoxPresenter embeddingModelPresenter;
    private final DocSelectionBoxPresenter rerankModelPresenter;
    private final ElasticVirtualFieldGroupPresenter groupPresenter;
    private final SimpleFieldSelectionListModel fieldSelectionListModel = new SimpleFieldSelectionListModel();
    private final MyDataGrid<ElasticVirtualFieldGroup> groupsGrid;
    private final MultiSelectionModelImpl<ElasticVirtualFieldGroup> groupsSelectionModel;
    private final ButtonView addGroupButton;
    private final ButtonView editGroupButton;
    private final ButtonView removeGroupButton;

    private ElasticFieldListModel model;
    private ElasticFieldSettingsListener listener;
    private boolean readOnly = true;
    private ElasticFieldRow row;
    private List<ElasticVirtualFieldGroup> groups = new ArrayList<>();
    private int selectedGroupIndex = -1;
    private boolean updating;

    @Inject
    public ElasticFieldSettingsPresenter(final EventBus eventBus,
                                         final ElasticFieldSettingsView view,
                                         final DocSelectionBoxPresenter embeddingModelPresenter,
                                         final DocSelectionBoxPresenter rerankModelPresenter,
                                         final ElasticVirtualFieldGroupPresenter groupPresenter,
                                         final PagerView groupsPagerView) {
        super(eventBus, view);
        this.embeddingModelPresenter = embeddingModelPresenter;
        this.rerankModelPresenter = rerankModelPresenter;
        this.groupPresenter = groupPresenter;

        embeddingModelPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        embeddingModelPresenter.setRequiredPermissions(DocumentPermission.USE);
        rerankModelPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        rerankModelPresenter.setRequiredPermissions(DocumentPermission.USE);

        groupsGrid = new MyDataGrid<>(this, MyDataGrid.MASSIVE_LIST_PAGE_SIZE);
        groupsGrid.setTableName("Virtual Field Groups");
        groupsSelectionModel = groupsGrid.addDefaultSelectionModel(false);
        // No heading, so the grid hides its header row; the Groups label above the list names it
        groupsGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(ElasticVirtualFieldGroup::getName).build(),
                (Header<?>) null,
                200);
        groupsPagerView.setDataWidget(groupsGrid);
        addGroupButton = groupsPagerView.addButton(SvgPresets.ADD.title(ADD_GROUP));
        editGroupButton = groupsPagerView.addButton(SvgPresets.EDIT.title(EDIT_GROUP));
        removeGroupButton = groupsPagerView.addButton(SvgPresets.DELETE.title("Remove group"));

        view.setUiHandlers(this);
        view.setEmbeddingModelView(embeddingModelPresenter.getView());
        view.setRerankModelView(rerankModelPresenter.getView());
        view.setGroupsView(groupsPagerView);
        showSections();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(embeddingModelPresenter.addDataSelectionHandler(event -> onDenseVectorChange()));
        registerHandler(rerankModelPresenter.addDataSelectionHandler(event -> onDenseVectorChange()));
        registerHandler(groupsSelectionModel.addSelectionHandler(event -> {
            onGroupSelected();
            if (event.getSelectionType().isDoubleSelect()) {
                editGroup();
            }
        }));
        registerHandler(addGroupButton.addClickHandler(event -> addGroup()));
        registerHandler(editGroupButton.addClickHandler(event -> editGroup()));
        registerHandler(removeGroupButton.addClickHandler(event -> removeGroup()));
    }

    /// @param model the working copy of the doc's fields and settings that edits are written to
    public void setModel(final ElasticFieldListModel model) {
        this.model = model;
    }

    /// @param listener told about each edit
    public void setListener(final ElasticFieldSettingsListener listener) {
        this.listener = listener;
    }

    /// @param indexDocRef the index doc, used when picking terms for a group's expression
    public void setIndex(final DocRef indexDocRef) {
        groupPresenter.setIndex(indexDocRef, fieldSelectionListModel);
    }

    /// @param readOnly `true` if the doc can't be changed
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        showSections();
    }

    /// Shows the settings of a field.
    ///
    /// @param row the field, or `null` for none
    public void setField(final ElasticFieldRow row) {
        final boolean sameField = row != null && row.equals(this.row);
        this.row = row;
        if (!sameField) {
            selectedGroupIndex = -1;
        }
        showSections();
    }

    @Override
    public void onEnabledChange() {
        if (canEdit()) {
            model.setEnabled(row.getName(), getView().isEnabledTicked());
            fireChanged();
        }
    }

    @Override
    public void onDenseVectorChange() {
        if (!canEdit() || !isDenseVector(row)) {
            return;
        }
        final Float scoreMinimum = parseFloat(getView().getRerankScoreMinimumText());
        getView().setRerankScoreMinimumError(scoreMinimum == null
                ? ERROR_SCORE_MINIMUM
                : null);
        if (scoreMinimum == null) {
            return;
        }

        final ElasticDenseVectorFormValues values = new ElasticDenseVectorFormValues(
                embeddingModelPresenter.getSelectedEntityReference(),
                getView().getNearestNeighbourCount(),
                rerankModelPresenter.getSelectedEntityReference(),
                getView().getRerankBatchSize(),
                getView().getRerankTextField(),
                getView().getRerankScoreField(),
                scoreMinimum);
        final ElasticIndexFieldSettings existing = model.findSettings(row.getName());
        final ElasticDenseVectorSettings existingDenseVector = NullSafe.get(existing,
                ElasticIndexFieldSettings::getDenseVector);
        final ElasticIndexFieldSettings.Builder builder = existing == null
                ? ElasticIndexFieldSettings.builder(row.getName())
                : existing.copy();
        model.putSettings(builder.denseVector(values.toSettings(existingDenseVector)).build());
        fireChanged();
    }

    private boolean canEdit() {
        return !updating && !readOnly && row != null && model != null;
    }

    private void fireChanged() {
        if (row != null) {
            fireChanged(row.getName());
        }
    }

    private void fireChanged(final String fieldName) {
        if (listener != null) {
            listener.onFieldSettingsChanged(fieldName);
        }
    }

    private void showSections() {
        updating = true;
        try {
            final ElasticFieldSettingsView view = getView();
            if (row == null || model == null) {
                view.setFieldName(null);
                view.setDenseVectorVisible(false);
                view.setVirtualVisible(false);
                view.setEnabled(false, false, null);
                return;
            }
            view.setFieldName(row.getName());
            view.setEnabled(row.isEnabled(), !readOnly && row.isEnabledEditable(), getEnabledNote(row));
            showDenseVector();
            showVirtual();
        } finally {
            updating = false;
        }
    }

    private String getEnabledNote(final ElasticFieldRow row) {
        if (row.getKind() == Kind.MISSING) {
            return NOTE_MISSING;
        }
        if (row.getKind() == Kind.CLASHING_VIRTUAL) {
            return NOTE_CLASHING;
        }
        if (row.hasTypeConflict()) {
            return NOTE_CONFLICT;
        }
        if (row.getKind() == Kind.MAPPED && model.isMandatory(row.getName())) {
            return NOTE_MANDATORY;
        }
        final String parent = NullSafe.get(row.getField(), ElasticIndexField::getMultiFieldParent);
        if (parent != null && !model.isFieldEnabled(parent)) {
            return NOTE_PARENT_DISABLED_PREFIX + parent;
        }
        return null;
    }

    private static boolean isDenseVector(final ElasticFieldRow row) {
        return row != null
               && row.getKind() == Kind.MAPPED
               && !row.hasTypeConflict()
               && row.getField().getFldType() == FieldType.DENSE_VECTOR;
    }

    private void showDenseVector() {
        final ElasticFieldSettingsView view = getView();
        final boolean denseVector = isDenseVector(row);
        view.setDenseVectorVisible(denseVector);
        if (!denseVector) {
            return;
        }
        final ElasticDenseVectorFormValues values = ElasticDenseVectorFormValues.fromSettings(
                NullSafe.get(row.getSettings(), ElasticIndexFieldSettings::getDenseVector));
        embeddingModelPresenter.setSelectedEntityReference(values.getEmbeddingModelRef(), true);
        rerankModelPresenter.setSelectedEntityReference(values.getRerankModelRef(), true);

        final List<String> textFields = new ArrayList<>(model.getRerankTextFieldNames());
        if (values.getRerankTextField() != null && !textFields.contains(values.getRerankTextField())) {
            // Keep a stored choice visible even if the field has gone from the mapping
            textFields.add(values.getRerankTextField());
        }
        view.setRerankTextFieldOptions(textFields);
        view.setDenseVectorValues(values.getNearestNeighbourCount(),
                values.getRerankBatchSize(),
                values.getRerankTextField(),
                values.getRerankScoreField(),
                ElasticFieldStatsSupport.formatDecimal(values.getRerankScoreMinimum(), 3));
        view.setRerankScoreMinimumError(null);

        final boolean editable = !readOnly;
        embeddingModelPresenter.setEnabled(editable);
        rerankModelPresenter.setEnabled(editable);
        view.setDenseVectorEditable(editable);
    }

    private void showVirtual() {
        final ElasticFieldSettingsView view = getView();
        final boolean virtual = row.isVirtual();
        view.setVirtualVisible(virtual);
        if (!virtual) {
            groups = new ArrayList<>();
            return;
        }

        fieldSelectionListModel.clear();
        for (final String fieldName : model.getKeywordFieldNames()) {
            fieldSelectionListModel.addItem(QueryField.builder()
                    .fldName(fieldName)
                    .fldType(FieldType.KEYWORD)
                    .conditionSet(ConditionSet.getElastic(FieldType.KEYWORD))
                    .queryable(true)
                    .build());
        }

        groups = new ArrayList<>(NullSafe.list(NullSafe.get(row.getSettings(),
                ElasticIndexFieldSettings::getVirtualGroups)));
        if (selectedGroupIndex >= groups.size()) {
            selectedGroupIndex = groups.size() - 1;
        }
        if (selectedGroupIndex < 0 && !groups.isEmpty()) {
            selectedGroupIndex = 0;
        }
        showGroups();
    }

    private void showGroups() {
        groupsGrid.setRowData(0, groups);
        groupsGrid.setRowCount(groups.size(), true);
        if (hasSelectedGroup()) {
            groupsSelectionModel.setSelected(groups.get(selectedGroupIndex), new SelectionType(), false);
        } else {
            groupsSelectionModel.clear(false);
        }
        updateGroupButtons();
    }

    private void updateGroupButtons() {
        addGroupButton.setEnabled(!readOnly);
        // A read-only doc's groups can still be viewed
        editGroupButton.setEnabled(hasSelectedGroup());
        editGroupButton.setTitle(readOnly
                ? VIEW_GROUP
                : EDIT_GROUP);
        removeGroupButton.setEnabled(!readOnly && hasSelectedGroup());
    }

    private boolean hasSelectedGroup() {
        return selectedGroupIndex >= 0 && selectedGroupIndex < groups.size();
    }

    private void onGroupSelected() {
        final ElasticVirtualFieldGroup selected = groupsSelectionModel.getSelected();
        final int index = selected == null
                ? -1
                : groups.indexOf(selected);
        if (index != selectedGroupIndex) {
            selectedGroupIndex = index;
            updateGroupButtons();
        }
    }

    private void addGroup() {
        if (canEdit() && row.isVirtual()) {
            // The selected field may change while the dialog is open, so the groups go to the field it was opened for
            final String fieldName = row.getName();
            final List<ElasticVirtualFieldGroup> groupsAtOpen = groups;
            groupPresenter.show(groupsAtOpen, -1, false, group -> {
                final List<ElasticVirtualFieldGroup> newGroups = ElasticVirtualFieldGroups.add(groupsAtOpen,
                        group.getName(),
                        group.getExpression());
                applyGroups(fieldName, newGroups, newGroups.size() - 1);
            });
        }
    }

    private void editGroup() {
        if (updating || row == null || model == null || !row.isVirtual() || !hasSelectedGroup()) {
            return;
        }
        final int index = selectedGroupIndex;
        final String fieldName = row.getName();
        final List<ElasticVirtualFieldGroup> groupsAtOpen = groups;
        groupPresenter.show(groupsAtOpen, index, readOnly, group -> {
            if (canEdit()) {
                applyGroups(fieldName,
                        ElasticVirtualFieldGroups.replace(groupsAtOpen,
                                index,
                                group.getName(),
                                group.getExpression()),
                        index);
            }
        });
    }

    private void removeGroup() {
        if (canEdit() && row.isVirtual() && hasSelectedGroup()) {
            final List<ElasticVirtualFieldGroup> newGroups = ElasticVirtualFieldGroups.remove(groups,
                    selectedGroupIndex);
            applyGroups(row.getName(), newGroups, Math.min(selectedGroupIndex, newGroups.size() - 1));
        }
    }

    /// Sets the groups of a virtual field.
    ///
    /// @param fieldName          the virtual field, which may no longer be the selected field
    /// @param newGroups          its new groups
    /// @param selectedGroupIndex the group to select if the field is still shown
    private void applyGroups(final String fieldName,
                             final List<ElasticVirtualFieldGroup> newGroups,
                             final int selectedGroupIndex) {
        if (!model.setVirtualGroups(fieldName, newGroups)) {
            // The virtual field was deleted or renamed while its group was being edited
            return;
        }
        if (row != null && fieldName.equals(row.getName())) {
            this.selectedGroupIndex = selectedGroupIndex;
            groups = new ArrayList<>(newGroups);
            showGroups();
        }
        fireChanged(fieldName);
    }

    private static Float parseFloat(final String text) {
        final String trimmed = NullSafe.trim(text);
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(trimmed);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /// The view of the Settings tab.
    public interface ElasticFieldSettingsView extends View, HasUiHandlers<ElasticFieldSettingsUiHandlers> {

        /// @param fieldName the full name of the field whose settings are shown, as the tab's heading, or `null`
        ///                  to show no heading
        void setFieldName(String fieldName);

        /// @param ticked   whether the Enabled checkbox is ticked
        /// @param editable whether the user may change it
        /// @param note     text explaining why the field can't be changed, or `null`
        void setEnabled(boolean ticked, boolean editable, String note);

        /// @return whether the Enabled checkbox is ticked
        boolean isEnabledTicked();

        /// @param visible `true` to show the `dense_vector` settings
        void setDenseVectorVisible(boolean visible);

        /// @param view the embedding model selector
        void setEmbeddingModelView(View view);

        /// @param view the rerank model selector
        void setRerankModelView(View view);

        /// @param fieldNames the fields that may be chosen as the rerank text field
        void setRerankTextFieldOptions(List<String> fieldNames);

        /// @param nearestNeighbourCount the nearest neighbour count
        /// @param rerankBatchSize       the rerank batch size
        /// @param rerankTextField       the rerank text field, or `null`
        /// @param rerankScoreField      the score field name, or `null`
        /// @param rerankScoreMinimum    the rerank score minimum, as text
        void setDenseVectorValues(int nearestNeighbourCount,
                                  int rerankBatchSize,
                                  String rerankTextField,
                                  String rerankScoreField,
                                  String rerankScoreMinimum);

        /// @return the nearest neighbour count
        int getNearestNeighbourCount();

        /// @return the rerank batch size
        int getRerankBatchSize();

        /// @return the rerank text field, or `null`
        String getRerankTextField();

        /// @return the score field name as entered
        String getRerankScoreField();

        /// @return the rerank score minimum as entered
        String getRerankScoreMinimumText();

        /// @param error the reason the rerank score minimum is not valid, or `null`
        void setRerankScoreMinimumError(String error);

        /// @param editable `true` if the `dense_vector` settings may be changed
        void setDenseVectorEditable(boolean editable);

        /// @param visible `true` to show the virtual field settings
        void setVirtualVisible(boolean visible);

        /// @param view the list of the virtual field's groups
        void setGroupsView(View view);
    }
}
