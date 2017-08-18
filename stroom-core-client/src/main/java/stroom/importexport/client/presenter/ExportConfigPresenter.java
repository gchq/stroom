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
 *
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
import stroom.core.client.LocationManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.entity.shared.DocRefs;
import stroom.explorer.client.presenter.EntityCheckTreePresenter;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode;
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

import java.util.Set;

public class ExportConfigPresenter
        extends MyPresenter<ExportConfigPresenter.ExportConfigView, ExportConfigPresenter.ExportProxy>
        implements ExportConfigEvent.Handler {
    private final LocationManager locationManager;
    private final EntityCheckTreePresenter treePresenter;
    private final ClientDispatchAsync clientDispatchAsync;

    @Inject
    public ExportConfigPresenter(final EventBus eventBus, final ExportConfigView view, final ExportProxy proxy, final LocationManager locationManager,
                                 final EntityCheckTreePresenter treePresenter, final ClientDispatchAsync clientDispatchAsync) {
        super(eventBus, view, proxy);
        this.locationManager = locationManager;
        this.treePresenter = treePresenter;
        this.clientDispatchAsync = clientDispatchAsync;
        view.setTreeView(treePresenter.getView());
    }

    @ProxyEvent
    @Override
    public void onExport(final ExportConfigEvent event) {
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        treePresenter.setRequiredPermissions(DocumentPermissionNames.READ, DocumentPermissionNames.EXPORT);
        treePresenter.refresh();

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

        final Set<ExplorerNode> dataItems = treePresenter.getSelectionModel().getSelectedSet();
        if (dataItems == null || dataItems.size() == 0) {
            // Let the user know that they didn't select anything to export.
            AlertEvent.fireWarn(this, "No folders have been selected for export", null);
            // Re-enable buttons on this popup.
            EnablePopupEvent.fire(this, this);

        } else {
            final DocRefs docRefs = new DocRefs();
            for (final ExplorerNode explorerNode : dataItems) {
                docRefs.add(explorerNode.getDocRef());
            }

            clientDispatchAsync.exec(new ExportConfigAction(docRefs))
                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                    .onFailure(throwable -> ExportFileCompleteUtil.onFailure(this));
        }
    }

    public interface ExportConfigView extends View {
        void setTreeView(View view);
    }

    @ProxyCodeSplit
    public interface ExportProxy extends Proxy<ExportConfigPresenter> {
    }
}
