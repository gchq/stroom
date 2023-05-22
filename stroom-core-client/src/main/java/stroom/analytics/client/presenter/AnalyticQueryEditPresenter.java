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

package stroom.analytics.client.presenter;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.query.client.presenter.QueryEditPresenter;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.shared.QueryDoc;

import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;

public class AnalyticQueryEditPresenter extends DocumentEditPresenter<QueryEditView, AnalyticRuleDoc> {

    private final QueryEditPresenter queryEditPresenter;

    @Inject
    public AnalyticQueryEditPresenter(final EventBus eventBus,
                                      final QueryEditPresenter queryEditPresenter) {
        super(eventBus, queryEditPresenter.getView());
        this.queryEditPresenter = queryEditPresenter;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(queryEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    public void onRead(final DocRef docRef, final AnalyticRuleDoc entity, final boolean readOnly) {
        queryEditPresenter.setQuery(docRef, entity.getQuery(), readOnly);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc entity) {
        return entity.copy().query(queryEditPresenter.getQuery()).build();
    }

    @Override
    public void onClose() {
        queryEditPresenter.onClose();
        super.onClose();
    }

    @Override
    public String getType() {
        return QueryDoc.DOCUMENT_TYPE;
    }
}
