/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.util.shared.Copyable;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindMetaCriteria extends ExpressionCriteria implements Copyable<FindMetaCriteria> {

    @JsonProperty
    private boolean fetchRelationships;

    public FindMetaCriteria() {
    }

    public FindMetaCriteria(final ExpressionOperator expression) {
        super(expression);
    }

    @JsonCreator
    public FindMetaCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                            @JsonProperty("expression") final ExpressionOperator expression,
                            @JsonProperty("fetchRelationships") final boolean fetchRelationships) {
        super(pageRequest, sortList, expression);
        this.fetchRelationships = fetchRelationships;
    }

    public static FindMetaCriteria unlocked() {
        return new FindMetaCriteria(MetaExpressionUtil.createStatusExpression(Status.UNLOCKED));
    }

    public static FindMetaCriteria createFromId(final long id) {
        return new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(id, Status.UNLOCKED));
    }

    public static FindMetaCriteria createFromMeta(final Meta meta) {
        return new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(meta.getId(), Status.UNLOCKED));
    }

    public static FindMetaCriteria createWithType(final String typeName) {
        return new FindMetaCriteria(MetaExpressionUtil.createTypeExpression(typeName, Status.UNLOCKED));
    }

    public void setFetchRelationships(final boolean fetchRelationships) {
        this.fetchRelationships = fetchRelationships;
    }

    public boolean isFetchRelationships() {
        return fetchRelationships;
    }

//    @Override
//    public boolean isConstrained() {
//        return (selectedIdSet != null && selectedIdSet.isConstrained()) || ExpressionUtil.termCount(expression) > 0;
//    }

    @Override
    public void copyFrom(final FindMetaCriteria other) {
        super.copyFrom(other);
        if (other != null) {
            this.setExpression(ExpressionUtil.copyOperator(other.getExpression()));
            this.fetchRelationships = other.fetchRelationships;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FindMetaCriteria)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindMetaCriteria that = (FindMetaCriteria) o;
        return fetchRelationships == that.fetchRelationships;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fetchRelationships);
    }
}
