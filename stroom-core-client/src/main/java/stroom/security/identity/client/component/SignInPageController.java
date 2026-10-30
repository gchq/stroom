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

package stroom.security.identity.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Page;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.web.bindery.event.shared.EventBus;

/// A page Stroom shows in place of the app, before the user has signed in: the sign in, password
/// reset and authentication error pages. `App.onModuleLoad` shows one with [#show()].
public abstract class SignInPageController extends Controller {

    // Hides the host page's loading message, not just making it transparent, so screen readers
    // stop reading "Loading..." (app-page.css)
    private static final String LOADING_HIDDEN = "app-page__loading--hidden";

    /// @param eventBus The application's event bus.
    protected SignInPageController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Shows the page as the whole page, then does what it does once shown ([#onShown()]).
    public final void show() {
        Page.show(this);
        onShown();
    }

    /// Does what the page does once it is shown, e.g. hiding the host page's loading message and
    /// focusing the first field. A workbench story calls it once it has added the page to itself.
    public abstract void onShown();

    /// Hides the host page's loading message (its `#loading` element).
    protected final void hideLoading() {
        final Element loading = RootPanel.get("loading").getElement();
        loading.addClassName(LOADING_HIDDEN);
    }
}
