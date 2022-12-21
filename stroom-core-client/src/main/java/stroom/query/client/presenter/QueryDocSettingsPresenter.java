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

package stroom.query.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.query.client.presenter.QueryDocSettingsPresenter.QueryDocSettingsView;
import stroom.query.shared.QueryDoc;

import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.event.dom.client.InputHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class QueryDocSettingsPresenter extends DocumentSettingsPresenter<QueryDocSettingsView, QueryDoc> {

    @Inject
    public QueryDocSettingsPresenter(final EventBus eventBus,
                                     final QueryDocSettingsView view) {
        super(eventBus, view);


    }

    @Override
    protected void onBind() {
        super.onBind();

        // Add listeners for dirty events.
        final InputHandler inputHandler = event -> setDirty(true);
        registerHandler(getView().getDescription().addDomHandler(inputHandler, InputEvent.getType()));
    }

    @Override
    protected void onRead(final DocRef docRef, final QueryDoc query) {
        getView().getDescription().setText(query.getDescription());
    }

    @Override
    protected void onWrite(final QueryDoc query) {
        query.setDescription(getView().getDescription().getText().trim());
    }

    @Override
    public String getType() {
        return QueryDoc.DOCUMENT_TYPE;
    }

    public interface QueryDocSettingsView extends View {

        TextArea getDescription();
    }
}
