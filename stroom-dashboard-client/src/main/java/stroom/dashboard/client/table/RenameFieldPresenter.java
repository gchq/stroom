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

package stroom.dashboard.client.table;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.shared.Field;
import stroom.util.shared.EqualsUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class RenameFieldPresenter extends MyPresenterWidget<RenameFieldPresenter.RenameFieldView> implements PopupUiHandlers {
    private TablePresenter tablePresenter;
    private Field field;

    @Inject
    public RenameFieldPresenter(final EventBus eventBus, final RenameFieldView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onHideRequest(false, true);
            }
        }));
    }

    public void show(final TablePresenter tablePresenter, final Field field) {
        this.tablePresenter = tablePresenter;
        this.field = field;

        getView().getName().setText(field.getName());

        final PopupSize popupSize = new PopupSize(250, 78, 250, 78, 1000, 78, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Rename Field", this);
        getView().focus();
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final String name = getView().getName().getText();
            if (name != null && !name.trim().isEmpty() && !EqualsUtil.isEquals(name, field.getName())) {
                field.setName(name);
                tablePresenter.setDirty(true);
                tablePresenter.redrawHeaders();
            }
        }

        HidePopupEvent.fire(tablePresenter, this);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
    }

    public String getName() {
        return getView().getName().getText();
    }

    public interface RenameFieldView extends View {
        HasText getName();

        HasKeyDownHandlers getNameBox();

        void focus();
    }
}
