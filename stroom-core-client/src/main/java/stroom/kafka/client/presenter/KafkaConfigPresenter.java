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

package stroom.kafka.client.presenter;

import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.kafka.shared.KafkaConfigResource;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.ResourceGeneration;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class KafkaConfigPresenter extends DocumentEditTabPresenter<LinkTabPanelView, KafkaConfigDoc> {

    private static final KafkaConfigResource KAFKA_CONFIG_RESOURCE = GWT.create(KafkaConfigResource.class);
    private static final TabData CONFIG = new TabDataImpl("Config");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final EditorPresenter editorPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    private final ButtonView downloadButton;
    private final RestFactory restFactory;
    private final LocationManager locationManager;

    private DocRef docRef;

    @Inject
    public KafkaConfigPresenter(final EventBus eventBus,
                                final LinkTabPanelView view,
                                final EditorPresenter editorPresenter,
                                final MarkdownEditPresenter markdownEditPresenter,
                                final RestFactory restFactory,
                                final LocationManager locationManager) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;
        this.markdownEditPresenter = markdownEditPresenter;
        this.restFactory = restFactory;
        this.locationManager = locationManager;

        editorPresenter.setMode(AceEditorMode.PROPERTIES);

        downloadButton = SvgButton.create(SvgPresets.DOWNLOAD);
        toolbar.addButton(downloadButton);

        addTab(CONFIG);
        addTab(DOCUMENTATION);
        selectTab(CONFIG);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(editorPresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(downloadButton.addClickHandler(clickEvent -> {
            final Rest<ResourceGeneration> rest = restFactory.create();
            rest
                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                    .call(KAFKA_CONFIG_RESOURCE)
                    .download(docRef);
        }));
    }


    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (CONFIG.equals(tab)) {
            callback.onReady(editorPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final KafkaConfigDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        this.docRef = docRef;
        downloadButton.setEnabled(true);

        if (doc.getData() != null) {
            editorPresenter.setText(doc.getData());
        }
        editorPresenter.setReadOnly(readOnly);
        editorPresenter.getFormatAction().setAvailable(!readOnly);

        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected KafkaConfigDoc onWrite(KafkaConfigDoc doc) {
        doc.setData(editorPresenter.getText());
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return KafkaConfigDoc.DOCUMENT_TYPE;
    }
}
