/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.node.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.entity.client.presenter.NameDocumentView;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.NodeGroup;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class NewNodeGroupPresenter
        extends MyPresenterWidget<NameDocumentView>
        implements HidePopupRequestEvent.Handler,
        DialogActionUiHandlers {

    private Consumer<NodeGroup> consumer;

    private final NodeGroupClient nodeGroupClient;

    @Inject
    public NewNodeGroupPresenter(final EventBus eventBus,
                                 final NameDocumentView view,
                                 final NodeGroupClient nodeGroupClient) {
        super(eventBus, view);
        this.nodeGroupClient = nodeGroupClient;
    }

    public void show(final String name, final Consumer<NodeGroup> consumer) {
        this.consumer = consumer;
        getView().setUiHandlers(this);
        getView().setName(name);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("New")
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .fire();
        getView().focus();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final String name = getView().getName().trim();
            if (NullSafe.isBlankString(name)) {
                AlertEvent.fireError(
                        NewNodeGroupPresenter.this,
                        "You must provide a name",
                        e::reset);
            } else {
                checkNodeGroupName(name, e);
            }
        } else {
            e.hide();
        }
    }

    private void checkNodeGroupName(final String name, final HidePopupRequestEvent e) {
        nodeGroupClient.checkNodeGroupName(name, result -> {
            if (result != null) {
                AlertEvent.fireError(
                        NewNodeGroupPresenter.this,
                        "Group name '"
                        + name
                        + "' is already in use by another group.",
                        e::reset);
            } else {
                create(name, e);
            }
        }, RestErrorHandler.forPopup(this, e), this);
    }

    private void create(final String name, final HidePopupRequestEvent e) {
        nodeGroupClient.create(name, nodeGroup -> {
            consumer.accept(nodeGroup);
            e.hide();
        }, RestErrorHandler.forPopup(this, e), this);
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        DialogEvent.fire(this, this, action);
    }
}
