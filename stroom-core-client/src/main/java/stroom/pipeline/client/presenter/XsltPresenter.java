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

package stroom.pipeline.client.presenter;

import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class XsltPresenter extends DocTabPresenter<LinkTabPanelView, XsltDoc> {

    private static final TabData XSLT = new TabDataImpl("XSLT");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public XsltPresenter(final EventBus eventBus,
                         final LinkTabPanelView view,
                         final Provider<EditorPresenter> editorPresenterProvider,
                         final Provider<MarkdownEditController> markdownEditProvider,
                         final DocumentUserPermissionsTabProvider<XsltDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(XSLT, new AbstractTabProvider<XsltDoc, EditorPresenter>() {
            @Override
            protected EditorPresenter createPresenter() {
                final EditorPresenter editorPresenter = editorPresenterProvider.get();
                editorPresenter.setMode(AceEditorMode.XML);
                own(editorPresenter.addValueChangeHandler(event -> onChange()));
                own(editorPresenter.addFormatHandler(event -> onChange()));
                return editorPresenter;
            }

            @Override
            public void onRead(final EditorPresenter presenter,
                               final DocRef docRef,
                               final XsltDoc document) {
//                presenter.getBasicAutoCompletionOption().setOn();
//                presenter.getSnippetsOption().setOn();
//                presenter.deRegisterCompletionProviders();


                presenter.setText(document.getData());
                presenter.setReadOnly(isReadOnly());
                presenter.getFormatAction().setAvailable(!isReadOnly());
            }

            @Override
            public XsltDoc onWrite(final EditorPresenter presenter, final XsltDoc document) {
                return document.copy().data(presenter.getText()).build();
            }
        });
        addTab(DOCUMENTATION, new MarkdownTabProvider<XsltDoc>(getEventBus(), markdownEditProvider) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final XsltDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public XsltDoc onWrite(final MarkdownEditController markdownEdit,
                                   final XsltDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(XSLT);
    }

    @Override
    public String getType() {
        return XsltDoc.TYPE;
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
