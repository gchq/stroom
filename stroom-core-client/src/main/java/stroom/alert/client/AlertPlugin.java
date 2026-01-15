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

package stroom.alert.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.CommonAlertPresenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HandlerContainerImpl;

public class AlertPlugin extends HandlerContainerImpl implements AlertEvent.Handler, ConfirmEvent.Handler {

    private final EventBus eventBus;
    private final CommonAlertPresenter alertPresenter;

    @Inject
    public AlertPlugin(final EventBus eventBus, final CommonAlertPresenter alertPresenter) {
        this.eventBus = eventBus;
        this.alertPresenter = alertPresenter;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(AlertEvent.getType(), this));
        registerHandler(getEventBus().addHandler(ConfirmEvent.getType(), this));
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
