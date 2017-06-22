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

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityReferenceFindAction;
import stroom.query.shared.ExpressionTerm.Condition;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.StreamAttributeCondition;

import java.util.List;

public class StreamAttributePresenter extends MyPresenterWidget<StreamAttributePresenter.StreamAttributeView> {
    @Inject
    public StreamAttributePresenter(final EventBus eventBus, final StreamAttributeView view,
                                    final ClientDispatchAsync dispatcher) {
        super(eventBus, view);

        final FindStreamAttributeKeyCriteria criteria = new FindStreamAttributeKeyCriteria();
        criteria.setOrderBy(FindStreamAttributeKeyCriteria.ORDER_BY_NAME);
        dispatcher.exec(new EntityReferenceFindAction<>(criteria)).onSuccess(view::setKeys);
    }

    public void read(final StreamAttributeCondition condition) {
        getView().setKey(condition.getStreamAttributeKey());
        getView().setCondition(condition.getCondition());
        getView().setValue(condition.getFieldValue());
    }

    public boolean write(final StreamAttributeCondition condition) {
        final DocRef attributeKey = getView().getKey();
        final Condition attributeCondition = getView().getCondition();
        final String attributeValue = getView().getValue();
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
        Condition getCondition();

        void setCondition(Condition condition);

        void setKeys(List<DocRef> keys);

        DocRef getKey();

        void setKey(DocRef key);

        String getValue();

        void setValue(String value);
    }
}
