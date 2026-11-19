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

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.search.elastic.client.component.ElasticIndexFieldList.FieldAction;
import stroom.search.elastic.client.model.ElasticFieldListModel;
import stroom.search.elastic.client.model.ElasticFieldListModel.SortColumn;
import stroom.search.elastic.client.model.ElasticFieldRow;
import stroom.search.elastic.client.model.ElasticFieldRow.Kind;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.NullSafe;
import stroom.widget.chooser.client.MultiChooserController;
import stroom.widget.chooser.client.OpenChooser;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// An Elastic index's Fields tab ([ElasticIndexFieldList]): a tree of the index's fields above the
/// selected field's Info ([ElasticFieldInfoController]) and Settings
/// ([ElasticFieldSettingsController]) panels. Every change is to the index as edited, which is then
/// changed; nothing is sent to the server until it is saved.
public class ElasticIndexFieldListController extends Controller {

    /// The panel showing how the field is mapped and statistics of its values.
    static final TabData INFO_TAB = new TabDataImpl("Info");
    /// The panel for changing the field's settings.
    static final TabData SETTINGS_TAB = new TabDataImpl("Settings");

    private static final ElasticIndexResource ELASTIC_INDEX_RESOURCE = GWT.create(ElasticIndexResource.class);
    private static final String ALL_TYPES = "All types";

    private final ElasticIndexFieldList view;
    private final RestFactory restFactory;
    private final ElasticFieldInfoController info;
    private final ElasticFieldSettingsController settings;
    private final ControllerLayer infoLayer;
    private final ControllerLayer settingsLayer;
    private final Provider<ElasticVirtualFieldNameController> nameDialogProvider;
    private final Provider<MultiChooserController<String>> typeChooserProvider;
    private final OpenChooser openChooser = new OpenChooser();
    private final ElasticFieldListModel model = new ElasticFieldListModel();
    private final Set<String> expandedNames = new HashSet<>();

    private DocRef docRef;
    private ElasticIndexDoc document;
    private Supplier<ElasticIndexDoc> currentDocSupplier;
    private String nameFilter = "";
    private List<String> typeFilter = new ArrayList<>();
    private SortColumn sortColumn = SortColumn.NAME;
    private boolean sortAscending = true;
    private String selectedName;
    private Kind selectedKind;
    private TabData selectedTab = INFO_TAB;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus            The application's event bus.
    /// @param view                The tab.
    /// @param restFactory         Reads the fields from the index's mapping.
    /// @param infoProvider        Makes the Info panel.
    /// @param settingsProvider    Makes the Settings panel.
    /// @param nameDialogProvider  Makes the dialog naming a virtual field.
    /// @param typeChooserProvider Makes the type filter's chooser.
    @Inject
    public ElasticIndexFieldListController(final EventBus eventBus,
                                           final ElasticIndexFieldList view,
                                           final RestFactory restFactory,
                                           final Provider<ElasticFieldInfoController> infoProvider,
                                           final Provider<ElasticFieldSettingsController> settingsProvider,
                                           final Provider<ElasticVirtualFieldNameController> nameDialogProvider,
                                           final Provider<MultiChooserController<String>> typeChooserProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.nameDialogProvider = nameDialogProvider;
        this.typeChooserProvider = typeChooserProvider;
        initWidget(view);

        info = make(infoProvider);
        settings = make(settingsProvider);
        infoLayer = new ControllerLayer(info);
        settingsLayer = new ControllerLayer(settings);
        settings.setModel(model);
        settings.setOnChange(fieldName -> {
            refreshRows();
            updateActions();
            markChanged();
        });

        view.addDetailsTab(INFO_TAB);
        view.addDetailsTab(SETTINGS_TAB);
        view.setOnDetailsTabSelected(this::showTab);
        view.setOnSelect(this::onSelect);
        view.setOnAction(this::onAction);
        view.setEnabledEditable(ElasticFieldRow::isEnabledEditable);
        view.setOnEnabledTicked(this::onEnabledTicked);
        view.setOnExpand((row, expand) -> {
            if (expand) {
                expandedNames.add(row.getName());
            } else {
                expandedNames.remove(row.getName());
            }
            refreshRows();
        });
        view.setOnSort((column, ascending) -> {
            sortColumn = column;
            sortAscending = ascending;
            refreshRows();
        });
        view.setOnNameFilterChange(text -> {
            nameFilter = text;
            refreshRows();
        });
        view.setOnOpenTypeFilter(this::openTypeFilter);
        view.setTypeFilterText(ALL_TYPES);
        view.setDetailsVisible(false);
        watch(Contexts.READ_ONLY, readOnly -> {
            view.setReadOnly(readOnly);
            updateActions();
        });
    }

