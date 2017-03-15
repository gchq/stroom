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

package stroom.pipeline.stepping.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import stroom.pipeline.shared.SteppingFilterSettings;
import stroom.editor.client.presenter.EditorPresenter;

public class ShowSteppingFilterSettingsEvent
        extends GwtEvent<ShowSteppingFilterSettingsEvent.ShowSteppingFilterSettingsHandler> {
    public interface ShowSteppingFilterSettingsHandler extends EventHandler {
        void onShow(ShowSteppingFilterSettingsEvent event);
    }

    private static Type<ShowSteppingFilterSettingsHandler> TYPE;

    private final EditorPresenter editor;
    private final boolean input;
    private final String elementId;
    private final SteppingFilterSettings settings;

    private ShowSteppingFilterSettingsEvent(final EditorPresenter editor, final boolean input,
                                            final String elementId, final SteppingFilterSettings settings) {
        this.editor = editor;
        this.input = input;
        this.elementId = elementId;
        this.settings = settings;
    }

    public static void fire(final HasHandlers source, final EditorPresenter editor, final boolean input,
                            final String elementId, final SteppingFilterSettings settings) {
        source.fireEvent(new ShowSteppingFilterSettingsEvent(editor, input, elementId, settings));
    }

    public static Type<ShowSteppingFilterSettingsHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<ShowSteppingFilterSettingsHandler>();
        }
        return TYPE;
    }

    @Override
    public Type<ShowSteppingFilterSettingsHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final ShowSteppingFilterSettingsHandler handler) {
        handler.onShow(this);
    }

    public EditorPresenter getEditor() {
        return editor;
    }

    public boolean isInput() {
        return input;
    }

    public String getElementId() {
        return elementId;
    }

    public SteppingFilterSettings getSettings() {
        return settings;
    }
}
