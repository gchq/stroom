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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.pipeline.shared.XSLT;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import javax.inject.Provider;

public class XSLTPresenter extends EntityEditTabPresenter<LinkTabPanelView, XSLT> {
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData XSLT_TAB = new TabDataImpl("XSLT");

    private final XSLTSettingsPresenter settingsPresenter;
    private final Provider<EditorPresenter> editorPresenterProvider;

    private EditorPresenter codePresenter;

    @Inject
    public XSLTPresenter(final EventBus eventBus, final LinkTabPanelView view,
                         final XSLTSettingsPresenter settingsPresenter, final Provider<EditorPresenter> editorPresenterProvider,
                         final ClientSecurityContext securityContext, final ClientDispatchAsync dispatcher) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.editorPresenterProvider = editorPresenterProvider;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(SETTINGS_TAB);
        addTab(XSLT_TAB);
        selectTab(SETTINGS_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (XSLT_TAB.equals(tab)) {
            callback.onReady(codePresenter);
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
        return XSLT.ENTITY_TYPE;
    }
}
