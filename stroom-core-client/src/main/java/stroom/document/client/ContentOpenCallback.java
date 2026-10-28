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

package stroom.document.client;

import stroom.widget.presenter.client.MyPresenterWidget;

import java.util.function.Consumer;

/// Tells an `OpenDocumentEvent`'s caller that a content screen (e.g. Jobs, opened from a saved tab
/// session) is open, whatever the screen is: a presenter is given to the callback, a controller as
/// null. Each opening of a tab session waits on this to open its next tab.
public final class ContentOpenCallback {

    private ContentOpenCallback() {
        // Static utility
    }

    /// @param callbackOnOpen The event's callback, or null for none.
    /// @param <T>            The type of the screens opened.
    /// @return What the content plugin runs with the screen it opened.
    public static <T> Consumer<T> of(final Consumer<MyPresenterWidget<?>> callbackOnOpen) {
        return screen -> {
            if (callbackOnOpen != null) {
                callbackOnOpen.accept(screen instanceof final MyPresenterWidget<?> presenter
                        ? presenter
                        : null);
            }
        };
    }
}
