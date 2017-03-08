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

package stroom.query.api;


import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ExpressionBuilder {
    private final Boolean enabled;
    private final Op op;

    private final List<Object> children = new ArrayList<>();

    /**
     * By default an expression builder will create enabled operators and will 'AND' all added terms.
     */
    public ExpressionBuilder() {
        this(null, Op.AND);
    }

    public ExpressionBuilder(final Op op) {
        this(null, op);
    }

    public ExpressionBuilder(final Boolean enabled, final Op op) {
        this.enabled = enabled;
        this.op = op;
    }

    public ExpressionBuilder addTerm(final String field, final Condition condition, final String value) {
        return addTerm(null, field, condition, value, null);
    }

    public ExpressionBuilder addTerm(final Boolean enabled, final String field, final Condition condition, final String value) {
        return addTerm(enabled, field, condition, value, null);
    }

    public ExpressionBuilder addTerm(final String field, final Condition condition, final DocRef dictionary) {
        return addTerm(null, field, condition, null, dictionary);
    }

    public ExpressionBuilder addTerm(final Boolean enabled, final String field, final Condition condition, final DocRef dictionary) {
        return addTerm(enabled, field, condition, null, dictionary);
    }

    public ExpressionBuilder addTerm(final Boolean enabled, final String field, final Condition condition, final String value, final DocRef dictionary) {
        children.add(new ExpressionTerm(enabled, field, condition, value, dictionary));
        return this;
    }

    public ExpressionBuilder addTerm(final ExpressionTerm term) {
        if (term != null) {
            addTerm(term.getEnabled(), term.getField(), term.getCondition(), term.getValue(), term.getDictionary());
        }

        return this;
    }

    public ExpressionBuilder addOperator(final Op op) {
        return addOperator(null, op);
    }

    public ExpressionBuilder addOperator(final Boolean enabled, final Op op) {
        final ExpressionBuilder builder = new ExpressionBuilder(enabled, op);
        children.add(builder);
        return builder;
    }

    public ExpressionBuilder addOperator(final ExpressionOperator operator) {
        if (operator != null) {
            final ExpressionBuilder builder = addOperator(operator.getEnabled(), operator.getOp());

            if (operator.getChildren() != null) {
                for (final ExpressionItem child : operator.getChildren()) {
                    if (child instanceof ExpressionOperator) {
                        builder.addOperator((ExpressionOperator) child);
                    } else if (child instanceof ExpressionTerm) {
                        builder.addTerm((ExpressionTerm) child);
                    }
                }
            }

            return builder;
        }

        return null;
    }

    public ExpressionOperator build() {
        if (children.size() == 0) {
            return new ExpressionOperator(enabled, op, Arrays.asList());
        }

        final List<ExpressionItem> list = new ArrayList<>(children.size());
        for (final Object object : children) {
            if (object instanceof ExpressionBuilder) {
                list.add(((ExpressionBuilder) object).build());
            } else if (object instanceof ExpressionTerm) {
                list.add((ExpressionTerm) object);
            }
        }

        return new ExpressionOperator(enabled, op, list);
    }
}