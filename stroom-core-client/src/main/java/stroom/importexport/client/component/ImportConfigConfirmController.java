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

package stroom.importexport.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.CellIcon;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportConfigResponse;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.Severity;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Confirms an import ([ImportConfigConfirm]), in a dialog made for one use: what the import will do
/// to each item, which can be ticked or unticked, and the import's settings. OK imports the ticked
/// items; Cancel aborts the import.
public class ImportConfigConfirmController extends Controller {

    private static final ContentResource CONTENT_RESOURCE =
            com.google.gwt.core.client.GWT.create(ContentResource.class);

    private final TooltipPresenter tooltipPresenter;
    private final ImportConfigConfirm view;
    private final DataGrid<ImportState> dataGrid;
    private final RestFactory restFactory;
    private ResourceKey resourceKey;
    private List<ImportState> confirmList = new ArrayList<>();

    private final ImportSettings.Builder importSettingsBuilder = ImportSettings.builder();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param tooltipPresenter              Shows an item's messages.
    /// @param docSelectionProvider          Makes the root folder picker's controller.
    /// @param restFactory                   Refreshes, makes and aborts the import.
    /// @param dateTimeBoxControllerProvider Makes the Process From box's controller.
    @Inject
    public ImportConfigConfirmController(final EventBus eventBus,
                                         final ImportConfigConfirm view,
                                         final TooltipPresenter tooltipPresenter,
                                         final Provider<DocSelectionController> docSelectionProvider,
                                         final RestFactory restFactory,
                                         final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        this.tooltipPresenter = own(tooltipPresenter);
        this.restFactory = restFactory;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getEnableFromBox());

        final DocSelectionController rootFolder = make(docSelectionProvider);
        rootFolder.attach(view.getRootFolderBox());
        rootFolder.setSelectedEntityReference(ExplorerConstants.SYSTEM_DOC_REF, false);
        rootFolder.setIncludedTypes(ExplorerConstants.FOLDER_TYPE);
        rootFolder.setRequiredPermissions(DocumentPermission.VIEW);
        rootFolder.setAllowFolderSelection(true);

        dataGrid = view.getDataGrid();
        view.setEnableFromDate(System.currentTimeMillis());
        view.setOnUseImportNames(useImportNames -> {
            importSettingsBuilder.useImportNames(useImportNames);
            refresh();
        });
        view.setOnUseImportFolders(useImportFolders -> {
            importSettingsBuilder.useImportFolders(useImportFolders);
            refresh();
        });

        addColumns();

