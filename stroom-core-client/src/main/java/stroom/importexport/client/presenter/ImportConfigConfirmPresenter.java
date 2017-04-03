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

package stroom.importexport.client.presenter;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.AlertCallback;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.ImportState;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.shared.ImportConfigAction;
import stroom.util.shared.Message;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.Severity;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

import java.util.ArrayList;
import java.util.List;

public class ImportConfigConfirmPresenter extends
        MyPresenter<ImportConfigConfirmPresenter.ImportConfigConfirmView, ImportConfigConfirmPresenter.ImportConfirmProxy>
        implements ImportConfigConfirmEvent.Handler, PopupUiHandlers {
    public interface ImportConfigConfirmView extends View {
        void setDataGridView(View view);
    }

    @ProxyCodeSplit
    public interface ImportConfirmProxy extends Proxy<ImportConfigConfirmPresenter> {
    }

    private final TooltipPresenter tooltipPresenter;
    private final DataGridView<ImportState> dataGridView;
    private final ClientDispatchAsync dispatcher;

    private ResourceKey resourceKey;
    private List<ImportState> confirmList;

    @Inject
    public ImportConfigConfirmPresenter(final EventBus eventBus, final ImportConfigConfirmView view,
                                        final ImportConfirmProxy proxy, final TooltipPresenter tooltipPresenter,
                                        final ClientDispatchAsync dispatcher) {
        super(eventBus, view, proxy);

        this.tooltipPresenter = tooltipPresenter;
        this.dispatcher = dispatcher;

        this.dataGridView = new DataGridViewImpl<ImportState>(false,
                DataGridViewImpl.MASSIVE_LIST_PAGE_SIZE);
        view.setDataGridView(dataGridView);

        addColumns();
    }

    @ProxyEvent
    @Override
    public void onConfirmImport(final ImportConfigConfirmEvent event) {
        resourceKey = event.getResourceKey();
        confirmList = event.getConfirmList();

        if (confirmList == null) {
            dataGridView.setRowCount(0);
        } else {
            dataGridView.setRowData(0, confirmList);
            dataGridView.setRowCount(confirmList.size());
        }
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Confirm Import", this);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        // Disable the popup ok/cancel buttons before we attempt import.
        DisablePopupEvent.fire(ImportConfigConfirmPresenter.this, ImportConfigConfirmPresenter.this);

        if (ok) {
            boolean warnings = false;
            for (final ImportState importState : confirmList) {
                if (importState.isAction() && importState.getSeverity().greaterThan(Severity.INFO)) {
                    warnings = true;
                }
            }

            if (warnings) {
                ConfirmEvent.fireWarn(ImportConfigConfirmPresenter.this,
                        "There are warnings in the items selected.  Are you sure you want to import?.",
                        new ConfirmCallback() {
                            @Override
                            public void onResult(final boolean result) {
                                if (result) {
                                    importData();
                                } else {
                                    // Re-enable popup buttons.
                                    EnablePopupEvent.fire(ImportConfigConfirmPresenter.this,
                                            ImportConfigConfirmPresenter.this);
                                }
                            }
                        });

            } else {
                importData();
            }
        } else {
            abortImport();
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    private void addColumns() {
        addSelectedColumn();
        addInfoColumn();
        addActionColumn();
        addTypeColumn();
        addSourcePathColumn();
        addDestPathColumn();
        dataGridView.addEndColumn(new EndColumn<ImportState>());
    }

    private void addSelectedColumn() {
        final TickBoxCell.MarginAppearance tickBoxAppearance = GWT.create(TickBoxCell.MarginAppearance.class);

        // Select Column
        final Column<ImportState, TickBoxState> column = new Column<ImportState, TickBoxState>(
                TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue(final ImportState object) {
                return TickBoxState.fromBoolean(object.isAction());
            }

        };
        final Header<TickBoxState> header = new Header<TickBoxState>(TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue() {
                return getHeaderState();
            }
        };
        dataGridView.addColumn(column, header, 15);

        // Add Handlers
        column.setFieldUpdater(new FieldUpdater<ImportState, TickBoxState>() {
            @Override
            public void update(final int index, final ImportState row, final TickBoxState value) {
                row.setAction(value.toBoolean());
            }
        });
        header.setUpdater(new ValueUpdater<TickBoxState>() {
            @Override
            public void update(final TickBoxState value) {
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
                    dataGridView.setRowData(0, confirmList);
                    dataGridView.setRowCount(confirmList.size());
                }
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

    protected void addInfoColumn() {
        // Info column.
        final InfoColumn<ImportState> infoColumn = new InfoColumn<ImportState>() {
            @Override
            public GlyphIcon getValue(final ImportState object) {
                if (object.getMessageList().size() > 0 || object.getUpdatedFieldList().size() > 0) {
                    final Severity severity = object.getSeverity();
                    switch (severity) {
                        case INFO:
                            return GlyphIcons.INFO;
                        case WARNING:
                            return GlyphIcons.ALERT;
                        case ERROR:
                            return GlyphIcons.ERROR;
                        default:
                            return GlyphIcons.ERROR;
                    }
                }
                return null;
            }

            @Override
            protected void showInfo(final ImportState action, final int x, final int y) {
                final StringBuilder builder = new StringBuilder();
                if (action.getMessageList().size() > 0) {
                    builder.append("<b>Messages:</b><br/>");
                    for (final Message msg : action.getMessageList()) {
                        builder.append(msg.getSeverity().getDisplayValue());
                        builder.append(": ");
                        builder.append(msg.getMessage());
                        builder.append("<br/>");
                    }
                    builder.append("<br/>");
                }

                if (action.getUpdatedFieldList().size() > 0) {
                    builder.append("<b>Fields Updated:</b><br/>");
                    for (final String string : action.getUpdatedFieldList()) {
                        builder.append(string);
                        builder.append("<br/>");
                    }
                    builder.append("<br/>");
                }
                tooltipPresenter.setHTML(builder.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(ImportConfigConfirmPresenter.this, tooltipPresenter, PopupType.POPUP, popupPosition,
                        null);
            }
        };
        dataGridView.addColumn(infoColumn, "<br/>", 18);
    }

    private void addActionColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getState().getDisplayValue();
            }
        };
        dataGridView.addResizableColumn(column, "Action", 50);
    }

    private void addTypeColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getDocRef().getType();
            }
        };
        dataGridView.addResizableColumn(column, "Type", 100);
    }

    private void addSourcePathColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getSourcePath();
            }
        };
        dataGridView.addResizableColumn(column, "Source Path", 300);
    }

    private void addDestPathColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getDestPath();
            }
        };
        dataGridView.addResizableColumn(column, "Destination Path", 300);
    }

    public void abortImport() {
        // Abort ... set the confirm list to blank
        dispatcher.execute(new ImportConfigAction(resourceKey, new ArrayList<ImportState>()),
                new AsyncCallbackAdaptor<ResourceKey>() {
                    @Override
                    public void onSuccess(final ResourceKey result2) {
                        AlertEvent.fireWarn(ImportConfigConfirmPresenter.this, "Import Aborted", new AlertCallback() {
                            @Override
                            public void onClose() {
                                HidePopupEvent.fire(ImportConfigConfirmPresenter.this,
                                        ImportConfigConfirmPresenter.this, false, false);
                            }
                        });
                    }
                });
    }

    public void importData() {
        dispatcher.execute(new ImportConfigAction(resourceKey, confirmList),

                new AsyncCallbackAdaptor<ResourceKey>() {
                    @Override
                    public void onSuccess(final ResourceKey result2) {
                        AlertEvent.fireInfo(ImportConfigConfirmPresenter.this, "Import Complete", new AlertCallback() {
                            @Override
                            public void onClose() {
                                HidePopupEvent.fire(ImportConfigConfirmPresenter.this, ImportConfigConfirmPresenter.this, false,
                                        true);
                                RefreshExplorerTreeEvent.fire(ImportConfigConfirmPresenter.this);

                                // We might have loaded a new visualisation or updated
                                // an existing one.
                                clearCaches();
                            }
                        });
                    }

                    @Override
                    public void onFailure(final Throwable caught) {
                        HidePopupEvent.fire(ImportConfigConfirmPresenter.this, ImportConfigConfirmPresenter.this, false, true);
                        // Even if the import was error we should refresh the tree in
                        // case it got part done.
                        RefreshExplorerTreeEvent.fire(ImportConfigConfirmPresenter.this);

                        // We might have loaded a new visualisation or updated an
                        // existing one.
                        clearCaches();
                    }
                });
    }

    private void clearCaches() {
        // TODO : Add cache clearing functionality.

        // ClearScriptCacheEvent.fire(this);
        // ClearFunctionCacheEvent.fire(this);
    }
}
