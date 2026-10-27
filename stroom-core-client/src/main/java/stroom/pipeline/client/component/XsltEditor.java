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

package stroom.pipeline.client.component;

import stroom.entity.client.component.CodeTabController;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// An XSLT's editor: its XSLT, Documentation and Permissions tabs (see [DocumentEditor]).
public class XsltEditor extends DocumentEditor<XsltDoc> {

    private static final TabData XSLT = new TabDataImpl("XSLT");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its XSLT, Documentation and Permissions tabs.
    @Inject
    public XsltEditor(final EventBus eventBus,
                      final LinkTabPanel view,
                      final Provider<CodeTabController> codeTabProvider,
                      final Provider<MarkdownEditController> markdownEditProvider,
                      final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        addTab(XSLT,
                () -> {
                    final CodeTabController xslt = codeTabProvider.get();
                    xslt.setMode(AceEditorMode.XML);
                    xslt.setOnChange(this::onChange);
                    return xslt;
                },
                (xslt, docRef, doc) -> xslt.setText(doc.getData()),
                (xslt, doc) -> doc.copy().data(xslt.getText()).build());
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                XsltDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        selectTab(XSLT);
    }
}
