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

package stroom.dictionary.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import javax.inject.Provider;

public class DictionaryPresenter extends DocumentEditTabPresenter<LinkTabPanelView, DictionaryDoc> {
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData WORDS_TAB = new TabDataImpl("Words");

    private final DictionarySettingsPresenter settingsPresenter;
    private final Provider<EditorPresenter> editorPresenterProvider;

    private EditorPresenter codePresenter;

    @Inject
    public DictionaryPresenter(final EventBus eventBus,
                               final LinkTabPanelView view,
                               final DictionarySettingsPresenter settingsPresenter,
                               final Provider<EditorPresenter> editorPresenterProvider,
                               final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.editorPresenterProvider = editorPresenterProvider;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(SETTINGS_TAB);
        addTab(WORDS_TAB);
        selectTab(SETTINGS_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (WORDS_TAB.equals(tab)) {
            callback.onReady(codePresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DictionaryDoc doc) {
        settingsPresenter.read(getDocRef(), doc);

        if (codePresenter != null) {
            codePresenter.setText(doc.getData());
        }
    }

    @Override
    protected void onWrite(final DictionaryDoc doc) {
        settingsPresenter.write(doc);

        if (codePresenter != null) {
            doc.setData(codePresenter.getText());
        }
    }

    @Override
    public void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);

        codePresenter = editorPresenterProvider.get();
        codePresenter.setReadOnly(readOnly);

        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));

        if (getEntity() != null) {
            codePresenter.setText(getEntity().getData());
        }
    }

    @Override
    public String getType() {
        return DictionaryDoc.ENTITY_TYPE;
    }
}