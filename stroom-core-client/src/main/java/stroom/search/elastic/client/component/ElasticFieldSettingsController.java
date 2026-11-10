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

import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.model.SimpleFieldSelectionListModel;
import stroom.search.elastic.client.component.ElasticFieldSettings.GroupAction;
import stroom.search.elastic.client.model.ElasticDenseVectorFormValues;
import stroom.search.elastic.client.model.ElasticFieldListModel;
import stroom.search.elastic.client.model.ElasticFieldRow;
import stroom.search.elastic.client.model.ElasticFieldRow.Kind;
import stroom.search.elastic.client.model.ElasticFieldStatsSupport;
import stroom.search.elastic.client.model.ElasticVirtualFieldGroups;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The Settings panel of an Elastic index's Fields tab ([ElasticFieldSettings]): edits the selected
/// field's settings in the field list's working copy ([ElasticFieldListModel]). Every field has
/// Enabled; `dense_vector` fields also have vector search settings, and virtual fields groups, each
/// a named query edited in a dialog ([ElasticVirtualFieldGroupEditorController]). Nothing is sent
/// to the server: the index is saved with them.
public class ElasticFieldSettingsController extends Controller {

    static final String NOTE_MISSING = "This field is missing from all source indices, so it can't be enabled or " +
                                       "disabled. Remove its settings if they are no longer needed.";
    static final String NOTE_CONFLICT = "The source indices map this field to different types, so it is always " +
                                        "hidden from users.";
    static final String NOTE_CLASHING = "This virtual field is hidden by the mapped field of the same name. Rename " +
                                        "it to use it.";
    static final String NOTE_MANDATORY = "Stroom needs this field to search the index, so it can't be disabled.";
    static final String NOTE_PARENT_DISABLED_PREFIX = "Disabled because the field it belongs to is disabled: ";

    private final ElasticFieldSettings view;
    private final DocSelectionController embeddingModel;
    private final DocSelectionController rerankModel;
    private final Provider<ElasticVirtualFieldGroupEditorController> groupEditorProvider;
    private final SimpleFieldSelectionListModel groupFields = new SimpleFieldSelectionListModel();

    private ElasticFieldListModel model;
    private Consumer<String> onChange = fieldName -> {
    };
    private DocRef index;
    private ElasticFieldRow row;
    private List<ElasticVirtualFieldGroup> groups = new ArrayList<>();
    private boolean updating;

    /// Made by GIN, with its panel.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The panel.
    /// @param docSelectionProvider Makes the model pickers' controllers.
    /// @param groupEditorProvider  Makes the dialog that edits a virtual field's group.
    @Inject
    public ElasticFieldSettingsController(final EventBus eventBus,
                                          final ElasticFieldSettings view,
                                          final Provider<DocSelectionController> docSelectionProvider,
                                          final Provider<ElasticVirtualFieldGroupEditorController>
                                                  groupEditorProvider) {
        super(eventBus);
        this.view = view;
        this.groupEditorProvider = groupEditorProvider;
        initWidget(view);

        embeddingModel = make(docSelectionProvider);
        embeddingModel.attach(view.getEmbeddingModelBox());
        embeddingModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        embeddingModel.setRequiredPermissions(DocumentPermission.USE);
        rerankModel = make(docSelectionProvider);
        rerankModel.attach(view.getRerankModelBox());
        rerankModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        rerankModel.setRequiredPermissions(DocumentPermission.USE);

        view.setOnEnabledChange(this::onEnabledChange);
        view.setOnDenseVectorChange(this::onDenseVectorChange);
        view.setOnGroupAction(this::onGroupAction);
        own(embeddingModel.addSelectionListener(docRef -> onDenseVectorChange()));
        own(rerankModel.addSelectionListener(docRef -> onDenseVectorChange()));
        watch(Contexts.READ_ONLY, readOnly -> {
            view.setReadOnly(readOnly);
            embeddingModel.setReadOnly(readOnly);
            rerankModel.setReadOnly(readOnly);
            showField();
        });
    }

