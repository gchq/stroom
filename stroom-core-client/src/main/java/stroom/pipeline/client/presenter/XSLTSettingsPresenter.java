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
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import stroom.security.client.ClientSecurityContext;
import stroom.app.client.event.DirtyKeyDownHander;
import stroom.entity.client.presenter.EntitySettingsPresenter;
import stroom.pipeline.shared.XSLT;

public class XSLTSettingsPresenter extends EntitySettingsPresenter<XSLTSettingsPresenter.XSLTSettingsView, XSLT> {
    public interface XSLTSettingsView extends View {
        TextArea getDescription();
    }

    @Override
    public String getType() {
        return XSLT.ENTITY_TYPE;
    }

    @Inject
    public XSLTSettingsPresenter(final EventBus eventBus, final XSLTSettingsView view,
            final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };

        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));

    }

    @Override
    protected void onRead(final XSLT xslt) {
        getView().getDescription().setText(xslt.getDescription());
    }

    @Override
    protected void onWrite(final XSLT xslt) {
        xslt.setDescription(getView().getDescription().getText().trim());
    }
}
