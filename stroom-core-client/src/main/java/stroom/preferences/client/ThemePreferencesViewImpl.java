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

import stroom.document.client.event.DirtyUiHandlers;
import stroom.item.client.SelectionBox;
import stroom.preferences.client.ThemePreferencesPresenter.ThemePreferencesView;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public final class ThemePreferencesViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements ThemePreferencesView {

    private final Widget widget;

    @UiField
    SelectionBox<String> theme;
    @UiField
    SelectionBox<String> density;
    @UiField
    SelectionBox<String> font;
    @UiField
    SelectionBox<String> fontSize;

    @Inject
    public ThemePreferencesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        density.addItem("Default");
        density.addItem("Comfortable");
        density.addItem("Compact");

        fontSize.addItem("Small");
        fontSize.addItem("Medium");
        fontSize.addItem("Large");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        theme.focus();
    }

    @Override
    public String getTheme() {
        return theme.getValue();
    }

    @Override
    public void setTheme(final String theme) {
        this.theme.setValue(theme);
    }

    @Override
    public void setThemes(final List<String> themes) {
        this.theme.clear();
        this.theme.addItems(themes);
    }

    @Override
    public String getDensity() {
        return density.getValue();
    }

    @Override
    public void setDensity(final String density) {
        if (density == null) {
            this.density.setValue("Default");
        } else {
            this.density.setValue(density);
        }
    }

    @Override
    public String getFont() {
        return font.getValue();
    }

    @Override
    public void setFont(final String font) {
        this.font.setValue(font);
    }

    @Override
    public void setFonts(final List<String> fonts) {
        this.font.clear();
        this.font.addItems(fonts);
    }

    @Override
    public String getFontSize() {
        return this.fontSize.getValue();
    }

    @Override
    public void setFontSize(final String fontSize) {
        this.fontSize.setValue(fontSize);
    }

    @UiHandler("theme")
    public void onThemeValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    @UiHandler("density")
    public void onDensityValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    @UiHandler("font")
    public void onFontValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    @UiHandler("fontSize")
    public void onFontSizeValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    public interface Binder extends UiBinder<Widget, ThemePreferencesViewImpl> {

    }
}
