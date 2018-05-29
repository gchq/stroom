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

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.pipeline.client.presenter.XsltSettingsPresenter.XsltSettingsView;
import stroom.pipeline.shared.XsltDoc;
import stroom.docref.DocRef;
import stroom.security.client.ClientSecurityContext;

public class XsltSettingsPresenter extends DocumentSettingsPresenter<XsltSettingsView, XsltDoc> {
    @Inject
    public XsltSettingsPresenter(final EventBus eventBus, final XsltSettingsView view,
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
    public String getType() {
        return XsltDoc.DOCUMENT_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final XsltDoc xslt) {
        getView().getDescription().setText(xslt.getDescription());
    }

    @Override
    protected void onWrite(final XsltDoc xslt) {
        xslt.setDescription(getView().getDescription().getText().trim());
    }

    public interface XsltSettingsView extends View {
        TextArea getDescription();
    }
}
