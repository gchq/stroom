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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.entity.client.presenter.NameDocumentView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class NewIndexVolumeGroupPresenter
        extends MyPresenterWidget<NameDocumentView>
        implements HidePopupRequestEvent.Handler {

    private Consumer<String> consumer;

    @Inject
    public NewIndexVolumeGroupPresenter(final EventBus eventBus, final NameDocumentView view) {
        super(eventBus, view);
    }

    public void show(final String name, final Consumer<String> consumer) {
        this.consumer = consumer;
        getView().setUiHandlers(new DefaultHideRequestUiHandlers(this));
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
            final String newName = getView().getName().trim();
            if (newName.length() == 0) {
                AlertEvent.fireError(NewIndexVolumeGroupPresenter.this, "You must provide a name", null);
            } else {
                consumer.accept(newName);
            }
        } else {
            consumer.accept(null);
        }
    }

    public void hide() {
        HidePopupEvent.builder(this).fire();
    }
}
