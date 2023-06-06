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

package stroom.pipeline.client.presenter;

import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.pipeline.shared.XsltDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class XsltPresenter extends DocumentEditTabPresenter<LinkTabPanelView, XsltDoc> {

    private static final TabData XSLT = new TabDataImpl("XSLT");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final EditorPresenter codePresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    @Inject
    public XsltPresenter(final EventBus eventBus,
                         final LinkTabPanelView view,
                         final EditorPresenter codePresenter,
                         final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.codePresenter = codePresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        codePresenter.setMode(AceEditorMode.XML);

        addTab(XSLT);
        addTab(DOCUMENTATION);
        selectTab(XSLT);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (XSLT.equals(tab)) {
            callback.onReady(codePresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }


    @Override
    public void onRead(final DocRef docRef, final XsltDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);

        codePresenter.setText(doc.getData());
        codePresenter.setReadOnly(readOnly);
        codePresenter.getFormatAction().setAvailable(!readOnly);

        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected XsltDoc onWrite(XsltDoc doc) {
        doc.setData(codePresenter.getText());
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return XsltDoc.DOCUMENT_TYPE;
    }
}
