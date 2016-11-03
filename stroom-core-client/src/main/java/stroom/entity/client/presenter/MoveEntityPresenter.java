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

import stroom.entity.client.event.MoveEntityEvent;
import stroom.entity.client.event.ShowMoveEntityDialogEvent;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.Folder;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class MoveEntityPresenter
        extends MyPresenter<MoveEntityPresenter.MoveEntityView, MoveEntityPresenter.MoveEntityProxy>
        implements ShowMoveEntityDialogEvent.Handler, PopupUiHandlers {
    private final EntityTreePresenter entityTreePresenter;
    private ExplorerData entity;

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
        this.entity = event.getSelected();

        entityTreePresenter.getSelectionModel().clear();

//        if (event.getCurrentParents() != null && event.getCurrentParents().size() > 0) {
//            ExplorerData folder = null;
//            for (final ExplorerData parent : event.getCurrentParents()) {
//                if (folder == null && parent != null && parent instanceof EntityData
//                        && Folder.ENTITY_TYPE.equals(parent.getType())) {
//                    folder = parent;
//                }
//            }
//
//            if (folder != null) {
//                entityTreePresenter.getSelectionModel().setSelected(folder, true);
//            }
//
//
//        } else {
//            entityTreePresenter.getModel().reset();
//            entityTreePresenter.getModel().refresh();
//        }

        entityTreePresenter.getSelectionModel().setSelected(event.getSelected(), true);
        entityTreePresenter.getModel().reset();
        entityTreePresenter.getModel().setEnsureVisible(event.getSelected());
        entityTreePresenter.getModel().refresh();

        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final String caption = "Move " + entity.getDisplayValue();
        final PopupSize popupSize = new PopupSize(350, 400, 350, 350, 2000, 2000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, this);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final DocRef folder = getFolder();
            MoveEntityEvent.fire(MoveEntityPresenter.this, MoveEntityPresenter.this, ((EntityData) entity).getDocRef(),
                    folder);
        } else {
            HidePopupEvent.fire(MoveEntityPresenter.this, MoveEntityPresenter.this, autoClose, ok);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    private DocRef getFolder() {
        final ExplorerData selected = entityTreePresenter.getSelectionModel().getSelectedObject();
        if (selected != null && selected instanceof EntityData) {
            return ((EntityData) selected).getDocRef();
        }

        return null;
    }

    public interface MoveEntityView extends View {
        void setFolderView(View view);
    }

    @ProxyCodeSplit
    public interface MoveEntityProxy extends Proxy<MoveEntityPresenter> {
    }
}
