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

package stroom.widget.popup.client.presenter;

import stroom.task.client.HasTaskMonitorFactory;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.view.PopupSupportImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.HashMap;
import java.util.Map;

public class PopupManager implements DialogEvent.Handler {

    private Map<PresenterWidget<?>, PopupSupport> popupMap;

    @Inject
    public PopupManager(final EventBus eventBus) {
        eventBus.addHandler(ShowPopupEvent.getType(), this::show);
        eventBus.addHandler(HidePopupRequestEvent.getType(), this::hideRequest);
        eventBus.addHandler(HidePopupEvent.getType(), this::hide);
        eventBus.addHandler(DisablePopupEvent.getType(), this::disable);
        eventBus.addHandler(EnablePopupEvent.getType(), this::enable);
        eventBus.addHandler(RenamePopupEvent.getType(), this::rename);
        eventBus.addHandler(DialogEvent.getType(), this);
    }

    private void show(final ShowPopupEvent event) {
        final PresenterWidget<?> presenterWidget = event.getPresenterWidget();

        if (popupMap == null) {
            popupMap = new HashMap<>();
        }

        // Toggle popup visibility if we are asked to show twice.
        if (popupMap.containsKey(presenterWidget)) {
            hide(HidePopupEvent.builder(presenterWidget).autoClose(true).ok(false).build());

        } else {
            HasTaskMonitorFactory hasTaskListener = null;
            if (presenterWidget instanceof HasTaskMonitorFactory) {
                hasTaskListener = (HasTaskMonitorFactory) presenterWidget;
            }


            final PopupSupport popupSupport = new PopupSupportImpl(
                    presenterWidget.getView(),
                    hasTaskListener,
                    event.getIcon(),
                    event.getCaption(),
                    event.getModal(), event.getAutoHidePartners());

            popupMap.put(presenterWidget, popupSupport);

            presenterWidget.bind();
            popupSupport.show(event);
        }
    }

    private void hideRequest(final HidePopupRequestEvent event) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.get(event.getPresenterWidget());
            if (popupSupport != null) {
                popupSupport.hideRequest(event);
            }
        }
    }

    private void hide(final HidePopupEvent event) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.remove(event.getPresenterWidget());
            if (popupSupport != null) {
                event.getPresenterWidget().unbind();
                popupSupport.hide(event);
            }
        }
    }

    private void disable(final DisablePopupEvent event) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.get(event.getPresenterWidget());
            if (popupSupport != null) {
                popupSupport.setEnabled(false);
            }
        }
    }

    private void enable(final EnablePopupEvent event) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.get(event.getPresenterWidget());
            if (popupSupport != null) {
                popupSupport.setEnabled(true);
            }
        }
    }

    private void rename(final RenamePopupEvent event) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.get(event.getPresenterWidget());
            if (popupSupport != null) {
                popupSupport.setCaption(event.getCaption());
            }
        }
    }

    @Override
    public void onDialogAction(final DialogEvent event) {
        if (popupMap != null) {
            final PopupSupport popupSupport = popupMap.get(event.getPresenterWidget());
            if (popupSupport != null) {
                popupSupport.onDialogAction(event.getAction());
            }
        }
    }
}
