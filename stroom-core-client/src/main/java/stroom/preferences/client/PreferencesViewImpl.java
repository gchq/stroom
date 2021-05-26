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

package stroom.preferences.client;

import stroom.item.client.StringListBox;
import stroom.preferences.client.PreferencesPresenter.PreferencesView;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public final class PreferencesViewImpl
        extends ViewWithUiHandlers<PreferencesUiHandlers>
        implements PreferencesView {

    private final Widget widget;

    @UiField
    StringListBox theme;
    @UiField
    Button setAsDefault;
    @UiField
    Button revertToDefault;

    @Inject
    public PreferencesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        theme.addChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChange();
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getThere() {
        return theme.getSelected();
    }

    @Override
    public void setTheme(final String theme) {
        this.theme.setSelected(theme);
    }

    @Override
    public void setThemes(final List<String> themes) {
        this.theme.clear();
        this.theme.addItems(themes);
    }

    @Override
    public void setAsDefaultVisible(final boolean visible) {
        setAsDefault.setVisible(visible);
    }

    @UiHandler("setAsDefault")
    void onClickSetAsDefault(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetAsDefault();
        }
    }

    @UiHandler("revertToDefault")
    void onClickRevertToDefault(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRevertToDefault();
        }
    }

    public interface Binder extends UiBinder<Widget, PreferencesViewImpl> {

    }
}
