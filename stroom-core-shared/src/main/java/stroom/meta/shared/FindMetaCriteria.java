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

package stroom.meta.shared;

import stroom.docref.SharedObject;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.Copyable;
import stroom.util.shared.HasIsConstrained;
import stroom.util.shared.IdSet;

import java.util.Objects;

public class FindMetaCriteria extends BaseCriteria implements SharedObject, HasIsConstrained, Copyable<FindMetaCriteria> {
    private static final long serialVersionUID = -4777723504698304778L;

    public static final String FIELD_FEED = "Feed";
    public static final String FIELD_TYPE = "Type";

    private ExpressionOperator expression;
    private IdSet selectedIdSet;
    private boolean fetchRelationships;

    public FindMetaCriteria() {
    }

    public FindMetaCriteria(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public static FindMetaCriteria createFromMeta(final Meta meta) {
        final FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression());
        criteria.obtainSelectedIdSet().add(meta.getId());
        return criteria;
    }

    public static FindMetaCriteria createWithType(final String typeName) {
        final FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(MetaExpressionUtil.createTypeExpression(typeName));
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
            expression = MetaExpressionUtil.createSimpleExpression();
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

    public void setFetchRelationships(final boolean fetchRelationships) {
        this.fetchRelationships = fetchRelationships;
    }

    public boolean isFetchRelationships() {
        return fetchRelationships;
    }

    @Override
    public boolean isConstrained() {
        return (selectedIdSet != null && selectedIdSet.isConstrained()) || ExpressionUtil.termCount(expression) > 0;
    }

    @Override
    public void copyFrom(final FindMetaCriteria other) {
        super.copyFrom(other);
        if (other != null) {
            this.expression = ExpressionUtil.copyOperator(other.expression);
            if (other.selectedIdSet == null) {
                this.selectedIdSet = null;
            } else {
                this.obtainSelectedIdSet().copyFrom(other.selectedIdSet);
            }
            this.fetchRelationships = other.fetchRelationships;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final FindMetaCriteria that = (FindMetaCriteria) o;
        return Objects.equals(expression, that.expression) &&
                Objects.equals(selectedIdSet, that.selectedIdSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression, selectedIdSet);
    }
}
