/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntityItemListBox;
import stroom.entity.shared.EntityReferenceFindAction;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.SharedDocRef;
import stroom.item.client.ItemListBox;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.StreamAttributeCondition;

public class StreamAttributePresenter extends MyPresenterWidget<StreamAttributePresenter.StreamAttributeView> {
    @Inject
    public StreamAttributePresenter(final EventBus eventBus, final StreamAttributeView view,
                                    final ClientDispatchAsync dispatcher) {
        super(eventBus, view);

        final EntityReferenceFindAction<FindStreamAttributeKeyCriteria> findAction = new EntityReferenceFindAction<FindStreamAttributeKeyCriteria>(
                new FindStreamAttributeKeyCriteria());
        dispatcher.execute(findAction, new AsyncCallbackAdaptor<ResultList<SharedDocRef>>() {
            @Override
            public void onSuccess(final ResultList<SharedDocRef> resultList) {
                for (final SharedDocRef docRef : resultList) {
                    view.getStreamAttributeKey().addItem(docRef);
                }
            }
        });
    }

    public void read(final StreamAttributeCondition condition) {
        getView().getStreamAttributeKey().setSelectedItem(condition.getStreamAttributeKey());
        getView().getStreamAttributeCondition().setSelectedItem(condition.getCondition());
        getView().setStreamAttributeValue(condition.getFieldValue());
    }

    public boolean write(final StreamAttributeCondition condition) {
        final DocRef attributeKey = getView().getStreamAttributeKey().getSelectedItem();
        final Condition attributeCondition = getView().getStreamAttributeCondition().getSelectedItem();
        final String attributeValue = getView().getStreamAttributeValue();
        if (attributeCondition != null && attributeValue != null && attributeValue.length() > 0) {
            condition.setStreamAttributeKey(attributeKey);
            condition.setCondition(attributeCondition);
            condition.setFieldValue(attributeValue);

            return true;
        }

        AlertEvent.fireError(this, "You must set a key, condition and value", null);
        return false;
    }

    public interface StreamAttributeView extends View {
        ItemListBox<Condition> getStreamAttributeCondition();

        EntityItemListBox getStreamAttributeKey();

        String getStreamAttributeValue();

        void setStreamAttributeValue(String value);
    }
}
