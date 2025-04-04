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
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class TextConverterPresenter extends DocumentEditTabPresenter<LinkTabPanelView, TextConverterDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData CONVERSION = new TabDataImpl("Conversion");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public TextConverterPresenter(final EventBus eventBus,
                                  final LinkTabPanelView view,
                                  final Provider<TextConverterSettingsPresenter> settingsPresenterProvider,
                                  final Provider<EditorPresenter> editorPresenterProvider,
                                  final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                                  final DocumentUserPermissionsTabProvider<TextConverterDoc>
                                          documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(CONVERSION, new AbstractTabProvider<TextConverterDoc, EditorPresenter>(eventBus) {

            @Override
            public EditorPresenter createPresenter() {
                final EditorPresenter editorPresenter = editorPresenterProvider.get();
                editorPresenter.setReadOnly(isReadOnly());
                editorPresenter.getFormatAction().setAvailable(!isReadOnly());
                setEditorMode(editorPresenter, getEntity());

                NullSafe.consume(
                        getEntity(),
                        TextConverterDoc::getData,
                        editorPresenter::setText);


                registerHandler(editorPresenter.addValueChangeHandler(event -> fireDirtyEvent(true)));
                registerHandler(editorPresenter.addFormatHandler(event -> fireDirtyEvent(true)));
                return editorPresenter;
            }

            @Override
            public void onRead(final EditorPresenter presenter,
                               final DocRef docRef,
                               final TextConverterDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getData());
                presenter.setReadOnly(readOnly);
                presenter.getFormatAction().setAvailable(!readOnly);
                setEditorMode(presenter, document);
            }

            @Override
            public TextConverterDoc onWrite(final EditorPresenter presenter, final TextConverterDoc document) {
                document.setData(presenter.getText());
                return document;
            }
        });

        addTab(SETTINGS, new DocumentEditTabProvider<>(settingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<TextConverterDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final TextConverterDoc document,
                               final boolean readOnly) {


                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public TextConverterDoc onWrite(final MarkdownEditPresenter presenter,
                                            final TextConverterDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(CONVERSION);
    }

    private void setEditorMode(final EditorPresenter editorPresenter,
                               final TextConverterDoc document) {
        final TextConverterType converterType = NullSafe.get(
                document,
                TextConverterDoc::getConverterType);
        // Fall back option if we don't know the type
        AceEditorMode mode = AceEditorMode.STROOM_COMBINED_PARSER;
        if (converterType != null && TextConverterType.NONE != converterType) {
            //noinspection EnhancedSwitchMigration
            switch (converterType) {
                case XML_FRAGMENT:
                    mode = AceEditorMode.STROOM_FRAGMENT_PARSER;
                    break;
                case DATA_SPLITTER:
                    mode = AceEditorMode.STROOM_DATA_SPLITTER;
                    break;
            }
        } else {
            final String code = editorPresenter.getText();
            if (!NullSafe.isBlankString(code)) {
                if (code.contains("dataSplitter")) {
                    mode = AceEditorMode.STROOM_DATA_SPLITTER;
                } else if (code.contains("!ENTITY")) {
                    mode = AceEditorMode.STROOM_FRAGMENT_PARSER;
                }
            }
        }
        GWT.log("Setting editor mode to " + mode);
        editorPresenter.setMode(mode);
    }

    @Override
    public String getType() {
        return TextConverterDoc.TYPE;
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
