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
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter;
import stroom.widget.xsdbrowser.client.view.XSDModel;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class XMLSchemaPresenter extends DocumentEditTabPresenter<LinkTabPanelView, XmlSchemaDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData GRAPHICAL = new TabDataImpl("Graphical");
    private static final TabData TEXT = new TabDataImpl("Text");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final XSDBrowserPresenter xsdBrowserPresenter;
    private final EditorPresenter codePresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    private final XSDModel data = new XSDModel();
    private final XMLSchemaSettingsPresenter settingsPresenter;
    private boolean shownText;
    private boolean updateDiagram;

    @Inject
    public XMLSchemaPresenter(final EventBus eventBus,
                              final LinkTabPanelView view,
                              final XMLSchemaSettingsPresenter settingsPresenter,
                              final XSDBrowserPresenter xsdBrowserPresenter,
                              final EditorPresenter codePresenter,
                              final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.xsdBrowserPresenter = xsdBrowserPresenter;
        this.codePresenter = codePresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        codePresenter.getIndicatorsOption().setAvailable(false);
        codePresenter.getIndicatorsOption().setOn(false);
        codePresenter.getLineNumbersOption().setAvailable(true);
        codePresenter.getLineNumbersOption().setOn(true);

        xsdBrowserPresenter.setModel(data);

        addTab(GRAPHICAL);
        addTab(TEXT);
        addTab(SETTINGS);
        addTab(DOCUMENTATION);
        selectTab(GRAPHICAL);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (GRAPHICAL.equals(tab)) {
            callback.onReady(xsdBrowserPresenter);
        } else if (TEXT.equals(tab)) {
            callback.onReady(codePresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    protected void afterSelectTab(final PresenterWidget<?> content) {
        if (content != null) {
            if (content.equals(xsdBrowserPresenter)) {
                if (updateDiagram) {
                    updateDiagram = false;
                    if (shownText) {
                        data.setContents(codePresenter.getText());
                    } else {
                        data.setContents(getEntity().getData());
                    }
                }
            } else if (content.equals(codePresenter)) {
                if (!shownText) {
                    shownText = true;
                    codePresenter.setMode(AceEditorMode.XML);
                    codePresenter.getFormatAction().setAvailable(!isReadOnly());
                    codePresenter.setText(getEntity().getData(), true);
                }
            }
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final XmlSchemaDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        settingsPresenter.read(docRef, doc, readOnly);

        shownText = false;
        updateDiagram = false;

        data.setContents(doc.getData());

        codePresenter.setReadOnly(readOnly);
        codePresenter.getFormatAction().setAvailable(!readOnly);

        if (!readOnly) {
            // Enable controls based on user permission
            registerHandler(codePresenter.addValueChangeHandler(event -> {
                setDirty(true);
                updateDiagram = true;
            }));
        }

        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected XmlSchemaDoc onWrite(XmlSchemaDoc doc) {
        doc = settingsPresenter.write(doc);
        if (shownText) {
            doc.setData(codePresenter.getText().trim());
        }
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return XmlSchemaDoc.DOCUMENT_TYPE;
    }
}
