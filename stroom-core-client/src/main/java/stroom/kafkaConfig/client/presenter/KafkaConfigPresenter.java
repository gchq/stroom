/*
 * Copyright 2019 Crown Copyright
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

package stroom.kafkaConfig.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.kafkaConfig.shared.KafkaConfigDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import javax.inject.Provider;

public class KafkaConfigPresenter extends DocumentEditTabPresenter<LinkTabPanelView, KafkaConfigDoc> {
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData CONFIG_TAB = new TabDataImpl("Config");

    private final KafkaConfigSettingsPresenter settingsPresenter;
    private final Provider<EditorPresenter> editorPresenterProvider;

    private EditorPresenter editorPresenter;
    private boolean readOnly = true;

    private int loadCount;

    @Inject
    public KafkaConfigPresenter(final EventBus eventBus, final LinkTabPanelView view,
                           final KafkaConfigSettingsPresenter settingsPresenter, final ClientSecurityContext securityContext, final Provider<EditorPresenter> editorPresenterProvider) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.editorPresenterProvider = editorPresenterProvider;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(CONFIG_TAB);
        addTab(SETTINGS_TAB);
        selectTab(CONFIG_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (CONFIG_TAB.equals(tab)) {
            callback.onReady(getOrCreateCodePresenter());
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final KafkaConfigDoc doc) {
        super.onRead(docRef, doc);
        loadCount++;
        settingsPresenter.read(docRef, doc);

        if (editorPresenter != null && doc.getData() != null) {
            editorPresenter.setText(doc.getData());
        }
    }

    @Override
    protected void onWrite(final KafkaConfigDoc doc) {
        settingsPresenter.write(doc);
        if (editorPresenter != null) {
            doc.setData(editorPresenter.getText());
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        this.readOnly = readOnly;
        settingsPresenter.onReadOnly(readOnly);
        if (editorPresenter != null) {
            editorPresenter.setReadOnly(readOnly);
            editorPresenter.getContextMenu().setShowFormatOption(!readOnly);
        }
    }

    @Override
    public String getType() {
        return KafkaConfigDoc.DOCUMENT_TYPE;
    }

    private EditorPresenter getOrCreateCodePresenter() {
        if (editorPresenter == null) {
            editorPresenter = editorPresenterProvider.get();
;
            editorPresenter.setMode(AceEditorMode.PROPERTIES);
            registerHandler(editorPresenter.addValueChangeHandler(event -> setDirty(true)));
            registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
            editorPresenter.setReadOnly(readOnly);
            editorPresenter.getContextMenu().setShowFormatOption(!readOnly);
            if (getEntity() != null && getEntity().getData() != null) {
                editorPresenter.setText(getEntity().getData());
            }
        }
        return editorPresenter;
    }
}
