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
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.pipeline.shared.XSLT;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.xmleditor.client.event.FormatEvent;
import stroom.xmleditor.client.event.FormatEvent.FormatHandler;
import stroom.xmleditor.client.presenter.BaseXMLEditorPresenter;
import stroom.xmleditor.client.presenter.ReadOnlyXMLEditorPresenter;
import stroom.xmleditor.client.presenter.XMLEditorPresenter;
import stroom.xmleditor.client.view.XMLEditorMenuPresenter;

public class XSLTPresenter extends EntityEditTabPresenter<LinkTabPanelView, XSLT> {
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData XSLT_TAB = new TabDataImpl("XSLT");
//    private static final TabData REFERENCES_TAB = new TabDataImpl("References");

    private final XSLTSettingsPresenter settingsPresenter;
    private final XMLEditorMenuPresenter editorMenuPresenter;
//    private final EntityReferenceListPresenter entityReferenceListPresenter;

    private BaseXMLEditorPresenter codePresenter;

    @Inject
    public XSLTPresenter(final EventBus eventBus, final LinkTabPanelView view,
                         final XSLTSettingsPresenter settingsPresenter, final XMLEditorMenuPresenter editorMenuPresenter,
//                         final EntityReferenceListPresenter entityReferenceListPresenter,
                         final ClientSecurityContext securityContext, final ClientDispatchAsync dispatcher) {
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

        addTab(SETTINGS_TAB);
        addTab(XSLT_TAB);
//        addTab(REFERENCES_TAB);
        selectTab(SETTINGS_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (XSLT_TAB.equals(tab)) {
            callback.onReady(codePresenter);
//        } else if (REFERENCES_TAB.equals(tab)) {
//            entityReferenceListPresenter.read(getEntity());
//            callback.onReady(entityReferenceListPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final XSLT xslt) {
        settingsPresenter.read(xslt);

        if (codePresenter != null) {
            codePresenter.setText(xslt.getData());
        }
    }

    @Override
    protected void onWrite(final XSLT xslt) {
        settingsPresenter.write(xslt);

        if (codePresenter != null) {
            xslt.setData(codePresenter.getText());
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
        return XSLT.ENTITY_TYPE;
    }
}
