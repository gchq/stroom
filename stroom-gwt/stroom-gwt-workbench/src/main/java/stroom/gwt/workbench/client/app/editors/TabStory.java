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

package stroom.gwt.workbench.client.app.editors;

import stroom.docref.DocRef;
import stroom.entity.client.component.TabbedController.TabReader;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.DirtyTracker;

import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.BiFunction;

/// One tab of a document's editor shown on its own, for a story of that tab: it reads the document
/// into the tab, and writes it, as the editor does, and works out whether the document is dirty as
/// the editor does, from what the tab tells it ([Contexts#DIRTY]). It is read only until it is
/// told otherwise ([#setReadOnly(boolean)]). [DocEditors#open(stroom.gwt.workbench.client.app.screen.ScreenHarness,
/// DocRef, TabStory, DocEditors.DocResource)] opens one.
///
/// @param <C> The tab's controller's type.
/// @param <D> The document's type.
public final class TabStory<C extends Controller, D> extends Controller {

    private final C content;
    private final TabReader<C, D> reader;
    private final BiFunction<C, D, D> writer;
    private final ContextValue<Boolean> readOnly = ContextValue.of(true);
    private D document;
    private boolean dirty;

    /// @param eventBus The story's event bus.
    /// @param content  The tab, which this owns.
    /// @param reader   Reads the document into it.
    /// @param writer   Writes what it shows into a copy of the document.
    public TabStory(final EventBus eventBus,
                    final C content,
                    final TabReader<C, D> reader,
                    final BiFunction<C, D, D> writer) {
        super(eventBus);
        this.content = own(content);
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
        provide(Contexts.READ_ONLY, readOnly);
        provide(Contexts.DIRTY, ContextValue.<DirtyTracker>of(this::onChange));
        initWidget(content.asWidget());
    }

    /// @param readOnly Whether the document may not be changed.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly.set(readOnly);
    }

    /// Reads the document into the tab.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document.
    public void read(final DocRef docRef, final D document) {
        this.document = document;
        dirty = false;
        reader.read(content, docRef, document);
    }

    /// @param document The document to write into.
    /// @return A copy of it with what the tab shows.
    public D write(final D document) {
        return writer.apply(content, document);
    }

    /// @return The document as last read.
    public D getEntity() {
        return document;
    }

    /// @return Whether the tab shows changes that aren't saved.
    public boolean isDirty() {
        return dirty;
    }

    private void onChange() {
        if (document != null && !readOnly.get()) {
            dirty = !Objects.equals(document, write(document));
        }
    }
}
