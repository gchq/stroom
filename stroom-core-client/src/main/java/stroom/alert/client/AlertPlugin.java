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

import stroom.alert.client.component.CommonAlertController;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.widget.component.client.AppRoot;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

/// Shows alerts and confirmations ([AlertEvent], [ConfirmEvent]), one at a time in the order they
/// come, each in a dialog of its own ([CommonAlertController]).
public class AlertPlugin implements AlertEvent.Handler, ConfirmEvent.Handler {

    private final EventBus eventBus;
    private final Provider<CommonAlertController> alertProvider;
    private final AppRoot appRoot;
    // The alerts to show; the first is shown
    private final List<CommonAlertEvent<?>> queue = new ArrayList<>();

    @Inject
    public AlertPlugin(final EventBus eventBus,
                       final Provider<CommonAlertController> alertProvider,
                       final AppRoot appRoot) {
        this.eventBus = eventBus;
        this.alertProvider = alertProvider;
        this.appRoot = appRoot;

        getEventBus().addHandler(AlertEvent.getType(), this);
        getEventBus().addHandler(ConfirmEvent.getType(), this);
    }

    protected final EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public void onAlert(final AlertEvent event) {
        show(event);
    }

    @Override
    public void onConfirm(final ConfirmEvent event) {
        show(event);
    }

    private void show(final CommonAlertEvent<?> event) {
        queue.add(event);
        if (queue.size() == 1) {
            showFirst();
        }
    }

    private void showFirst() {
        final CommonAlertEvent<?> event = queue.get(0);
        // The app owns each alert's dialog while it is shown
        alertProvider.get().show(appRoot, event, ok -> {
            try {
                // Tell the caller what the user decided
                if (event instanceof final ConfirmEvent confirmEvent) {
                    if (confirmEvent.getCallback() != null) {
                        confirmEvent.getCallback().onResult(ok);
                    }
                } else if (event instanceof final AlertEvent alertEvent) {
                    if (alertEvent.getCallback() != null) {
                        alertEvent.getCallback().onClose();
                    }
                }
            } catch (final RuntimeException exception) {
                GWT.log(exception.getMessage());
            } finally {
                queue.remove(0);
                if (!queue.isEmpty()) {
                    showFirst();
                }
            }
        });
    }
}
