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

package stroom.documentation.client.component;

import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.documentation.shared.DocumentationResource;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.DocumentationTabController;
import stroom.entity.client.component.LinkTabPanel;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A documentation document's editor: its Documentation (the document's text, in Markdown) and
/// Permissions tabs, with Download beside Save and Save As (see [DocumentEditor]).
public class DocumentationEditor extends DocumentEditor<DocumentationDoc> {

    private static final DocumentationResource DOCUMENTATION_RESOURCE = GWT.create(DocumentationResource.class);
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final ToolbarButton downloadButton;

    /// Makes the editor, with its Documentation and Permissions tabs.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 Its component.
    /// @param documentationTabProvider Makes the Documentation tab's editor.
    /// @param permissionsProvider  Makes the Permissions tab.
    /// @param restFactory          Makes REST requests.
    /// @param locationManager      The browser's location.
    @Inject
    public DocumentationEditor(final EventBus eventBus,
                               final LinkTabPanel view,
                               final Provider<DocumentationTabController> documentationTabProvider,
                               final Provider<DocumentUserPermissionsTabController> permissionsProvider,
                               final RestFactory restFactory,
                               final LocationManager locationManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        downloadButton = getToolbar().button(SvgImage.DOWNLOAD, "Download Documentation", this::download);
        downloadButton.setEnabled(false);

        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                DocumentationDoc::getData,
                (doc, data) -> doc.copy().data(data).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(DOCUMENTATION);
    }

    @Override
    protected void onRead(final DocRef docRef, final DocumentationDoc doc) {
        downloadButton.setEnabled(true);
    }

    private void download() {
        final DocRef docRef = getDocRef();
        restFactory
                .create(DOCUMENTATION_RESOURCE)
                .method(resource -> resource.download(docRef))
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                .owner(this)
                .exec();
    }
}
