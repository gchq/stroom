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

package stroom.alert.client.presenter;

import stroom.alert.client.event.PromptEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class PromptPresenter extends MyPresenter<PromptPresenter.PromptView, PromptPresenter.PromptProxy>
        implements PromptEvent.Handler {

    @Inject
    public PromptPresenter(final EventBus eventBus, final PromptView view, final PromptProxy proxy) {
        super(eventBus, view, proxy);
    }

    @ProxyEvent
    @Override
    public void onPrompt(final PromptEvent event) {
        getView().setPrompt(event.getMessage(), event.getInitialValue());
        // Enter in the text box is the same as OK.
        getView().setOnEnter(() -> HidePopupRequestEvent.builder(this).action(DialogAction.OK).fire());
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(450, 190))
                .caption(event.getCaption())
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    e.hide();
                    event.getCallback().onResult(e.isOk()
                            ? getView().getValue()
                            : null);
                })
                .fire();
    }

    @Override
    protected void revealInParent() {
        // Do nothing.
    }

    @ProxyStandard
    public interface PromptProxy extends Proxy<PromptPresenter> {

    }

    public interface PromptView extends View, Focus {

        /**
         * Shows what to ask, and the value to start with.
         */
        void setPrompt(String message, String initialValue);

        /**
         * @return The value the user entered.
         */
        String getValue();

        /**
         * Sets what to do when the user presses Enter in the text box.
         */
        void setOnEnter(Runnable onEnter);
    }
}
