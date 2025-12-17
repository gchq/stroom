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

package stroom.pipeline.structure.client.presenter;

import stroom.pipeline.shared.data.PipelineElementType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class NewElementPresenter extends MyPresenterWidget<NewElementPresenter.NewElementView> {

    private PipelineElementType elementType;
    private HidePopupRequestEvent.Handler handler;

    @Inject
    public NewElementPresenter(final EventBus eventBus, final NewElementView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                if (handler != null) {
                    HidePopupRequestEvent.builder(this).fire();
                }
            }
        }));
    }

    public void show(final PipelineElementType elementType,
                     final HidePopupRequestEvent.Handler handler,
                     final String name,
                     final String caption) {
        this.elementType = elementType;
        this.handler = handler;
        getView().getName().setText(name != null
                ? name
                : "");
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    public PipelineElementType getElementInfo() {
        return elementType;
    }

    public String getElementName() {
        return getView().getName().getText();
    }

    public String getElementDescription() {
        return getView().getDescription().getText();
    }

    public interface NewElementView extends View, Focus {

        HasText getName();

        HasKeyDownHandlers getNameBox();

        HasText getDescription();

        HasKeyDownHandlers getDescriptionBox();
    }
}
