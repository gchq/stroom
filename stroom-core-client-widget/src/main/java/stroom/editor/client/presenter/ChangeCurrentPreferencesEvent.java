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

package stroom.editor.client.presenter;

import stroom.ui.config.shared.UserPreferences.Toggle;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class ChangeCurrentPreferencesEvent extends GwtEvent<ChangeCurrentPreferencesEvent.Handler> {

    private static Type<Handler> TYPE;
    private final CurrentPreferences currentPreferences;

    private ChangeCurrentPreferencesEvent(final CurrentPreferences currentPreferences) {
        Objects.requireNonNull(currentPreferences);
        this.currentPreferences = currentPreferences;
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    public static void fire(final HasHandlers handlers,
                            final CurrentPreferences currentPreferences) {
        handlers.fireEvent(new ChangeCurrentPreferencesEvent(currentPreferences));
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onChange(this);
    }

    public String getTheme() {
        return currentPreferences.getTheme();
    }

    public String getEditorTheme() {
        return currentPreferences.getEditorTheme();
    }

    public String getEditorKeyBindings() {
        return currentPreferences.getEditorKeyBindings();
    }

    public Toggle getEditorLiveAutoCompletion() {
        return currentPreferences.getEditorLiveAutoCompletion();
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onChange(ChangeCurrentPreferencesEvent event);
    }
}
