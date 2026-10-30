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

package stroom.widget.popup.client;

import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;

public class PopupManager {

    private Map<Controller, PopupHost> popupMap;

    @Inject
    public PopupManager(final EventBus eventBus) {
        eventBus.addHandler(ShowPopupEvent.getType(), this::show);
        eventBus.addHandler(HidePopupRequestEvent.getType(), this::hideRequest);
        eventBus.addHandler(HidePopupEvent.getType(), this::hide);
        eventBus.addHandler(DisablePopupEvent.getType(), this::disable);
        eventBus.addHandler(EnablePopupEvent.getType(), this::enable);
        eventBus.addHandler(RenamePopupEvent.getType(), this::rename);
    }

    private void show(final ShowPopupEvent event) {
        final Controller controller = event.getController();

        if (popupMap == null) {
            popupMap = new HashMap<>();
        }

        // Toggle popup visibility if we are asked to show twice.
        if (popupMap.containsKey(controller)) {
            hide(HidePopupEvent.builder(controller).autoClose(true).ok(false).build());

        } else {
            final PopupHost popupHost = new PopupHost(
                    controller,
                    event.getIcon(),
                    event.getCaption(),
                    event.getModal(), event.getAutoHidePartners());

            popupMap.put(controller, popupHost);

            popupHost.show(event);
        }
    }

    private void hideRequest(final HidePopupRequestEvent event) {
        if (popupMap != null) {
            final PopupHost popupHost = popupMap.get(event.getController());
            if (popupHost != null) {
                popupHost.hideRequest(event);
            }
        }
    }

    private void hide(final HidePopupEvent event) {
        if (popupMap != null) {
            final PopupHost popupHost = popupMap.remove(event.getController());
            if (popupHost != null) {
                popupHost.hide(event);
            }
        }
    }

    private void disable(final DisablePopupEvent event) {
        if (popupMap != null) {
            final PopupHost popupHost = popupMap.get(event.getController());
            if (popupHost != null) {
                if (event.getAction() != null) {
                    popupHost.setEnabled(event.getAction(), false);
                } else {
                    popupHost.setEnabled(false);
                }
            }
        }
    }

    private void enable(final EnablePopupEvent event) {
        if (popupMap != null) {
            final PopupHost popupHost = popupMap.get(event.getController());
            if (popupHost != null) {
                if (event.getAction() != null) {
                    popupHost.setEnabled(event.getAction(), true);
                } else {
                    popupHost.setEnabled(true);
                }
            }
        }
    }

    private void rename(final RenamePopupEvent event) {
        if (popupMap != null) {
            final PopupHost popupHost = popupMap.get(event.getController());
            if (popupHost != null) {
                popupHost.setCaption(event.getCaption());
            }
        }
    }
}
