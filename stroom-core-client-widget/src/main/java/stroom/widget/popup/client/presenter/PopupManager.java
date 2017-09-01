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

package stroom.widget.popup.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.view.PopupSupportImpl;

import java.util.HashMap;
import java.util.Map;

public class PopupManager {
    private Map<PresenterWidget<?>, PopupSupport> popupMap;

    @Inject
    public PopupManager(final EventBus eventBus) {
        eventBus.addHandler(ShowPopupEvent.getType(), event -> show(event));
        eventBus.addHandler(HidePopupEvent.getType(), event -> hide(event.getPresenterWidget(), event.isAutoClose(), event.isOk()));
        eventBus.addHandler(DisablePopupEvent.getType(), event -> disable(event.getPresenterWidget()));
        eventBus.addHandler(EnablePopupEvent.getType(), event -> enable(event.getPresenterWidget()));
    }

    private void show(final ShowPopupEvent event) {
        final PresenterWidget<?> presenterWidget = event.getPresenterWidget();

        if (popupMap == null) {
            popupMap = new HashMap<>();
        }

        // Toggle popup visibility if we are asked to show twice.
        if (popupMap.containsKey(presenterWidget)) {
            hide(presenterWidget, true, false);

        } else {
            final PopupSupport popupSupport = new PopupSupportImpl(presenterWidget.getView(), event.getCaption(),
                    event.getModal(), event.getAutoHidePartners());

            popupMap.put(presenterWidget, popupSupport);

            // Create popup UI handlers if none have been provided to perform
            // default hide behaviour.
            PopupUiHandlers popupUiHandlers = event.getPopupUiHandlers();
            if (popupUiHandlers == null) {
                popupUiHandlers = new DefaultPopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        // By default always hide a popup when requested to do
                        // so.
                        hide(presenterWidget, autoClose, ok);
                    }
                };
            }

            presenterWidget.bind();
            popupSupport.show(event.getPopupType(), event.getPopupPosition(), event.getPopupSize(), popupUiHandlers);
        }
    }

    private void hide(final PresenterWidget<?> presenterWidget, final boolean autoClose, final boolean ok) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.remove(presenterWidget);
            if (popupSupport != null) {
                presenterWidget.unbind();
                popupSupport.hide(autoClose, ok);
            }
        }
    }

    private void disable(final PresenterWidget<?> presenterWidget) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.get(presenterWidget);
            if (popupSupport != null) {
                popupSupport.setEnabled(false);
            }
        }
    }

    private void enable(final PresenterWidget<?> presenterWidget) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.get(presenterWidget);
            if (popupSupport != null) {
                popupSupport.setEnabled(true);
            }
        }
    }
}
