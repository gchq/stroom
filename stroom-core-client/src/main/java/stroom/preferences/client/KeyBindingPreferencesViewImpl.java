/*
 * Copyright 2026 Crown Copyright
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

import stroom.preferences.client.KeyBindingPreferencesPresenter.KeyBindingPreferencesView;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class KeyBindingPreferencesViewImpl
        extends ViewWithUiHandlers<KeyBindingPreferencesUiHandlers>
        implements KeyBindingPreferencesView {

    private final Widget widget;

    @UiField
    CustomCheckBox enableCharacterKeyShortcuts;
    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel list;

    @Inject
    public KeyBindingPreferencesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        quickFilter.setAccessibleName("Filter keyboard shortcuts");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        enableCharacterKeyShortcuts.setFocus(true);
    }

    @Override
    public void setList(final View view) {
        list.setWidget(view.asWidget());
    }

    @Override
    public boolean isCharacterKeyShortcutsEnabled() {
        return enableCharacterKeyShortcuts.getValue();
    }

    @Override
    public void setCharacterKeyShortcutsEnabled(final boolean enabled) {
        enableCharacterKeyShortcuts.setValue(enabled);
    }

    @UiHandler("enableCharacterKeyShortcuts")
    void onEnableCharacterKeyShortcutsValueChange(final ValueChangeEvent<Boolean> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("quickFilter")
    void onQuickFilterValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onFilterChange(e.getValue());
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, KeyBindingPreferencesViewImpl> {

    }
}
