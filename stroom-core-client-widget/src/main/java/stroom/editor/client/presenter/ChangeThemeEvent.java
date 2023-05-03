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

package stroom.editor.client.presenter;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ChangeThemeEvent extends GwtEvent<ChangeThemeEvent.Handler> {

    private static Type<Handler> TYPE;
    private final String theme;
    private final String editorTheme;
    private final String editorKeyBindings;

    private ChangeThemeEvent(final String theme,
                             final String editorTheme,
                             final String editorKeyBindings) {
        this.theme = theme;
        this.editorTheme = editorTheme;
        this.editorKeyBindings = editorKeyBindings;
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    public static void fire(final HasHandlers handlers,
                            final String theme,
                            final String editorTheme,
                            final String editorKeyBindings) {
        handlers.fireEvent(new ChangeThemeEvent(theme, editorTheme, editorKeyBindings));
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
        return theme;
    }

    public String getEditorTheme() {
        return editorTheme;
    }

    public String getEditorKeyBindings() {
        return editorKeyBindings;
    }

    public interface Handler extends EventHandler {

        void onChange(ChangeThemeEvent event);
    }
}