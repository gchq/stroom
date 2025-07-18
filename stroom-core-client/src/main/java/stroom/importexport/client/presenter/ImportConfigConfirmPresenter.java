/*
 * Copyright 2016-2024 Crown Copyright
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

package stroom.importexport.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.Severity;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ImportConfigConfirmPresenter extends
        MyPresenter<ImportConfigConfirmPresenter.ImportConfigConfirmView,
                ImportConfigConfirmPresenter.ImportConfirmProxy>
        implements ImportConfigConfirmEvent.Handler, HidePopupRequestEvent.Handler {

    private static final ContentResource CONTENT_RESOURCE =
            com.google.gwt.core.client.GWT.create(ContentResource.class);

    private final TooltipPresenter tooltipPresenter;
    private final ImportConfigConfirmView view;
    private final MyDataGrid<ImportState> dataGrid;
    private final RestFactory restFactory;
    private ResourceKey resourceKey;
    private List<ImportState> confirmList = new ArrayList<>();
    private final DocSelectionBoxPresenter rootFolderPresenter;

    private final ImportSettings.Builder importSettingsBuilder = ImportSettings.builder();

    @Inject
    public ImportConfigConfirmPresenter(final EventBus eventBus,
                                        final ImportConfigConfirmView view,
                                        final ImportConfirmProxy proxy,
                                        final TooltipPresenter tooltipPresenter,
                                        final DocSelectionBoxPresenter rootFolderPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view, proxy);
        this.rootFolderPresenter = rootFolderPresenter;

        rootFolderPresenter.setSelectedEntityReference(ExplorerConstants.SYSTEM_DOC_REF, false);
        rootFolderPresenter.setIncludedTypes(ExplorerConstants.FOLDER_TYPE);
        rootFolderPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        rootFolderPresenter.setAllowFolderSelection(true);

        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;

        this.view = view;

        dataGrid = new MyDataGrid<>(this, MyDataGrid.MASSIVE_LIST_PAGE_SIZE);

        view.setDataGrid(dataGrid);
        view.setRootFolderView(rootFolderPresenter.getView());
        view.setEnableFilters(false);
        view.setEnableFromDate(System.currentTimeMillis());
        view.setUseImportNames(false);
        view.setUseImportFolders(false);
        view.onUseImportNames(useImportNames -> {
            importSettingsBuilder.useImportNames(useImportNames);
            refresh();
        });
        view.onUseImportFolders(useImportFolders -> {
            importSettingsBuilder.useImportFolders(useImportFolders);
            refresh();
        });

        addColumns();
    }

    private void setRootDocRef(final DocRef rootDocRef) {
        importSettingsBuilder.rootDocRef(rootDocRef);
        refresh();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(rootFolderPresenter.addDataSelectionHandler(event -> {
            if (event.getSelectedItem() != null &&
                event.getSelectedItem().compareTo(ExplorerConstants.SYSTEM_DOC_REF) != 0 &&
                event.getSelectedItem().getUuid().length() > 1) {
                setRootDocRef(event.getSelectedItem());
            } else {
                setRootDocRef(null);
            }
        }));
    }

    @ProxyEvent
    @Override
    public void onConfirmImport(final ImportConfigConfirmEvent event) {
        resourceKey = event.getResponse().getResourceKey();
        confirmList = event.getResponse().getConfirmList();
        updateList();
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = PopupSize.resizable(
                1000, 800,
                380, 480);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Confirm Import")
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .fire();
    }

    public void refresh() {
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
                .taskMonitorFactory(this)
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

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            boolean warnings = false;
            int count = 0;
            importSettingsBuilder.enableFilters(getView().isEnableFilters());
            importSettingsBuilder.enableFiltersFromTime(getView().getEnableFromDate());
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
                        ImportConfigConfirmPresenter.this,
                        "No items are selected for import", e::reset);
            } else if (warnings) {
                ConfirmEvent.fireWarn(ImportConfigConfirmPresenter.this,
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
        } else {
            abortImport(e);
        }
    }

    private void addColumns() {
        addSelectedColumn();
        addInfoColumn();
        addActionColumn();
        addIconColumn();
        addTypeColumn();
        addSourcePathColumn();
        addDestPathColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addIconColumn() {
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, this::getDocTypeIcon)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("")
                        .withToolTip("Document type")
                        .build(),
                ColumnSizeConstants.ICON_COL);
    }

    private void addSelectedColumn() {

        final Header<TickBoxState> tickBoxHeader = buildTickBoxHeader();
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder((final ImportState importState) -> {
                            final Severity severity = importState.getSeverity();
                            if (severity != null && severity.greaterThanOrEqual(Severity.ERROR)) {
                                return null;
                            }
                            return TickBoxState.fromBoolean(importState.isAction());
                        })
                        .centerAligned()
                        .withFieldUpdater((idx, importState, tickBoxState) -> {
                            importState.setAction(tickBoxState.toBoolean());
                        })
                        .build(),
                tickBoxHeader,
                ColumnSizeConstants.CHECKBOX_COL);
    }

    private Header<TickBoxState> buildTickBoxHeader() {
        //noinspection Convert2Diamond // Cos GWT
        final Header<TickBoxState> tickBoxHeader = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                return getHeaderState();
            }
        };

        tickBoxHeader.setUpdater(value -> {
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
        return tickBoxHeader;
    }

    private TickBoxState getHeaderState() {
        final TickBoxState state;
        if (NullSafe.hasItems(confirmList)) {
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
        } else {
            state = TickBoxState.UNTICK;
        }
        return state;
    }

    private Preset createInfoColSvgPreset(final Severity severity) {
        final String title = "Click to see " + severity.getSummaryValue()
                .toLowerCase();

        final Preset svgPreset = switch (severity) {
            case INFO -> SvgPresets.INFO;
            case WARNING -> SvgPresets.ALERT;
            default -> SvgPresets.ERROR;
        };
        return svgPreset.title(title);
    }

    protected void addInfoColumn() {
        // Info column.
        final InfoColumn<ImportState> infoColumn = new InfoColumn<ImportState>() {
            @Override
            public Preset getValue(final ImportState object) {
                if (!object.getMessageList().isEmpty() || !object.getUpdatedFieldList().isEmpty()) {
                    final Severity severity = object.getSeverity();
                    return createInfoColSvgPreset(severity);
                } else {
                    return null;
                }
            }

            @Override
            protected void showInfo(final ImportState action, final PopupPosition popupPosition) {
                final HtmlBuilder htmlBuilder = new HtmlBuilder();
                if (!action.getMessageList().isEmpty()) {

                    final TableBuilder tb = new TableBuilder();
                    tb.row(TableCell.header("Messages", 2));
                    for (final Message msg : action.getMessageList()) {
                        tb.row(msg.getSeverity().getDisplayValue(), msg.getMessage());
                    }
                    htmlBuilder.div(tb::write, Attribute.className("infoTable"));
                }

                if (!action.getUpdatedFieldList().isEmpty()) {
                    if (!action.getMessageList().isEmpty()) {
                        htmlBuilder.br();
                    }

                    final TableBuilder tb = new TableBuilder();
                    tb.row(TableCell.header("Fields Updated"));
                    action.getUpdatedFieldList().forEach(tb::row);
                    htmlBuilder.div(tb::write, Attribute.className("infoTable"));
                }
                tooltipPresenter.show(htmlBuilder.toSafeHtml(), popupPosition);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addActionColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((ImportState importState) ->
                                NullSafe.getOrElse(importState,
                                        ImportState::getState,
                                        State::getDisplayValue,
                                        "Error"))
                        .build(),
                "Action",
                50);
    }

    private void addTypeColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((ImportState importState) ->
                                importState.getDocRef().getType())
                        .build(),
                "Type",
                200);
    }

    private void addSourcePathColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(ImportState::getSourcePath)
                        .build(),
                "Source Path",
                320);
    }

    private void addDestPathColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(ImportState::getDestPath)
                        .build(),
                "Destination Path",
                320);
    }

    public void abortImport(final HidePopupRequestEvent e) {
        // Abort ... use a blank confirm list to perform an import that imports nothing.
        importSettingsBuilder.importMode(ImportMode.ACTION_CONFIRMATION);
        importSettingsBuilder.useImportNames(false);
        importSettingsBuilder.useImportFolders(false);
        importSettingsBuilder.enableFilters(false);

        restFactory
                .create(CONTENT_RESOURCE)
                .method(res -> res.importContent(new ImportConfigRequest(resourceKey,
                        importSettingsBuilder.build(),
                        new ArrayList<>())))
                .onSuccess(result2 -> AlertEvent.fireWarn(ImportConfigConfirmPresenter.this,
                        "Import Aborted",
                        e::hide))
                .onFailure(caught -> AlertEvent.fireError(ImportConfigConfirmPresenter.this,
                        caught.getMessage(),
                        e::hide))
                .taskMonitorFactory(this)
                .exec();
    }

    public void importData(final HidePopupRequestEvent e) {
        importSettingsBuilder.importMode(ImportMode.ACTION_CONFIRMATION);
        restFactory
                .create(CONTENT_RESOURCE)
                .method(res -> res.importContent(new ImportConfigRequest(resourceKey,
                        importSettingsBuilder.build(),
                        confirmList)))
                .onSuccess(result2 ->
                        AlertEvent.fireInfo(
                                ImportConfigConfirmPresenter.this,
                                "Import Complete", () -> {
                                    e.hide();
                                    RefreshExplorerTreeEvent.fire(ImportConfigConfirmPresenter.this);

                                    // We might have loaded a new visualisation or updated
                                    // an existing one.
                                    clearCaches();
                                }))
                .onFailure(caught -> {
                    e.hide();
                    // Even if the import was error we should refresh the tree in
                    // case it got part done.
                    RefreshExplorerTreeEvent.fire(ImportConfigConfirmPresenter.this);

                    // We might have loaded a new visualisation or updated an
                    // existing one.
                    clearCaches();
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private Preset getDocTypeIcon(final ImportState importState) {
        final DocRef docRef = NullSafe.get(importState, ImportState::getDocRef);
        if (docRef != null) {
            final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
            return NullSafe.get(
                    documentType,
                    dt -> new Preset(dt.getIcon(), dt.getDisplayType(), true));
        } else {
            return null;
        }
    }

    private void clearCaches() {
        // TODO : Add cache clearing functionality.
        // ClearScriptCacheEvent.fire(this);
        // ClearFunctionCacheEvent.fire(this);
    }


    // --------------------------------------------------------------------------------


    public interface ImportConfigConfirmView extends View, Focus {

        void setDataGrid(Widget widget);

        Long getEnableFromDate();

        void setEnableFromDate(Long date);

        boolean isEnableFilters();

        void setEnableFilters(boolean enableFilters);

        void setUseImportNames(boolean useImportNames);

        void onUseImportNames(Consumer<Boolean> consumer);

        void setUseImportFolders(boolean useImportFolders);

        void onUseImportFolders(Consumer<Boolean> consumer);

        void setRootFolderView(View view);

        Widget getDataGridViewWidget();
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface ImportConfirmProxy extends Proxy<ImportConfigConfirmPresenter> {

    }
}
