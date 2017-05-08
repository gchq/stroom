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

package stroom.pipeline.structure.client.presenter;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class NewElementPresenter extends MyPresenterWidget<NewElementPresenter.NewElementView> {
    public interface NewElementView extends View {
        HasText getId();

        HasKeyDownHandlers getIdBox();

        void focus();
    }

    private PipelineElementType elementType;

    @Inject
    public NewElementPresenter(final EventBus eventBus, final NewElementView view) {
        super(eventBus, view);

        registerHandler(view.getIdBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                hide();
            }
        }));
    }

    public void show(final PipelineElementType elementType, final PopupUiHandlers popupUiHandlers) {
        this.elementType = elementType;

        getView().getId().setText(ModelStringUtil.toCamelCase(elementType.getType()));

        final PopupSize popupSize = new PopupSize(250, 78, 250, 78, 1000, 78, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Create Element", popupUiHandlers);
        getView().focus();
    }

    public void hide() {
        HidePopupEvent.fire(NewElementPresenter.this, NewElementPresenter.this);
    }

    public PipelineElementType getElementInfo() {
        return elementType;
    }

    public String getElementId() {
        return getView().getId().getText();
    }
}
