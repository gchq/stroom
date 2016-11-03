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

package stroom.widget.dropdowntree.client.presenter;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public abstract class DropDownTreePresenter extends MyPresenterWidget<DropDownTreePresenter.DropDownTreeView>
        implements DropDownTreeUiHandlers, PopupUiHandlers {
    public interface DropDownTreeView extends View, HasUiHandlers<DropDownTreeUiHandlers> {
        void setCellTree(Widget widget);

        void setUnselectedText(String unselectedText);

        void setSize(int width, int height);
    }

    private String caption = "Choose item";

    public DropDownTreePresenter(final EventBus eventBus, final DropDownTreeView view) {
        super(eventBus, view);
        getView().setUiHandlers(this);
    }

    public void show() {
        refresh();
        final PopupSize popupSize = new PopupSize(350, 400, 350, 350, 2000, 2000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, this);
        focus();
    }

    protected abstract void refresh();

    protected abstract void focus();

    public void setUnselectedText(final String unselectedText) {
        getView().setUnselectedText(unselectedText);
    }

    public void setCellTree(final Widget widget) {
        getView().setCellTree(widget);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        HidePopupEvent.fire(this, this);
    }

    public void setCaption(final String caption) {
        this.caption = caption;
    }
}