    /// @param model The working copy of the index's fields and settings that changes go to.
    public void setModel(final ElasticFieldListModel model) {
        this.model = model;
    }

    /// @param onChange Given the name of the field whose settings the user changed.
    public void setOnChange(final Consumer<String> onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    /// @param index The index, whose fields a virtual field's group may query.
    public void setIndex(final DocRef index) {
        this.index = index;
    }

    /// Shows a field's settings.
    ///
    /// @param row The field, or null for none.
    public void setField(final ElasticFieldRow row) {
        this.row = row;
        showField();
    }

    private boolean isReadOnly() {
        return context(Contexts.READ_ONLY);
    }

    private boolean canEdit() {
        return !updating && !isReadOnly() && row != null && model != null;
    }

    private void fireChanged(final String fieldName) {
        onChange.accept(fieldName);
    }

    private void showField() {
        updating = true;
        try {
            if (row == null || model == null) {
                view.setFieldName(null);
                view.setDenseVectorVisible(false);
                view.setVirtualVisible(false);
                view.setEnabledState(false, false, null);
                return;
            }
            view.setFieldName(row.getName());
            view.setEnabledState(row.isEnabled(), row.isEnabledEditable(), getEnabledNote(row));
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

    private void onEnabledChange() {
        if (canEdit()) {
            model.setEnabled(row.getName(), view.isEnabledTicked());
            fireChanged(row.getName());
        }
    }

    private static boolean isDenseVector(final ElasticFieldRow row) {
        return row != null
               && row.getKind() == Kind.MAPPED
               && !row.hasTypeConflict()
               && row.getField().getFldType() == FieldType.DENSE_VECTOR;
    }

    private void showDenseVector() {
        final boolean denseVector = isDenseVector(row);
        view.setDenseVectorVisible(denseVector);
        if (denseVector) {
            final ElasticDenseVectorFormValues values = ElasticDenseVectorFormValues.fromSettings(
                    NullSafe.get(row.getSettings(), ElasticIndexFieldSettings::getDenseVector));
            embeddingModel.setSelectedEntityReference(values.getEmbeddingModelRef(), true);
            rerankModel.setSelectedEntityReference(values.getRerankModelRef(), true);

            final List<String> textFields = new ArrayList<>(model.getRerankTextFieldNames());
            if (values.getRerankTextField() != null && !textFields.contains(values.getRerankTextField())) {
                // A stored choice is kept, even if the field has gone from the mapping
                textFields.add(values.getRerankTextField());
            }
            view.setRerankTextFieldOptions(textFields);
            view.setDenseVectorValues(values.getNearestNeighbourCount(),
                    values.getRerankBatchSize(),
                    values.getRerankTextField(),
                    values.getRerankScoreField(),
                    ElasticFieldStatsSupport.formatDecimal(values.getRerankScoreMinimum(), 3));
        }
    }

    // Writes the vector search settings to the field's settings, unless the minimum rerank score
    // isn't a score: then nothing is written, but the index is still told, as input it can't keep is
    // a change (Save is enabled, and refuses, taking the user to the box, which says why)
    private void onDenseVectorChange() {
        if (canEdit() && isDenseVector(row)) {
            final Float scoreMinimum = view.getRerankScoreMinimum();
            if (scoreMinimum == null) {
                fireChanged(row.getName());
            } else {
                final ElasticDenseVectorFormValues values = new ElasticDenseVectorFormValues(
                        embeddingModel.getSelectedEntityReference(),
                        view.getNearestNeighbourCount(),
                        rerankModel.getSelectedEntityReference(),
                        view.getRerankBatchSize(),
                        view.getRerankTextField(),
                        view.getRerankScoreField(),
                        scoreMinimum);
                final ElasticIndexFieldSettings existing = model.findSettings(row.getName());
                final ElasticDenseVectorSettings existingDenseVector = NullSafe.get(existing,
                        ElasticIndexFieldSettings::getDenseVector);
                final ElasticIndexFieldSettings.Builder builder = existing == null
                        ? ElasticIndexFieldSettings.builder(row.getName())
                        : existing.copy();
                model.putSettings(builder.denseVector(values.toSettings(existingDenseVector)).build());
                fireChanged(row.getName());
            }
        }
    }

    private void showVirtual() {
        final boolean virtual = row.isVirtual();
        view.setVirtualVisible(virtual);
        if (!virtual) {
            groups = new ArrayList<>();
            return;
        }

        // Each field carries its query options and whether its values can be picked, so a group's
        // terms get the query options and value picker buttons, as a dashboard's do
        groupFields.clear();
        for (final String fieldName : model.getKeywordFieldNames()) {
            final ElasticIndexField field = model.findField(fieldName);
            groupFields.addItem(QueryField.builder()
                    .fldName(fieldName)
                    .fldType(FieldType.KEYWORD)
                    .conditionSet(ConditionSet.getElastic(FieldType.KEYWORD))
                    .queryable(true)
                    .queryOptionSet(NullSafe.get(field, ElasticIndexField::getQueryOptionSet))
                    .supportsTopValues(NullSafe.get(field, ElasticIndexField::getSupportsTopValues))
                    .build());
        }

        final int selected = view.getSelectedGroupIndex();
        groups = new ArrayList<>(NullSafe.list(NullSafe.get(row.getSettings(),
                ElasticIndexFieldSettings::getVirtualGroups)));
        view.setGroups(groups, groups.isEmpty()
                ? -1
                : Math.max(0, Math.min(selected, groups.size() - 1)));
    }

    private void onGroupAction(final GroupAction action) {
        if (updating || row == null || model == null || !row.isVirtual()) {
            return;
        }
        switch (action) {
            case ADD -> addGroup();
            case EDIT -> editGroup();
            case REMOVE -> removeGroup();
        }
    }

    private void addGroup() {
        if (canEdit()) {
            // The selected field may change while the dialog is open, so the group goes to the field
            // it was opened for
            final String fieldName = row.getName();
            final List<ElasticVirtualFieldGroup> groupsAtOpen = groups;
            groupEditorProvider.get().show(this, index, groupFields, groupsAtOpen, -1, false, group -> {
                final List<ElasticVirtualFieldGroup> newGroups = ElasticVirtualFieldGroups.add(groupsAtOpen,
                        group.getName(),
                        group.getExpression());
                applyGroups(fieldName, newGroups, newGroups.size() - 1);
            });
        }
    }

    private void editGroup() {
        final int groupIndex = view.getSelectedGroupIndex();
        if (groupIndex >= 0) {
            final boolean readOnly = isReadOnly();
            final String fieldName = row.getName();
            final List<ElasticVirtualFieldGroup> groupsAtOpen = groups;
            groupEditorProvider.get().show(this, index, groupFields, groupsAtOpen, groupIndex, readOnly, group ->
                    applyGroups(fieldName,
                            ElasticVirtualFieldGroups.replace(groupsAtOpen,
                                    groupIndex,
                                    group.getName(),
                                    group.getExpression()),
                            groupIndex));
        }
    }

    private void removeGroup() {
        final int groupIndex = view.getSelectedGroupIndex();
        if (canEdit() && groupIndex >= 0) {
            final List<ElasticVirtualFieldGroup> newGroups = ElasticVirtualFieldGroups.remove(groups, groupIndex);
            applyGroups(row.getName(), newGroups, Math.min(groupIndex, newGroups.size() - 1));
        }
    }

    // Sets a virtual field's groups, which may no longer be the field shown
    private void applyGroups(final String fieldName,
                             final List<ElasticVirtualFieldGroup> newGroups,
                             final int selectedIndex) {
        if (!model.setVirtualGroups(fieldName, newGroups)) {
            // The virtual field was deleted or renamed while its group was being edited
            return;
        }
        if (row != null && fieldName.equals(row.getName())) {
            groups = new ArrayList<>(newGroups);
            view.setGroups(groups, selectedIndex);
        }
        fireChanged(fieldName);
    }
}
