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
 */

package stroom.streamstore.shared;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.Copyable;
import stroom.entity.shared.HasIsConstrained;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.IdSet;
import stroom.query.api.v2.ExpressionOperator;

public class FindStreamCriteria extends BaseCriteria implements HasIsConstrained, Copyable<FindStreamCriteria> {
    private static final long serialVersionUID = -4777723504698304778L;

    private ExpressionOperator expression;
    private IdSet selectedIdSet;
    private IdRange streamIdRange;

    public FindStreamCriteria() {
    }

    public static FindStreamCriteria createWithStream(final Stream stream) {
        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.setExpression(ExpressionUtil.createSimpleExpression());
        criteria.obtainSelectedIdSet().add(stream.getId());
        return criteria;
    }

    public static FindStreamCriteria createWithStreamType(final StreamType streamType) {
        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.setExpression(ExpressionUtil.createStreamTypeExpression(streamType));
        return criteria;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public ExpressionOperator obtainExpression() {
        if (expression == null) {
            expression = ExpressionUtil.createSimpleExpression();
        }
        return expression;
    }

    public IdSet getSelectedIdSet() {
        return selectedIdSet;
    }

    public void setSelectedIdSet(final IdSet selectedIdSet) {
        this.selectedIdSet = selectedIdSet;
    }

    public IdSet obtainSelectedIdSet() {
        if (selectedIdSet == null) {
            selectedIdSet = new IdSet();
        }
        return selectedIdSet;
    }

    public IdRange getStreamIdRange() {
        return streamIdRange;
    }

    public void setStreamIdRange(final IdRange streamIdRange) {
        this.streamIdRange = streamIdRange;
    }

    public IdRange obtainStreamIdRange() {
        if (streamIdRange == null) {
            streamIdRange = new IdRange();
        }
        return streamIdRange;
    }

    @Override
    public boolean isConstrained() {
        return (selectedIdSet != null && selectedIdSet.isConstrained()) || (streamIdRange != null && streamIdRange.isConstrained()) || ExpressionUtil.termCount(expression) > 0;
    }

    @Override
    public void copyFrom(final FindStreamCriteria other) {
        super.copyFrom(other);
        this.expression = other.expression;
        this.obtainSelectedIdSet().copyFrom(other.obtainSelectedIdSet());
        this.obtainStreamIdRange().copyFrom(other.obtainStreamIdRange());
    }
}
