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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.ExportFileCompleteHandler;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.explorer.client.presenter.EntityCheckTreePresenter;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.shared.ExportConfigAction;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tickbox.client.view.TickBox;

import java.util.Set;

public class ExportConfigPresenter
        extends MyPresenter<ExportConfigPresenter.ExportConfigView, ExportConfigPresenter.ExportProxy>
        implements ExportConfigEvent.Handler {
    private final EntityCheckTreePresenter folderCheckTreePresenter;
    private final ClientDispatchAsync clientDispatchAsync;

    @Inject
    public ExportConfigPresenter(final EventBus eventBus, final ExportConfigView view, final ExportProxy proxy,
                                 final EntityCheckTreePresenter folderCheckTreePresenter, final ClientDispatchAsync clientDispatchAsync) {
        super(eventBus, view, proxy);
        this.folderCheckTreePresenter = folderCheckTreePresenter;
        this.clientDispatchAsync = clientDispatchAsync;
        view.setFolderView(folderCheckTreePresenter.getView());
    }

    @ProxyEvent
    @Override
    public void onExport(final ExportConfigEvent event) {
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        folderCheckTreePresenter.setIncludedTypes(Folder.ENTITY_TYPE);
        folderCheckTreePresenter.setRequiredPermissions(DocumentPermissionNames.READ);
        folderCheckTreePresenter.refresh();

        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    export();
                } else {
                    HidePopupEvent.fire(ExportConfigPresenter.this, ExportConfigPresenter.this, false, false);
                }
            }
        };

        final PopupSize popupSize = new PopupSize(350, 400, 350, 350, 2000, 2000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Export", popupUiHandlers);
    }

    private void export() {
        // Disable the popup ok/cancel buttons before we attempt export.
        DisablePopupEvent.fire(this, this);

        final Set<ExplorerData> dataItems = folderCheckTreePresenter.getSelectionModel().getSelectedSet();
        if (dataItems == null || dataItems.size() == 0) {
            // Let the user know that they didn't select anything to export.
            AlertEvent.fireWarn(this, "No folders have been selected for export", null);
            // Re-enable buttons on this popup.
            EnablePopupEvent.fire(this, this);

        } else {
            final FindFolderCriteria criteria = new FindFolderCriteria();
            for (final ExplorerData dataItem : dataItems) {
                if (dataItem instanceof EntityData) {
                    final EntityData entityData = (EntityData) dataItem;
                    if (Folder.ENTITY_TYPE.equals(entityData.getType())) {
                        criteria.getFolderIdSet().add(entityData.getDocRef().getId());
                    }
                } else {
                    // It must be the root folder that is selected
                    criteria.getFolderIdSet().setMatchNull(Boolean.TRUE);
                }
            }
            criteria.getFolderIdSet().setGlobal(false);
            criteria.getFolderIdSet().setDeep(true);

            clientDispatchAsync.execute(new ExportConfigAction(criteria, getView().getIgnoreErrors().getBooleanValue()),
                    new ExportFileCompleteHandler(this));
        }
    }

    public interface ExportConfigView extends View {
        void setFolderView(View view);

        TickBox getIgnoreErrors();
    }

    @ProxyCodeSplit
    public interface ExportProxy extends Proxy<ExportConfigPresenter> {
    }
}
