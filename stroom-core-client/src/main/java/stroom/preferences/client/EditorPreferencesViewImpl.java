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

package stroom.preferences.client;

import stroom.document.client.event.DirtyUiHandlers;
import stroom.item.client.DecoratedItem;
import stroom.item.client.SelectionBox;
import stroom.preferences.client.EditorPreferencesPresenter.EditorPreferencesView;
import stroom.ui.config.shared.UserPreferences.EditorKeyBindings;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.util.shared.NullSafe;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class EditorPreferencesViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements EditorPreferencesView {

    private final Widget widget;

    @UiField
    SelectionBox<EditorThemeName> editorTheme;
    @UiField
    SelectionBox<String> editorKeyBindings;
    @UiField
    SelectionBox<String> editorLiveAutoCompletion;

    @Inject
    public EditorPreferencesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        setEditorKeyBindingsValues();
        setEditorLiveAutoCompletionValues();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        editorTheme.focus();
    }

    @Override
    public String getEditorTheme() {
        return editorTheme.getValue().getValue();
    }

    @Override
    public void setEditorTheme(final String editorTheme) {
        String localDisplayName = editorTheme;
        if (localDisplayName != null) {
            localDisplayName = localDisplayName.replace('_', ' ');
            localDisplayName = Character.toUpperCase(localDisplayName.charAt(0))
                    + localDisplayName.substring(1);
        }

        this.editorTheme.setValue(new EditorThemeName(editorTheme, localDisplayName));
    }

    @Override
    public void setEditorThemes(final List<String> editorThemes) {
        this.editorTheme.clear();

        final List<EditorThemeName> editorThemeNames = NullSafe.stream(editorThemes)
                .map(this::createEditorThemeItem)
                .collect(Collectors.toList());

        this.editorTheme.addItems(editorThemeNames);
    }

    @Override
    public EditorKeyBindings getEditorKeyBindings() {
        return EditorKeyBindings.fromDisplayValue(editorKeyBindings.getValue());

    }

    @Override
    public void setEditorKeyBindings(final EditorKeyBindings editorKeyBindings) {
        this.editorKeyBindings.setValue(editorKeyBindings.getDisplayValue());
    }

    private EditorThemeName createEditorThemeItem(final String editorTheme) {
        String displayName = editorTheme;
        if (displayName != null) {
            displayName = displayName.replace('_', ' ');
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

    @UiHandler("editorTheme")
    public void onEditorThemeValueChange(final ValueChangeEvent<EditorThemeName> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    @UiHandler("editorKeyBindings")
    public void onEditorKeyBindingsValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    @UiHandler("editorLiveAutoCompletion")
    public void onEditorLiveAutoCompletionValueChange(final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    public interface Binder extends UiBinder<Widget, EditorPreferencesViewImpl> {

    }

    public static class EditorThemeName extends DecoratedItem<String> {

        public EditorThemeName(final String value, final String displayValue) {
            super(value, displayValue);
        }
    }
}
