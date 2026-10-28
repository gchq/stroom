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

package stroom.widget.component.client;


import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;

/// The browser page, which shows one screen: the app's core screen, or a sign in page.
public final class Page {

    private Page() {
        // Static utility
    }

    /// Shows a screen as the whole page, in place of what the page held.
    ///
    /// @param content The screen, a component's controller or a presenter.
    public static void show(final Controller content) {
        RootLayoutPanel.get().clear();
        RootPanel.get().clear();
        RootPanel.get().add(content.asWidget());
    }
}
