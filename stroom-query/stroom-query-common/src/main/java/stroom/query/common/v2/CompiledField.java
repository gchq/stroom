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

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;
import stroom.query.api.v2.Field;

public class CompiledField {

    private final Field field;
    private final int groupDepth;
    private final Generator generator;
    private final boolean hasAggregate;
    private final boolean requiresChildData;
    private final CompiledFilter compiledFilter;

    public CompiledField(final Field field,
                         final int groupDepth,
                         final Generator generator,
                         final boolean hasAggregate,
                         final boolean requiresChildData,
                         final CompiledFilter compiledFilter) {
        this.field = field;
        this.groupDepth = groupDepth;
        this.generator = generator;
        this.hasAggregate = hasAggregate;
        this.requiresChildData = requiresChildData;
        this.compiledFilter = compiledFilter;
    }

    public Field getField() {
        return field;
    }

    public int getGroupDepth() {
        return groupDepth;
    }

    public Generator getGenerator() {
        return generator;
    }

    /**
     * Is this function an aggregating function or are any of the child
     * parameters used going to aggregate data.
     *
     * @return True if this function is an aggregating function or any child
     * parameters used will aggregate data.
     */
    public boolean hasAggregate() {
        return hasAggregate;
    }

    /**
     * If the function selects a child row return true.
     *
     * @return True is the function selects a child row.
     */
    public boolean requiresChildData() {
        return requiresChildData;
    }

    public CompiledFilter getCompiledFilter() {
        return compiledFilter;
    }

    @Override
    public String toString() {
        return "CompiledField{" +
                "field=" + field +
                ", groupDepth=" + groupDepth +
                ", generator=" + generator +
                ", compiledFilter=" + compiledFilter +
                '}';
    }
}