    /// Sets what gives the index as edited across all of its tabs, so that Refresh reads the fields
    /// with any unsaved cluster or index name.
    ///
    /// @param currentDocSupplier Gives the index with all unsaved changes.
    public void setCurrentDocSupplier(final Supplier<ElasticIndexDoc> currentDocSupplier) {
        this.currentDocSupplier = currentDocSupplier;
    }

    /// Shows an index's fields.
    ///
    /// @param docRef   The index's reference.
    /// @param document The index.
    public void read(final DocRef docRef, final ElasticIndexDoc document) {
        this.docRef = docRef;
        this.document = document;
        view.setIndexName(docRef.getName());
        model.read(document.getFields(), document.getFieldSettings());
        model.setMandatoryFieldNames(document.getMandatoryFieldNames());
        info.setIndex(docRef);
        settings.setIndex(docRef);
        refreshRows();
        showSelectedField();
        updateActions();
    }

    /// Writes the fields' settings into a copy of an index.
    ///
    /// @param document The index.
    /// @return A copy of it with the settings shown.
    public ElasticIndexDoc write(final ElasticIndexDoc document) {
        return document.copy()
                .fieldSettings(model.getSettingsList())
                .build();
    }

    private boolean isReadOnly() {
        return context(Contexts.READ_ONLY);
    }

    private void showTab(final TabData tab) {
        selectedTab = tab;
        view.showDetailsTab(tab, tab == INFO_TAB
                ? infoLayer
                : settingsLayer);
        info.setActive(getSelectedRow() != null && tab == INFO_TAB);
    }

    private void onSelect(final ElasticFieldRow row) {
        final String name = NullSafe.get(row, ElasticFieldRow::getName);
        final Kind kind = NullSafe.get(row, ElasticFieldRow::getKind);
        if (row == null || !name.equals(selectedName) || kind != selectedKind) {
            selectedName = name;
            selectedKind = kind;
            showSelectedField();
        }
        updateActions();
    }

    private void onEnabledTicked(final ElasticFieldRow row, final boolean enabled) {
        if (!isReadOnly() && row.isEnabledEditable()) {
            model.setEnabled(row.getName(), enabled);
            refreshRows();
            showSelectedField();
            updateActions();
            markChanged();
        } else {
            // Drawn again, to put back the tick the cell has already changed
            refreshRows();
        }
    }

    private void onAction(final FieldAction action) {
        switch (action) {
            case ADD_VIRTUAL_FIELD -> addVirtualField();
            case RENAME -> renameSelected();
            case REMOVE -> removeSelected();
            case REFRESH -> refreshFields();
            case EXPAND_ALL -> {
                expandedNames.addAll(model.getExpandableNames());
                refreshRows();
            }
            case COLLAPSE_ALL -> {
                expandedNames.clear();
                refreshRows();
            }
        }
    }

    private List<ElasticFieldRow> refreshRows() {
        final List<ElasticFieldRow> rows = model.buildRows(nameFilter,
                typeFilter,
                sortColumn,
                sortAscending,
                expandedNames);
        view.setRows(rows, getSelectedRow());
        return rows;
    }

    private ElasticFieldRow getSelectedRow() {
        return model.findRow(selectedName, selectedKind);
    }

    private void showSelectedField() {
        ElasticFieldRow row = getSelectedRow();
        if (row == null && selectedName != null) {
            // The field may have changed kind, e.g. a mapped field gone from the mapping
            row = model.findRow(selectedName);
            selectedKind = NullSafe.get(row, ElasticFieldRow::getKind);
            if (row == null) {
                selectedName = null;
            }
        }

        view.setDetailsVisible(row != null);
        settings.setField(row);
        info.setField(row, this::getSelectedVirtualGroups);
        showTab(selectedTab);
    }

    private List<ElasticVirtualFieldGroup> getSelectedVirtualGroups() {
        return NullSafe.get(model.findSettings(selectedName), ElasticIndexFieldSettings::getVirtualGroups);
    }

