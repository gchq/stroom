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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;

/// Registers handlers in [#onBind()] and removes them when unbound. One made by GIN is bound once it
/// is made; one made with `new` is bound by whoever makes it.
public class HandlerContainerImpl implements HandlerContainer {

    private final List<HandlerRegistration> registrations = new ArrayList<>();
    private boolean bound;

    @Override
    public final void bind() {
        if (!bound) {
            onBind();
            bound = true;
        }
    }

    @Override
    public final boolean isBound() {
        return bound;
    }

    @Override
    public final void unbind() {
        if (bound) {
            bound = false;
            for (final HandlerRegistration registration : registrations) {
                registration.removeHandler();
            }
            registrations.clear();
            onUnbind();
        }
    }

    /// Registers handlers ([#registerHandler(HandlerRegistration)]) when it is bound.
    protected void onBind() {
    }

    /// Lets go of anything else when it is unbound, after its registrations are removed.
    protected void onUnbind() {
    }

    /// Keeps a registration, removed when this is unbound.
    ///
    /// @param registration The registration.
    protected void registerHandler(final HandlerRegistration registration) {
        registrations.add(registration);
    }

    // GIN calls this once it has made and injected one
    @Inject
    final void automaticBind() {
        bind();
    }
}
