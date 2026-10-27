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

package stroom.document.asset.client.presenter;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.component.DocumentAssetsTabController;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.widget.component.client.ControllerView;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// A document's Assets tab ([DocumentAssetsTabController]), for a document still shown by a
/// [DocTabPresenter]: it shows the controller through a small [DocPresenter], with the buttons it
/// adds to the document's toolbar.
///
/// The document owns the controller, as it saves the assets with the document and counts them in
/// whether it is dirty, whether or not the tab has been shown.
///
/// @param <D> The document's type.
public final class DocumentAssetsTabProvider<D extends AbstractDoc> implements TabProvider<D> {

    private final EventBus eventBus;
    private final DocumentAssetsTabController<D> assetsTab;
    private Host host;

    /// @param eventBus  The application's event bus.
    /// @param assetsTab The tab's controller, owned by the document.
    public DocumentAssetsTabProvider(final EventBus eventBus,
                                     final DocumentAssetsTabController<D> assetsTab) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.assetsTab = Objects.requireNonNull(assetsTab, "assetsTab");
    }

    @Override
    public DocPresenter<?, D> getPresenter() {
        if (host == null) {
            host = new Host();
        }
        return host;
    }

    /// Tells whether a presenter is this tab's, without making it.
    ///
    /// @param presenter A presenter, e.g. the one a document has just shown.
    /// @return True if it is this tab's.
    public boolean isPresenter(final MyPresenterWidget<?> presenter) {
        return host != null && host == presenter;
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


    // --------------------------------------------------------------------------------


    // The presenter the document shows the controller through, with the controller's buttons
    private final class Host extends DocPresenter<ControllerView, D> implements HasToolbar {

        private Host() {
            super(eventBus, new ControllerView(assetsTab));
        }

        @Override
        protected void onRead(final DocRef docRef, final D document) {
            assetsTab.read(docRef, document);
        }

        @Override
        protected D onWrite(final D document) {
            // The assets are saved by the document's post-save callback (onSave)
            return document;
        }

        @Override
        public List<Widget> getDocumentButtons() {
            return assetsTab.getDocumentButtons();
        }
    }
}
