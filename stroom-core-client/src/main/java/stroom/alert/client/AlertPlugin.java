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

package stroom.alert.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.event.PromptEvent;
import stroom.alert.client.presenter.CommonAlertPresenter;
import stroom.alert.client.presenter.PromptPresenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class AlertPlugin implements AlertEvent.Handler, ConfirmEvent.Handler {

    private final EventBus eventBus;
    private final CommonAlertPresenter alertPresenter;

    @Inject
    public AlertPlugin(final EventBus eventBus, final CommonAlertPresenter alertPresenter,
                       final Provider<PromptPresenter> promptPresenterProvider) {
        this.eventBus = eventBus;
        this.alertPresenter = alertPresenter;

        getEventBus().addHandler(AlertEvent.getType(), this);
        getEventBus().addHandler(ConfirmEvent.getType(), this);

        // The screens it opens, made when first asked for
        eventBus.addHandler(PromptEvent.getType(), event -> promptPresenterProvider.get().onPrompt(event));
    }

    protected final EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public void onAlert(final AlertEvent event) {
        alertPresenter.show(event);
    }

    @Override
    public void onConfirm(final ConfirmEvent event) {
        alertPresenter.show(event);
    }
}
