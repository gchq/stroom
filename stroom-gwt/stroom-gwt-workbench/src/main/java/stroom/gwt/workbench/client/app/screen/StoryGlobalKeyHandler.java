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


package stroom.gwt.workbench.client.app.screen;

import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.inject.Inject;

/// The [GlobalKeyHandler] of a harness's injector, in place of Stroom's `GlobalKeyHandlerImpl`
/// (in the app shell's `stroom.main` module), which runs the app's keyboard shortcuts (e.g.
/// `ctrl+s` to save the selected tab). A story has no app shell, so the keys editors pass on
/// to it do nothing.
public final class StoryGlobalKeyHandler implements GlobalKeyHandler {

    /// Creates the handler.
    @Inject
    public StoryGlobalKeyHandler() {
        super();
    }

    /// Does nothing.
    ///
    /// @param event The key event.
    @Override
    public void onKeyDown(final KeyDownEvent event) {
        // No app shortcuts in a story
    }

    /// Does nothing.
    ///
    /// @param event The key event.
    @Override
    public void onKeyUp(final KeyUpEvent event) {
        // No app shortcuts in a story
    }
}
