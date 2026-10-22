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

package stroom.widget.presenter.client;

import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the screen that is the whole page ([RevealRootContentEvent]), in place of the one before.
/// An application binds it as an eager singleton.
@Singleton
public class RootPresenter {

    // The screen that is the whole page, for MyPresenterWidget#isRevealed
    private static MyPresenterWidget<?> rootContent;

    @Inject
    public RootPresenter(final EventBus eventBus) {
        eventBus.addHandler(RevealRootContentEvent.getType(), event -> show(event.getContent()));
    }

    private static void show(final MyPresenterWidget<?> content) {
        RootLayoutPanel.get().clear();
        RootPanel.get().clear();
        setRootContent(content);
        if (content != null) {
            RootPanel.get().add(content.getWidget());
        }
    }

    // Also for tests, which can't show a page
    static void setRootContent(final MyPresenterWidget<?> content) {
        rootContent = content;
    }

    /// @param presenter A presenter.
    /// @return Whether it is the whole page.
    static boolean isRootContent(final MyPresenterWidget<?> presenter) {
        return presenter != null && presenter == rootContent;
    }
}
