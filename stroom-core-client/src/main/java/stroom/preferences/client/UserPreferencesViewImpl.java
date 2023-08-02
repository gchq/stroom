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

import stroom.item.client.DecoratedItem;
import stroom.item.client.SelectionBox;
import stroom.preferences.client.UserPreferencesPresenter.UserPreferencesView;
import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.shared.UserPreferences.EditorKeyBindings;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UserPreferencesViewImpl
        extends ViewWithUiHandlers<UserPreferencesUiHandlers>
        implements UserPreferencesView {

    public static final List<String> STANDARD_FORMATS = Arrays
            .asList("yyyy-MM-dd'T'HH:mm:ss.SSSXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS xx",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS xxx",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS VV",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "dd/MM/yyyy HH:mm:ss",
                    "dd/MM/yy HH:mm:ss",
                    "MM/dd/yyyy HH:mm:ss",
                    "d MMM yyyy HH:mm:ss",
                    "yyyy-MM-dd",
                    "dd/MM/yyyy",
                    "dd/MM/yy",
                    "MM/dd/yyyy",
                    "d MMM yyyy");

    private final Widget widget;

    @UiField
    FormGroup userPreferencesTimeZoneId;
    @UiField
    FormGroup userPreferencesTimeZoneOffset;
    @UiField
    SelectionBox<String> theme;
    @UiField
    SelectionBox<EditorThemeName> editorTheme;
    @UiField
    SelectionBox<String> editorKeyBindings;
    @UiField
    SelectionBox<String> editorLiveAutoCompletion;
    @UiField
    SelectionBox<String> density;
    @UiField
    SelectionBox<String> font;
    @UiField
    SelectionBox<String> fontSize;
    @UiField
    SelectionBox<String> format;
    @UiField
    CustomCheckBox custom;
    @UiField
    TextBox text;
    @UiField
    SelectionBox<Use> timeZoneUse;
    @UiField
    SelectionBox<String> timeZoneId;
    @UiField
    ValueSpinner timeZoneOffsetHours;
    @UiField
    ValueSpinner timeZoneOffsetMinutes;
    @UiField
    Button setAsDefault;
    @UiField
    Button revertToDefault;

    @Inject
    public UserPreferencesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setAsDefault.setIcon(SvgImage.OK);
        revertToDefault.setIcon(SvgImage.UNDO);

        setEditorKeyBindingsValues();
        setEditorLiveAutoCompletionValues();

        density.addItem("Default");
        density.addItem("Comfortable");
        density.addItem("Compact");

        fontSize.addItem("Small");
        fontSize.addItem("Medium");
        fontSize.addItem("Large");

        format.addItems(STANDARD_FORMATS);

        timeZoneUse.addItem(TimeZone.Use.LOCAL);
        timeZoneUse.addItem(TimeZone.Use.UTC);
        timeZoneUse.addItem(TimeZone.Use.ID);
        timeZoneUse.addItem(TimeZone.Use.OFFSET);

        for (final String tz : getTimeZoneIds()) {
            timeZoneId.addItem(tz);
        }

        timeZoneOffsetHours.setMin(-12);
        timeZoneOffsetHours.setMax(12);
        timeZoneOffsetHours.setValue(0);
        timeZoneOffsetHours.setMinStep(1);
        timeZoneOffsetHours.setMaxStep(1);

        timeZoneOffsetMinutes.setMin(0);
        timeZoneOffsetMinutes.setMax(45);
        timeZoneOffsetMinutes.setValue(0);
        timeZoneOffsetMinutes.setMinStep(15);
        timeZoneOffsetMinutes.setMaxStep(15);
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
    public String getEditorTheme() {
        return editorTheme.getValue().getValue();
    }

    @Override
    public void setEditorTheme(final String editorTheme) {
        String localDisplayName = editorTheme;
        if (localDisplayName != null) {
            localDisplayName = localDisplayName.replace("_", " ");
            localDisplayName = Character.toUpperCase(localDisplayName.charAt(0))
                    + localDisplayName.substring(1);
        }

        this.editorTheme.setValue(new EditorThemeName(editorTheme, localDisplayName));
    }

    @Override
    public void setEditorThemes(final List<String> editorThemes) {
        this.editorTheme.clear();

        final List<EditorThemeName> editorThemeNames = GwtNullSafe.stream(editorThemes)
                .map(this::createEditorThemeItem)
                .collect(Collectors.toList());

        this.editorTheme.addItems(editorThemeNames);
    }

    @Override
    public EditorKeyBindings getEditorKeyBindings() {
        return EditorKeyBindings.fromDisplayValue(editorKeyBindings.getValue());

    }

    @Override
    public void setEditorKeyBindings(EditorKeyBindings editorKeyBindings) {
        this.editorKeyBindings.setValue(editorKeyBindings.getDisplayValue());
    }

    private EditorThemeName createEditorThemeItem(final String editorTheme) {
        String displayName = editorTheme;
        if (displayName != null) {
            displayName = displayName.replace("_", " ");
            displayName = Character.toUpperCase(displayName.charAt(0))
                    + displayName.substring(1);
        }
        return new EditorThemeName(editorTheme, displayName);
    }

    public void setEditorKeyBindingsValues() {
        this.editorKeyBindings.clear();
        Arrays.stream(EditorKeyBindings.values())
                .sorted(Comparator.comparing(EditorKeyBindings::getDisplayValue))
                .map(EditorKeyBindings::getDisplayValue)
                .forEach(displayName -> this.editorKeyBindings.addItem(displayName));
    }

    @Override
    public Toggle getEditorLiveAutoCompletion() {
        return Toggle.fromDisplayValue(editorLiveAutoCompletion.getValue());
    }


    @Override
    public void setEditorLiveAutoCompletion(final Toggle editorLiveAutoCompletion) {
        Objects.requireNonNull(editorLiveAutoCompletion);
        this.editorLiveAutoCompletion.setValue(editorLiveAutoCompletion.getDisplayValue());
    }

    public void setEditorLiveAutoCompletionValues() {
        this.editorLiveAutoCompletion.clear();
        this.editorLiveAutoCompletion.addItem(Toggle.ON.getDisplayValue());
        this.editorLiveAutoCompletion.addItem(Toggle.OFF.getDisplayValue());
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

    @Override
    public String getPattern() {
        if (custom.getValue()) {
            return text.getText();
        }

        return format.getValue();
    }

    @Override
    public void setPattern(final String pattern) {
        String text = pattern;
        if (text == null || text.trim().length() == 0) {
            text = STANDARD_FORMATS.get(0);
        }

        if (!text.equals(this.format.getValue())) {
            this.format.setValue(text);
        }

        final boolean custom = this.format.getValue() == null;
        this.custom.setValue(custom);
        this.text.setEnabled(custom);
        this.text.setText(text);
    }

    @Override
    public Use getTimeZoneUse() {
        return this.timeZoneUse.getValue();
    }

    @Override
    public void setTimeZoneUse(final Use use) {
        this.timeZoneUse.setValue(use);
        changeVisible();
    }

    @Override
    public String getTimeZoneId() {
        return this.timeZoneId.getValue();
    }

    @Override
    public void setTimeZoneId(final String timeZoneId) {
        this.timeZoneId.setValue(timeZoneId);
    }

    @Override
    public Integer getTimeZoneOffsetHours() {
        final int val = this.timeZoneOffsetHours.getIntValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

    @Override
    public void setTimeZoneOffsetHours(final Integer timeZoneOffsetHours) {
        if (timeZoneOffsetHours == null) {
            this.timeZoneOffsetHours.setValue(0);
        } else {
            this.timeZoneOffsetHours.setValue(timeZoneOffsetHours);
        }
    }

    @Override
    public Integer getTimeZoneOffsetMinutes() {
        final int val = this.timeZoneOffsetMinutes.getIntValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

    @Override
    public void setTimeZoneOffsetMinutes(final Integer timeZoneOffsetMinutes) {
        if (timeZoneOffsetMinutes == null) {
            this.timeZoneOffsetMinutes.setValue(0);
        } else {
            this.timeZoneOffsetMinutes.setValue(timeZoneOffsetMinutes);
        }
    }

    @Override
    public void setAsDefaultVisible(final boolean visible) {
        setAsDefault.setVisible(visible);
    }


    public void changeVisible() {
        userPreferencesTimeZoneId.setVisible(TimeZone.Use.ID.equals(this.timeZoneUse.getValue()));
        userPreferencesTimeZoneOffset.setVisible(TimeZone.Use.OFFSET.equals(this.timeZoneUse.getValue()));
    }

    @UiHandler("theme")
    public void onThemeValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("editorTheme")
    public void onEditorThemeValueChange(final ValueChangeEvent<EditorThemeName> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("editorKeyBindings")
    public void onEditorKeyBindingsValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("editorLiveAutoCompletion")
    public void onEditorLiveAutoCompletionValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("density")
    public void onDensityValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("font")
    public void onFontValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("fontSize")
    public void onFontSizeValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("custom")
    public void onTickBoxClick(final ValueChangeEvent<Boolean> event) {
        text.setEnabled(custom.getValue());
    }

    @UiHandler("format")
    public void onFormatChange(final ValueChangeEvent<String> event) {
        setPattern(this.format.getValue());
    }

    @UiHandler("timeZoneUse")
    public void onTimeZoneUseValueChange(final ValueChangeEvent<TimeZone.Use> event) {
        changeVisible();
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


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, UserPreferencesViewImpl> {

    }


    // --------------------------------------------------------------------------------


    private static native String[] getTimeZoneIds()/*-{
        return $wnd.moment.tz.names();
    }-*/;


    // --------------------------------------------------------------------------------


    public static class EditorThemeName extends DecoratedItem<String> {

        public EditorThemeName(final String value, final String displayValue) {
            super(value, displayValue);
        }
    }
}
