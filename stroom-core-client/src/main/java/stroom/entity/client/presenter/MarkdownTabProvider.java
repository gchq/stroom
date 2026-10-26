/*
 * Copyright 2023 Crown Copyright
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
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.widget.component.client.ControllerView;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// A document's Documentation tab ([MarkdownEditController]), for a document still shown by a
/// [DocTabPresenter]: it shows the controller through a small [DocPresenter] that owns it, as
/// [ControllerTabProvider] does, and passes on the buttons it adds to the document's toolbar.
///
/// The controller is made when the tab is first shown. A document reads its documentation into it
/// and writes it out by overriding [#onRead] and [#onWrite].
///
/// @param <E> The document's type.
public abstract class MarkdownTabProvider<E> implements TabProvider<E> {

    private final EventBus eventBus;
    private final Supplier<MarkdownEditController> markdownEditProvider;
    private Host host;

    /// @param eventBus             The application's event bus.
    /// @param markdownEditProvider Makes the tab's controller.
    public MarkdownTabProvider(final EventBus eventBus,
                               final Supplier<MarkdownEditController> markdownEditProvider) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.markdownEditProvider = Objects.requireNonNull(markdownEditProvider, "markdownEditProvider");
    }

    @Override
    public final DocPresenter<?, E> getPresenter() {
        if (host == null) {
            // It tells the document of changes itself (Contexts.DIRTY)
            host = new Host(markdownEditProvider.get());
        }
        return host;
    }

    @Override
    public final void read(final DocRef docRef, final E document) {
        getPresenter().read(docRef, document);
    }

    @Override
    public final E write(final E document) {
        return getPresenter().write(document);
    }

    @Override
    public void onClose() {
        getPresenter().onClose();
    }

    /// Reads the document's documentation into the tab.
    ///
    /// @param markdownEdit The tab's controller.
    /// @param docRef       The document.
    /// @param document     The document.
    public void onRead(final MarkdownEditController markdownEdit, final DocRef docRef, final E document) {
    }

    /// Writes the tab's documentation into a copy of the document.
    ///
    /// @param markdownEdit The tab's controller.
    /// @param document     The document.
    /// @return The copy.
    public E onWrite(final MarkdownEditController markdownEdit, final E document) {
        return document;
    }


    // --------------------------------------------------------------------------------


    // The presenter the document shows the controller through, which owns it, with the
    // controller's buttons
    private final class Host extends DocPresenter<ControllerView, E> implements HasToolbar {

        private final MarkdownEditController content;

        private Host(final MarkdownEditController content) {
            super(eventBus, new ControllerView(content));
            this.content = own(content);
        }

        @Override
        protected void onRead(final DocRef docRef, final E document) {
            MarkdownTabProvider.this.onRead(content, docRef, document);
        }

        @Override
        protected E onWrite(final E document) {
            return MarkdownTabProvider.this.onWrite(content, document);
        }

        @Override
        public List<Widget> getDocumentButtons() {
            return content.getDocumentButtons();
        }

        @Override
        public List<Widget> getToolbars() {
            return content.getToolbars();
        }
    }
}
