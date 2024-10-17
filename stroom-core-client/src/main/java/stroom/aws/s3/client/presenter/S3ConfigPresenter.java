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

package stroom.aws.s3.client.presenter;

import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.aws.s3.shared.S3ConfigResource;
import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class S3ConfigPresenter extends DocumentEditTabPresenter<LinkTabPanelView, S3ConfigDoc> {

    private static final S3ConfigResource S3_CONFIG_RESOURCE = GWT.create(S3ConfigResource.class);
    private static final TabData CONFIG = new TabDataImpl("Config");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final ButtonView downloadButton;
    private final RestFactory restFactory;
    private final LocationManager locationManager;

    private DocRef docRef;

    @Inject
    public S3ConfigPresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final Provider<EditorPresenter> editorPresenterProvider,
                             final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                             final RestFactory restFactory,
                             final LocationManager locationManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.locationManager = locationManager;

        downloadButton = SvgButton.create(SvgPresets.DOWNLOAD);
        toolbar.addButton(downloadButton);

        addTab(CONFIG, new AbstractTabProvider<S3ConfigDoc, EditorPresenter>(eventBus) {
            @Override
            protected EditorPresenter createPresenter() {
                final EditorPresenter editorPresenter = editorPresenterProvider.get();
                editorPresenter.setMode(AceEditorMode.PROPERTIES);
                registerHandler(editorPresenter.addValueChangeHandler(event -> setDirty(true)));
                registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
                return editorPresenter;
            }

            @Override
            public void onRead(final EditorPresenter presenter,
                               final DocRef docRef,
                               final S3ConfigDoc document,
                               final boolean readOnly) {
                presenter.setReadOnly(readOnly);
                presenter.getFormatAction().setAvailable(!readOnly);

                if (document.getData() != null) {
                    presenter.setText(document.getData());
                }
            }

            @Override
            public S3ConfigDoc onWrite(final EditorPresenter presenter, final S3ConfigDoc document) {
                document.setData(presenter.getText());
                return document;
            }
        });
        addTab(DOCUMENTATION, new MarkdownTabProvider<S3ConfigDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final S3ConfigDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public S3ConfigDoc onWrite(final MarkdownEditPresenter presenter,
                                       final S3ConfigDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        selectTab(CONFIG);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(downloadButton.addClickHandler(clickEvent -> {
            restFactory
                    .create(S3_CONFIG_RESOURCE)
                    .method(res -> res.download(docRef))
                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                    .taskMonitorFactory(this)
                    .exec();
        }));
    }

    @Override
    public void onRead(final DocRef docRef, final S3ConfigDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        this.docRef = docRef;
        downloadButton.setEnabled(true);
    }

    @Override
    public String getType() {
        return S3ConfigDoc.DOCUMENT_TYPE;
    }
}
