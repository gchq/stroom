/*
 * Copyright 2016 Crown Copyright
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

package stroom.aws.s3.client.component;

import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.aws.s3.shared.S3ConfigResource;
import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.component.CodeTabController;
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
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// An S3 configuration's editor: its Config (properties), Documentation and Permissions tabs, with
/// Download beside Save and Save As (see [DocumentEditor]).
public class S3ConfigEditor extends DocumentEditor<S3ConfigDoc> {

    private static final S3ConfigResource S3_CONFIG_RESOURCE = GWT.create(S3ConfigResource.class);
    private static final TabData CONFIG = new TabDataImpl("Config");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final ToolbarButton downloadButton;

    /// Makes the editor, with its Config, Documentation and Permissions tabs.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 Its component.
    /// @param codeTabProvider      Makes the code tab.
    /// @param documentationTabProvider Makes the Documentation tab's editor.
    /// @param permissionsProvider  Makes the Permissions tab.
    /// @param restFactory          Makes REST requests.
    /// @param locationManager      The browser's location.
    @Inject
    public S3ConfigEditor(final EventBus eventBus,
                          final LinkTabPanel view,
                          final Provider<CodeTabController> codeTabProvider,
                          final Provider<DocumentationTabController> documentationTabProvider,
                          final Provider<DocumentUserPermissionsTabController> permissionsProvider,
                          final RestFactory restFactory,
                          final LocationManager locationManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        downloadButton = getToolbar().button(SvgImage.DOWNLOAD, "Download S3 Configuration", this::download);
        downloadButton.setEnabled(false);

        addTab(CONFIG,
                () -> {
                    final CodeTabController config = codeTabProvider.get();
                    config.setMode(AceEditorMode.PROPERTIES);
                    config.setOnChange(this::onChange);
                    return config;
                },
                (config, docRef, doc) -> config.setText(doc.getData()),
                (config, doc) -> doc.copy().data(config.getText()).build());
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                S3ConfigDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(CONFIG);
    }

    @Override
    protected void onRead(final DocRef docRef, final S3ConfigDoc doc) {
        downloadButton.setEnabled(true);
    }

    private void download() {
        final DocRef docRef = getDocRef();
        restFactory
                .create(S3_CONFIG_RESOURCE)
                .method(resource -> resource.download(docRef))
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                .owner(this)
                .exec();
    }
}
