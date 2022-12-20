/*
 * Copyright 2022 Crown Copyright
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

package stroom.sigmarule.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.sigmarule.client.presenter.SigmaRuleSettingsPresenter.SigmaRuleSettingsView;
import stroom.sigmarule.shared.SigmaRuleDoc;

import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.event.dom.client.InputHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class SigmaRuleSettingsPresenter extends DocumentSettingsPresenter<SigmaRuleSettingsView, SigmaRuleDoc> {

    @Inject
    public SigmaRuleSettingsPresenter(final EventBus eventBus,
                                      final SigmaRuleSettingsView view) {
        super(eventBus, view);

        // Add listeners for dirty events.
        final InputHandler inputHandler = event -> setDirty(true);
        registerHandler(view.getDescription().addDomHandler(inputHandler, InputEvent.getType()));
    }

    @Override
    protected void onRead(final DocRef docRef, final SigmaRuleDoc sigmaRule) {
        getView().getDescription().setText(sigmaRule.getDescription());
    }

    @Override
    protected void onWrite(final SigmaRuleDoc sigmaRule) {
        sigmaRule.setDescription(getView().getDescription().getText().trim());
    }

    @Override
    public String getType() {
        return SigmaRuleDoc.DOCUMENT_TYPE;
    }

    public interface SigmaRuleSettingsView extends View {

        TextArea getDescription();
    }
}
