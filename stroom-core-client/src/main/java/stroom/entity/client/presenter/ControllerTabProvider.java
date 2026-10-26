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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.component.TabbedController.TabReader;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerView;

import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/// One of a document's tabs whose content is a controller, for a document still shown by a
/// [DocTabPresenter], which takes only presenters: it shows the controller through a small
/// [DocPresenter] that owns it, as `TabbedController.addTab` does for a document's editor.
///
/// The controller is made when the tab is first shown and read into whenever the document is. It
/// sees the document as a part of it does: whether it may be changed
/// ([stroom.widget.component.client.Contexts#READ_ONLY]), which document it is, and it tells the
/// document of changes with `markChanged()`.
///
/// ```java
/// addTab(SETTINGS, new ControllerTabProvider<>(getEventBus(), settingsProvider::get,
///         DataGenSettingsController::read, DataGenSettingsController::write));
/// ```
///
/// @param <C> The controller's type.
/// @param <D> The document's type.
public final class ControllerTabProvider<C extends Controller, D> implements TabProvider<D> {

    private final EventBus eventBus;
    private final Supplier<C> make;
    private final TabReader<C, D> reader;
    private final BiFunction<C, D, D> writer;
    private Host host;

    /// A tab that only shows the document.
    ///
    /// @param eventBus The application's event bus.
    /// @param make     Makes the controller, e.g. a GIN provider's `get`.
    /// @param reader   Reads the document into it.
    public ControllerTabProvider(final EventBus eventBus,
                                 final Supplier<C> make,
                                 final TabReader<C, D> reader) {
        this(eventBus, make, reader, (content, document) -> document);
    }

    /// A tab that shows and changes part of the document.
    ///
    /// @param eventBus The application's event bus.
    /// @param make     Makes the controller, e.g. a GIN provider's `get`.
    /// @param reader   Reads the document into it.
    /// @param writer   Writes what it shows into a copy of the document.
    public ControllerTabProvider(final EventBus eventBus,
                                 final Supplier<C> make,
                                 final TabReader<C, D> reader,
                                 final BiFunction<C, D, D> writer) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.make = Objects.requireNonNull(make, "make");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public DocPresenter<?, D> getPresenter() {
        if (host == null) {
            host = new Host(make.get());
        }
        return host;
    }

    @Override
    public void read(final DocRef docRef, final D document) {
        getPresenter().read(docRef, document);
    }

    @Override
    public D write(final D document) {
        return getPresenter().write(document);
    }

    @Override
    public void onClose() {
        getPresenter().onClose();
    }

    // The presenter the document shows the controller through, which owns it
    private final class Host extends DocPresenter<ControllerView, D> {

        private final C content;

        private Host(final C content) {
            super(eventBus, new ControllerView(content));
            this.content = own(content);
        }

        @Override
        protected void onRead(final DocRef docRef, final D document) {
            reader.read(content, docRef, document);
        }

        @Override
        protected D onWrite(final D document) {
            return writer.apply(content, document);
        }
    }
}