    private void updateActions() {
        final boolean readOnly = isReadOnly();
        final ElasticFieldRow row = getSelectedRow();
        final Set<FieldAction> actions = EnumSet.of(FieldAction.REFRESH,
                FieldAction.EXPAND_ALL,
                FieldAction.COLLAPSE_ALL);
        final boolean virtual = row != null && row.isVirtual();
        if (!readOnly) {
            actions.add(FieldAction.ADD_VIRTUAL_FIELD);
            if (virtual) {
                actions.add(FieldAction.RENAME);
                actions.add(FieldAction.REMOVE);
            } else if (row != null && model.hasSettings(row.getName())) {
                actions.add(FieldAction.REMOVE);
            }
        }
        view.setAvailableActions(actions, virtual);
    }

    private void addVirtualField() {
        if (!isReadOnly()) {
            nameDialogProvider.get().show(this,
                    "New Virtual Field",
                    null,
                    name -> model.validateVirtualFieldName(null, name),
                    name -> {
                        if (model.addVirtualField(name)) {
                            selectedTab = SETTINGS_TAB;
                            revealVirtualField(name);
                            markChanged();
                        }
                    });
        }
    }

    private void renameSelected() {
        final ElasticFieldRow row = getSelectedRow();
        if (!isReadOnly() && row != null && row.isVirtual()) {
            final String oldName = row.getName();
            nameDialogProvider.get().show(this,
                    "Rename Virtual Field",
                    oldName,
                    name -> model.validateVirtualFieldRename(oldName, name),
                    newName -> {
                        if (model.renameVirtualField(oldName, newName)) {
                            // A renamed field can't clash with a mapped field, so is a plain virtual field
                            revealVirtualField(newName);
                            markChanged();
                        }
                    });
        }
    }

    // Selects a virtual field and scrolls to it, clearing the filters if they hide it
    private void revealVirtualField(final String name) {
        selectedName = name;
        selectedKind = Kind.VIRTUAL;
        List<ElasticFieldRow> rows = refreshRows();
        if (!rows.contains(getSelectedRow())) {
            nameFilter = "";
            typeFilter = new ArrayList<>();
            view.clearNameFilter();
            view.setTypeFilterText(ALL_TYPES);
            rows = refreshRows();
        }
        showSelectedField();
        updateActions();
        view.scrollToRow(rows.indexOf(getSelectedRow()));
    }

    private void removeSelected() {
        final ElasticFieldRow row = getSelectedRow();
        if (!isReadOnly() && row != null) {
            if (row.isVirtual()) {
                ConfirmEvent.fire(this,
                        SafeHtmlUtils.fromString("Are you sure you want to delete the virtual field '" +
                                                 row.getName() + "'?"),
                        ok -> {
                            if (ok) {
                                removeSettings(row);
                            }
                        });
            } else {
                removeSettings(row);
            }
        }
    }

    private void removeSettings(final ElasticFieldRow row) {
        model.removeSettings(row.getName());
        if (row.getKind() != Kind.MAPPED) {
            // Virtual fields and missing fields only exist through their settings
            selectedName = null;
            selectedKind = null;
        }
        refreshRows();
        showSelectedField();
        updateActions();
        markChanged();
    }

    // Reads the fields again from the index's mapping, with any unsaved cluster or index name
    private void refreshFields() {
        final ElasticIndexDoc currentDoc = currentDocSupplier != null
                ? currentDocSupplier.get()
                : NullSafe.get(document, this::write);
        if (currentDoc != null) {
            restFactory
                    .create(ELASTIC_INDEX_RESOURCE)
                    .method(resource -> resource.fetchFields(currentDoc))
                    .onSuccess(fields -> {
                        final List<ElasticIndexField> fieldList = NullSafe.list(fields);
                        model.setFields(fieldList);
                        refreshRows();
                        showSelectedField();
                        updateActions();
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }

    // The type filter: a chooser of the types the fields have, ticking none showing all of them
    private void openTypeFilter() {
        final Element element = view.getTypeFilterElement();
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final List<String> types = model.getTypeFilterOptions();
        final MultiChooserController<String> chooser = typeChooserProvider.get();
        chooser.setDataSupplier((filter, consumer) -> consumer.accept(types.stream()
                .filter(type -> NullSafe.isBlankString(filter)
                                || type.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList())));
        chooser.setDisplayValueFunction(SafeHtmlUtils::fromString);
        chooser.setAccessibleName("Field types");
        chooser.setSelectedItems(typeFilter);
        chooser.show(this, element, "Choose Field Types", selected -> {
            typeFilter = new ArrayList<>(selected);
            view.setTypeFilterText(typeFilter.isEmpty()
                    ? ALL_TYPES
                    : String.join(", ", typeFilter));
            refreshRows();
        });
        openChooser.opened(chooser, element);
    }
}