        own(rootFolder.addSelectionListener(selected -> {
            if (selected != null
                && selected.compareTo(ExplorerConstants.SYSTEM_DOC_REF) != 0
                && selected.getUuid().length() > 1) {
                setRootDocRef(selected);
            } else {
                setRootDocRef(null);
            }
        }));
    }

    /// Shows what an import will do in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param response The import's upload, with what it will do to each item.
    public void show(final HasControllerNode opener, final ImportConfigResponse response) {
        resourceKey = response.getResourceKey();
        confirmList = response.getConfirmList();
        updateList();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(1200, 800, 380, 480))
                .caption("Confirm Import")
                .onShow(e -> view.focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    private void setRootDocRef(final DocRef rootDocRef) {
        importSettingsBuilder.rootDocRef(rootDocRef);
        refresh();
    }

    private void refresh() {
        importSettingsBuilder.importMode(ImportMode.CREATE_CONFIRMATION);
        restFactory
                .create(CONTENT_RESOURCE)
                .method(res -> res.importContent(new ImportConfigRequest(resourceKey,
                        importSettingsBuilder.build(),
                        confirmList)))
                .onSuccess(result -> {
                    confirmList = result.getConfirmList();
                    if (confirmList.isEmpty()) {
                        warning("The import package contains nothing that can be imported into " +
                                "this version of Stroom.");
                    }
                    updateList();
                })
                .onFailure(caught -> error(caught.getMessage()))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void updateList() {
        if (confirmList == null) {
            dataGrid.setRowCount(0);
        } else {
            dataGrid.setRowData(0, confirmList);
            dataGrid.setRowCount(confirmList.size());
        }
    }

    private void warning(final String message) {
        AlertEvent.fireWarn(this, message, null);
    }

    private void error(final String message) {
        AlertEvent.fireError(this, message, null);
    }

    private void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            boolean warnings = false;
            int count = 0;
            importSettingsBuilder.enableFilters(view.isEnableFilters());
            importSettingsBuilder.enableFiltersFromTime(view.getEnableFromDate());
            for (final ImportState importState : confirmList) {
                if (importState.isAction()) {
                    count++;
                    if (importState.getSeverity().greaterThan(Severity.INFO)) {
                        warnings = true;
                    }
                }
            }

            if (count == 0) {
                // Re-enable popup buttons.
                AlertEvent.fireWarn(
                        this,
                        "No items are selected for import", e::reset);
            } else {
                final String ownedDocumentsMsg = checkOwnedDocuments();
                if (NullSafe.isNonEmptyString(ownedDocumentsMsg)) {
                    AlertEvent.fireWarn(this, ownedDocumentsMsg, e::reset);
                } else if (warnings) {
                    ConfirmEvent.fireWarn(this,
                            "There are warnings in the items selected.  Are you sure you want to import?.",
                            result -> {
                                if (result) {
                                    importData(e);
                                } else {
                                    // Re-enable popup buttons.
                                    e.reset();
                                }
                            });
                } else {
                    importData(e);
                }
            }
        } else {
            abortImport(e);
        }
    }

    private String checkOwnedDocuments() {
        //noinspection SimplifyStreamApiCallChains // Cos GWT
        final List<ImportState> selectedAndOwned = NullSafe.stream(confirmList)
                .filter(ImportState::isAction)
                .filter(importState -> importState.getState() != State.IGNORE)
                .filter(importState -> importState.getOwnerDocRef() != null)
                .collect(Collectors.toList());

        if (!selectedAndOwned.isEmpty()) {
            final Map<DocRef, ImportState> selectedDocRefToStateMap = NullSafe.stream(confirmList)
                    .collect(Collectors.toMap(ImportState::getDocRef, Function.identity()));

            for (final ImportState importState : selectedAndOwned) {
                final DocRef ownerDocRef = importState.getOwnerDocRef();
                final ImportState ownerImportState = selectedDocRefToStateMap.get(ownerDocRef);

                final DocRef ownedDocRef = importState.getDocRef();
                if (ownerImportState == null
                    || (!ownerImportState.isAction()
                        && (ownerImportState.getState() == State.NEW || ownerImportState.getState() == State.IGNORE))) {
                    return "You have selected " + docRefToString(ownedDocRef)
                           + " without selecting its parent document " + docRefToString(ownerDocRef)
                           + ". You must also select its parent.";
                } else if (ownerImportState.getSeverity().greaterThanOrEqual(Severity.ERROR)) {
                    return "You have selected " + docRefToString(ownedDocRef)
                           + " but its parent document " + docRefToString(ownerDocRef)
                           + " cannot be imported due to an error. You cannot import this document.";
                }
            }
        }
        return null;
    }

    private String docRefToString(final DocRef docRef) {
        if (docRef == null) {
            return "";
        } else {
            if (docRef.getName() != null) {
                return docRef.getType() + " '" + docRef.getName() + "' (" + docRef.getUuid() + ")";
            } else {
                return docRef.getType() + " " + docRef.getUuid();
            }
        }
    }

    private void addColumns() {
        addSelectedColumn();
        addInfoColumn();
        addActionColumn();
        addTypeColumn();
        addSourcePathColumn();
        addDestPathColumn();
    }

    private void addSelectedColumn() {

        // Select Column
        final Column<ImportState, TickBoxState> column = new Column<ImportState, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final ImportState object) {
                final Severity severity = object.getSeverity();
                if (severity != null && severity.greaterThanOrEqual(Severity.ERROR)) {
                    return null;
                }

                return TickBoxState.fromBoolean(object.isAction());
            }
        };
        final Header<TickBoxState> header = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                return getHeaderState();
            }
        };
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        column.setFieldUpdater((index, row, value) -> row.setAction(value.toBoolean()));
        header.setUpdater(value -> {
            if (confirmList != null) {
                if (value.equals(TickBoxState.UNTICK)) {
                    for (final ImportState item : confirmList) {
                        item.setAction(false);
                    }
                }
                if (value.equals(TickBoxState.TICK)) {
                    for (final ImportState item : confirmList) {
                        item.setAction(true);
                    }
                }
                // Refresh list
                dataGrid.setRowData(0, confirmList);
                dataGrid.setRowCount(confirmList.size());
            }
        });
    }

    private TickBoxState getHeaderState() {
        TickBoxState state = TickBoxState.UNTICK;

        if (confirmList != null && confirmList.size() > 0) {
            boolean allAction = true;
            boolean allNotAction = true;

            for (final ImportState item : confirmList) {
                if (item.isAction()) {
                    allNotAction = false;
                } else {
                    allAction = false;
                }
            }

            if (allAction) {
                state = TickBoxState.TICK;
            } else if (allNotAction) {
                state = TickBoxState.UNTICK;
            } else {
                state = TickBoxState.HALF_TICK;
            }
        }
        return state;

    }

    private CellIcon createInfoColIcon(final Severity severity) {

        final String title = "Click to see " + severity.getSummaryValue()
                .toLowerCase();

        final CellIcon cellIcon;
        switch (severity) {
            case INFO:
                cellIcon = CellIcon.of(SvgImage.INFO, "Info");
                break;
            case WARNING:
                cellIcon = CellIcon.of(SvgImage.ALERT, "Alert");
                break;
            default:
                cellIcon = CellIcon.of(SvgImage.ERROR, "Error");
        }
        return cellIcon.withName(title);
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<ImportState> infoColumn = new InfoColumn<ImportState>() {
            @Override
            public CellIcon getValue(final ImportState object) {
                if (object.getMessageList().size() > 0 || object.getUpdatedFieldList().size() > 0) {
                    final Severity severity = object.getSeverity();
                    return createInfoColIcon(severity);
                } else {
                    return null;
                }
            }

            @Override
            protected void showInfo(final ImportState action,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                final HtmlBuilder htmlBuilder = new HtmlBuilder();
                if (action.getMessageList().size() > 0) {

                    final TableBuilder tb = new TableBuilder();
                    tb.row(TableCell.header("Messages", 2));
                    for (final Message msg : action.getMessageList()) {
                        tb.row(msg.getSeverity().getDisplayValue(), msg.getMessage());
                    }
                    htmlBuilder.div(tb::write, Attribute.className("infoTable"));
                }

                if (action.getUpdatedFieldList().size() > 0) {
                    if (action.getMessageList().size() > 0) {
                        htmlBuilder.br();
                    }

                    final TableBuilder tb = new TableBuilder();
                    tb.row(TableCell.header("Fields Updated"));
                    action.getUpdatedFieldList().forEach(tb::row);
                    htmlBuilder.div(tb::write, Attribute.className("infoTable"));
                }
                tooltipPresenter.show(htmlBuilder.toSafeHtml(), popupPosition, anchor);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addActionColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                if (action.getState() != null) {
                    return action.getState().getDisplayValue();
                }
                return "Error";
            }
        };
        dataGrid.addResizableColumn(column, "Action", 50);
    }

    private void addTypeColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getDocRef().getType();
            }
        };
        dataGrid.addResizableColumn(column, "Type", 150);
    }

    private void addSourcePathColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getSourcePath();
            }
        };
        dataGrid.addResizableColumn(column, "Source Path", 450);
    }

    private void addDestPathColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getDestPath();
            }
        };
        dataGrid.addResizableColumn(column, "Destination Path", 450);
    }

    private void abortImport(final HidePopupRequestEvent e) {
        // Abort ... use a blank confirm list to perform an import that imports nothing.
        importSettingsBuilder.importMode(ImportMode.ACTION_CONFIRMATION);
        importSettingsBuilder.useImportNames(false);
        importSettingsBuilder.useImportFolders(false);
        importSettingsBuilder.enableFilters(false);

        restFactory
                .create(CONTENT_RESOURCE)
                .method(res -> {
                    res.abortImport(resourceKey);
                    return null;
                })
                .onSuccess(ignored -> AlertEvent.fireWarn(
                        this,
                        "Import Aborted",
                        e::hide))
                .onFailure(caught -> AlertEvent.fireError(
                        this,
                        caught.getMessage(),
                        e::hide))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void importData(final HidePopupRequestEvent e) {
        importSettingsBuilder.importMode(ImportMode.ACTION_CONFIRMATION);
        restFactory
                .create(CONTENT_RESOURCE)
                .method(res -> res.importContent(new ImportConfigRequest(resourceKey,
                        importSettingsBuilder.build(),
                        confirmList)))
                .onSuccess(result2 ->
                        AlertEvent.fireInfo(
                                this,
                                "Import Complete", () -> {
                                    e.hide();
                                    RefreshExplorerTreeEvent.fire(this);

                                    // We might have loaded a new visualisation or updated
                                    // an existing one.
                                    clearCaches();
                                }))
                .onFailure(caught -> {
                    e.hide();
                    // Even if the import was error we should refresh the tree in
                    // case it got part done.
                    RefreshExplorerTreeEvent.fire(this);

                    // We might have loaded a new visualisation or updated an
                    // existing one.
                    clearCaches();
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void clearCaches() {
        // TODO : Add cache clearing functionality.
        // ClearScriptCacheEvent.fire(this);
        // ClearFunctionCacheEvent.fire(this);
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
