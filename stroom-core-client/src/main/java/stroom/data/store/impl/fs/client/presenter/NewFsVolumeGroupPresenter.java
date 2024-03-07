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

package stroom.data.store.impl.fs.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.store.impl.fs.client.presenter.NewFsVolumeGroupPresenter.NewFsVolumeGroupView;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestFactory;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class NewFsVolumeGroupPresenter
        extends MyPresenterWidget<NewFsVolumeGroupView>
        implements HidePopupRequestEvent.Handler {

    private Consumer<FsVolumeGroup> consumer;

    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final RestFactory restFactory;


    @Inject
    public NewFsVolumeGroupPresenter(final EventBus eventBus,
                                     final NewFsVolumeGroupView view,
                                     final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    public void show(final Consumer<FsVolumeGroup> consumer) {
        this.consumer = consumer;
        getView().setUiHandlers(new DefaultHideRequestUiHandlers(this));

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
            final boolean isDefault = getView().isDefault();

            final Runnable action = () -> {
                final FsVolumeGroup volumeGroup = FsVolumeGroup.builder()
                        .withName(name)
                        .withDefaultVolume(isDefault)
                        .build();

                restFactory.builder()
                        .forType(FsVolumeGroup.class)
                        .onSuccess(result -> {
                            if (result != null) {
                                AlertEvent.fireError(
                                        NewFsVolumeGroupPresenter.this,
                                        "Group name '"
                                                + name
                                                + "' is already in use by another group.",
                                        null);
                            } else {
                                consumer.accept(volumeGroup);
                            }
                        })
                        .call(FS_VOLUME_GROUP_RESOURCE)
                        .fetchByName(name);
            };

            if (GwtNullSafe.isBlankString(name)) {
                AlertEvent.fireError(
                        NewFsVolumeGroupPresenter.this,
                        "You must provide a name",
                        null);
            } else if (isDefault) {
                ConfirmEvent.fireWarn(
                        NewFsVolumeGroupPresenter.this,
                        "You are setting this Volume Group as the default group.\n" +
                                "This will remove the default state from any existing default volume group.\n\n" +
                                "Do you wish to continue?",
                        ok -> {
                            if (ok) {
                                action.run();
                            }
                        });
            } else {
                action.run();
            }
        } else {
            consumer.accept(null);
        }
    }

    public void hide() {
        HidePopupEvent.builder(this).fire();
    }


    // --------------------------------------------------------------------------------


    public interface NewFsVolumeGroupView
            extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        String getName();

        void setName(String name);

        boolean isDefault();

        void setDefault(final boolean isDefault);
    }
}
