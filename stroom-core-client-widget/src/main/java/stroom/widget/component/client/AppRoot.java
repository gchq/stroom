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


import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Objects;

/// The owner of what lasts as long as the page and isn't part of a screen: the dialogs of the
/// plugins, the managers (e.g. `TabSessionManager`) and the app-wide helpers (e.g. `CurrentUser`),
/// which are singletons rather than controllers. Inject it, and show a dialog for one use from it:
///
/// ```java
/// ControllerDialog.showOnce(appRoot, frameProvider.get()).caption("Page").fire();
/// ```
///
/// A dialog left open is then still owned, so it shows in the ownership tree's leak counts, rather
/// than being an unowned controller.
@Singleton
public final class AppRoot implements HasControllerNode {

    private final ControllerNode node = ControllerNode.root("App");

    /// Makes the app root; GIN makes one per injector.
    @Inject
    public AppRoot() {
        // Nothing to set up: the root is made with the field
    }

    @Override
    public ControllerNode getControllerNode() {
        return node;
    }

    /// Makes a dialog for one use, owned by the app root until it is hidden, then disposed (once the
    /// popup manager has hidden it).
    ///
    /// @param provider What makes it, e.g. a GIN provider.
    /// @param <C>      Its type.
    /// @return The dialog, made and owned, ready to show.
    public <C extends Controller> C makeDialog(final Provider<C> provider) {
        Objects.requireNonNull(provider, "provider");
        final C dialog = provider.get();
        node.adopt(dialog.getControllerNode());
        return ControllerDialog.disposeWhenHidden(dialog);
    }

    /// Makes something the app keeps once made, e.g. a dialog shown again and again that remembers
    /// what it last showed, owned by the app root for as long as the page.
    ///
    /// @param provider What makes it, e.g. a GIN provider.
    /// @param <C>      Its type.
    /// @return It, made and owned.
    public <C extends Controller> C make(final Provider<C> provider) {
        Objects.requireNonNull(provider, "provider");
        final C controller = provider.get();
        node.adopt(controller.getControllerNode());
        return controller;
    }
}
