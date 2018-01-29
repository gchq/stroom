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

package stroom.dictionary.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.app.client.LocationManager;
import stroom.app.client.event.DirtyKeyDownHander;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DownloadDictionaryAction;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.shared.DocRefUtil;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class DictionaryPresenter extends EntityEditTabPresenter<LinkTabPanelView, Dictionary> {
    private static final TabData WORDS = new TabDataImpl("Words");

    private final TextAreaPresenter textAreaPresenter;
    private final ButtonView downloadButton;
    private final ClientDispatchAsync dispatcher;
    private final LocationManager locationManager;

    private Dictionary dictionary;

    @Inject
    public DictionaryPresenter(final EventBus eventBus,
                               final LinkTabPanelView view,
                               final TextAreaPresenter textAreaPresenter,
                               final ClientSecurityContext securityContext,
                               final ClientDispatchAsync dispatcher,
                               final LocationManager locationManager) {
        super(eventBus, view, securityContext);
        this.textAreaPresenter = textAreaPresenter;
        this.dispatcher = dispatcher;
        this.locationManager = locationManager;

        registerHandler(textAreaPresenter.addKeyDownHandler(new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        }));

        downloadButton = addButtonLeft(SvgPresets.DOWNLOAD);

        addTab(WORDS);
        selectTab(WORDS);
    }

    @java.lang.Override
    protected void onBind() {
        super.onBind();
        registerHandler(downloadButton.addClickHandler(clickEvent -> {
            dispatcher.exec(new DownloadDictionaryAction(DocRefUtil.create(dictionary))).onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, null, result));
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (WORDS.equals(tab)) {
            callback.onReady(textAreaPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    protected void onRead(final Dictionary dictionary) {
        this.dictionary = dictionary;
        downloadButton.setEnabled(true);
        textAreaPresenter.setText(dictionary.getData());
    }

    @Override
    protected void onWrite(final Dictionary dictionary) {
        dictionary.setData(textAreaPresenter.getText());
    }

    @Override
    public String getType() {
        return Dictionary.ENTITY_TYPE;
    }
}
