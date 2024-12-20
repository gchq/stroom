/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.xmlschema.client.presenter;

import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter;
import stroom.widget.xsdbrowser.client.view.XSDModel;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class XMLSchemaPresenter extends DocumentEditTabPresenter<LinkTabPanelView, XmlSchemaDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData GRAPHICAL = new TabDataImpl("Graphical");
    private static final TabData TEXT = new TabDataImpl("Text");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private XSDBrowserPresenter xsdBrowserPresenter;
    private EditorPresenter codePresenter;
    private final XSDModel data = new XSDModel();
    private boolean updateDiagram;

    @Inject
    public XMLSchemaPresenter(final EventBus eventBus,
                              final LinkTabPanelView view,
                              final Provider<XMLSchemaSettingsPresenter> settingsPresenterProvider,
                              final Provider<XSDBrowserPresenter> xsdBrowserPresenterProvider,
                              final Provider<EditorPresenter> codePresenterProvider,
                              final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                              final DocumentUserPermissionsTabProvider<XmlSchemaDoc>
                                      documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(GRAPHICAL, new AbstractTabProvider<XmlSchemaDoc, XSDBrowserPresenter>(eventBus) {
            @Override
            protected XSDBrowserPresenter createPresenter() {
                xsdBrowserPresenter = xsdBrowserPresenterProvider.get();
                xsdBrowserPresenter.setModel(data);
                return xsdBrowserPresenter;
            }
        });
        addTab(TEXT, new AbstractTabProvider<XmlSchemaDoc, EditorPresenter>(eventBus) {
            @Override
            protected EditorPresenter createPresenter() {
                codePresenter = codePresenterProvider.get();
                codePresenter.setMode(AceEditorMode.XML);
                codePresenter.getIndicatorsOption().setAvailable(false);
                codePresenter.getIndicatorsOption().setOn(false);
                codePresenter.getLineNumbersOption().setAvailable(true);
                codePresenter.getLineNumbersOption().setOn(true);
                codePresenter.setReadOnly(isReadOnly());
                codePresenter.getFormatAction().setAvailable(!isReadOnly());
                return codePresenter;
            }

            @Override
            protected void onBind() {
                super.onBind();
            }

            @Override
            public void onRead(final EditorPresenter presenter,
                               final DocRef docRef,
                               final XmlSchemaDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getData(), true);
                if (!readOnly) {
                    // Enable controls based on user permission
                    registerHandler(presenter.addValueChangeHandler(event -> {
                        setDirty(true);
                        updateDiagram = true;
                    }));
                }
            }

            @Override
            public XmlSchemaDoc onWrite(final EditorPresenter presenter, final XmlSchemaDoc document) {
                document.setData(presenter.getText().trim());
                return document;
            }
        });
        addTab(SETTINGS, new DocumentEditTabProvider<>(settingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<XmlSchemaDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final XmlSchemaDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public XmlSchemaDoc onWrite(final MarkdownEditPresenter presenter,
                                        final XmlSchemaDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(GRAPHICAL);
    }

    @Override
    protected void afterSelectTab(final PresenterWidget<?> content) {
        if (content != null) {
            if (content.equals(xsdBrowserPresenter)) {
                if (updateDiagram) {
                    updateDiagram = false;
                    if (codePresenter != null) {
                        data.setContents(codePresenter.getText());
                    } else {
                        data.setContents(getEntity().getData());
                    }
                }
            }
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final XmlSchemaDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        data.setContents(doc.getData());
    }

    @Override
    public String getType() {
        return XmlSchemaDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }
}
