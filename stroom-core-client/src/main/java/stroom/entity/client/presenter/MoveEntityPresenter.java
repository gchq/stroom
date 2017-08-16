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

package stroom.entity.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.entity.client.event.MoveEntityEvent;
import stroom.entity.client.event.ShowMoveEntityDialogEvent;
import stroom.entity.shared.Folder;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.shared.ExplorerData;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class MoveEntityPresenter
        extends MyPresenter<MoveEntityPresenter.MoveEntityView, MoveEntityPresenter.MoveEntityProxy>
        implements ShowMoveEntityDialogEvent.Handler, PopupUiHandlers {
    private final EntityTreePresenter entityTreePresenter;
    private List<ExplorerData> explorerDataList;

    @Inject
    public MoveEntityPresenter(final EventBus eventBus, final MoveEntityView view, final MoveEntityProxy proxy,
                               final EntityTreePresenter entityTreePresenter) {
        super(eventBus, view, proxy);
        this.entityTreePresenter = entityTreePresenter;
        view.setFolderView(entityTreePresenter.getView());

        entityTreePresenter.setIncludedTypes(Folder.ENTITY_TYPE);
        entityTreePresenter.setRequiredPermissions(DocumentPermissionNames.USE, DocumentPermissionNames.READ);
    }

    @ProxyEvent
    @Override
    public void onMove(final ShowMoveEntityDialogEvent event) {
        getView().setPermissionInheritance(PermissionInheritance.INHERIT);

        this.explorerDataList = event.getExplorerDataList();

        entityTreePresenter.setSelectedItem(null);

        final ExplorerData firstChild = event.getExplorerDataList().get(0);
        entityTreePresenter.setSelectedItem(firstChild);
        entityTreePresenter.getModel().reset();
        entityTreePresenter.getModel().setEnsureVisible(firstChild);
        entityTreePresenter.getModel().refresh();

        forceReveal();
    }

    @Override
    protected void revealInParent() {
        String caption = "Move Multiple Items";
        if (explorerDataList.size() == 1) {
            caption = "Move " + explorerDataList.get(0).getDisplayValue();
        }

        final PopupSize popupSize = new PopupSize(350, 400, 350, 350, 2000, 2000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, this);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final ExplorerData folder = entityTreePresenter.getSelectedItem();
            MoveEntityEvent.fire(MoveEntityPresenter.this, MoveEntityPresenter.this, folder, explorerDataList, getView().getPermissionInheritance());
        } else {
            HidePopupEvent.fire(MoveEntityPresenter.this, MoveEntityPresenter.this, autoClose, ok);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    public interface MoveEntityView extends View {
        void setFolderView(View view);

        PermissionInheritance getPermissionInheritance();

        void setPermissionInheritance(PermissionInheritance permissionInheritance);
    }

    @ProxyCodeSplit
    public interface MoveEntityProxy extends Proxy<MoveEntityPresenter> {
    }
}
