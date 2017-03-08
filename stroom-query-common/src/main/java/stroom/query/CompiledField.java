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

package stroom.query;

import stroom.dashboard.expression.Expression;
import stroom.query.api.Field;

public class CompiledField {
    private final Field field;
    private final int groupDepth;
    private final Expression expression;
    private final CompiledFilter compiledFilter;

    public CompiledField(final Field field, final int groupDepth, final Expression expression,
                         final CompiledFilter compiledFilter) {
        this.field = field;
        this.groupDepth = groupDepth;
        this.expression = expression;
        this.compiledFilter = compiledFilter;
    }

    public Field getField() {
        return field;
    }

    public int getGroupDepth() {
        return groupDepth;
    }

    public Expression getExpression() {
        return expression;
    }

    public CompiledFilter getCompiledFilter() {
        return compiledFilter;
    }

    @Override
    public String toString() {
        return field.toString();
    }
}
