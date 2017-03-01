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

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.pipeline.shared.TextConverter;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.xmleditor.client.event.FormatEvent;
import stroom.xmleditor.client.event.FormatEvent.FormatHandler;
import stroom.xmleditor.client.presenter.BaseXMLEditorPresenter;
import stroom.xmleditor.client.presenter.ReadOnlyXMLEditorPresenter;
import stroom.xmleditor.client.presenter.XMLEditorPresenter;
import stroom.xmleditor.client.view.XMLEditorMenuPresenter;

public class TextConverterPresenter extends EntityEditTabPresenter<LinkTabPanelView, TextConverter> {
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData CONVERSION = new TabDataImpl("Conversion");
//    private static final TabData REFERENCES_TAB = new TabDataImpl("References");

    private final TextConverterSettingsPresenter settingsPresenter;
    private final XMLEditorMenuPresenter editorMenuPresenter;
//    private final EntityReferenceListPresenter entityReferenceListPresenter;
    private BaseXMLEditorPresenter codePresenter;

    @Inject
    public TextConverterPresenter(final EventBus eventBus, final LinkTabPanelView view,
                                  final TextConverterSettingsPresenter settingsPresenter,
                                  final XMLEditorMenuPresenter editorMenuPresenter,
//                                  final EntityReferenceListPresenter entityReferenceListPresenter,
                                  final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.editorMenuPresenter = editorMenuPresenter;
//        this.entityReferenceListPresenter = entityReferenceListPresenter;


        settingsPresenter.addDirtyHandler(new DirtyHandler() {
            @Override
            public void onDirty(final DirtyEvent event) {
                if (event.isDirty()) {
                    setDirty(true);
                }
            }
        });

        addTab(SETTINGS);
        addTab(CONVERSION);
//        addTab(REFERENCES_TAB);
        selectTab(SETTINGS);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (CONVERSION.equals(tab)) {
            callback.onReady(codePresenter);
//        } else if (REFERENCES_TAB.equals(tab)) {
//            entityReferenceListPresenter.read(getEntity());
//            callback.onReady(entityReferenceListPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final TextConverter textConverter) {
        settingsPresenter.read(textConverter);

        if (codePresenter != null) {
            codePresenter.setText(textConverter.getData());
        }
    }

    @Override
    protected void onWrite(final TextConverter textConverter) {
        settingsPresenter.write(textConverter);

        if (codePresenter != null) {
            textConverter.setData(codePresenter.getText());
        }
    }

    @Override
    protected void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);

        if (!readOnly) {
            codePresenter = new XMLEditorPresenter(getEventBus(), editorMenuPresenter);
        } else {
            codePresenter = new ReadOnlyXMLEditorPresenter(getEventBus(), editorMenuPresenter);
        }

        registerHandler(codePresenter.addKeyDownHandler(new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        }));
        registerHandler(codePresenter.addFormatHandler(new FormatHandler() {
            @Override
            public void onFormat(final FormatEvent event) {
                setDirty(true);
            }
        }));

        if (getEntity() != null) {
            codePresenter.setText(getEntity().getData());
        }
    }

    @Override
    public String getType() {
        return TextConverter.ENTITY_TYPE;
    }
}
