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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.NameDocumentView;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.gwt.core.shared.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class NewIndexVolumeGroupPresenter
        extends MyPresenterWidget<NameDocumentView>
        implements HidePopupRequestEvent.Handler,
        DialogActionUiHandlers {

    private Consumer<IndexVolumeGroup> consumer;

    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final RestFactory restFactory;

    @Inject
    public NewIndexVolumeGroupPresenter(final EventBus eventBus,
                                        final NameDocumentView view,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    public void show(final String name, final Consumer<IndexVolumeGroup> consumer) {
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
            if (name.length() == 0) {
                AlertEvent.fireError(
                        NewIndexVolumeGroupPresenter.this,
                        "You must provide a name",
                        e::reset);
            } else {
                checkVolumeGroupName(name, e);
            }
        } else {
            e.hide();
        }
    }

    private void checkVolumeGroupName(final String name, final HidePopupRequestEvent e) {
        restFactory
                .create(INDEX_VOLUME_GROUP_RESOURCE)
                .method(res -> res.fetchByName(name))
                .onSuccess(result -> {
                    if (result != null) {
                        AlertEvent.fireError(
                                NewIndexVolumeGroupPresenter.this,
                                "Group name '"
                                        + name
                                        + "' is already in use by another group.",
                                e::reset);
                    } else {
                        createVolumeGroup(name, e);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, e))
                .taskMonitorFactory(this)
                .exec();
    }

    private void createVolumeGroup(final String name, final HidePopupRequestEvent e) {
        restFactory
                .create(INDEX_VOLUME_GROUP_RESOURCE)
                .method(res -> res.create(name))
                .onSuccess(indexVolumeGroup -> {
                    consumer.accept(indexVolumeGroup);
                    e.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, e))
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        DialogEvent.fire(this, this, action);
    }
}
