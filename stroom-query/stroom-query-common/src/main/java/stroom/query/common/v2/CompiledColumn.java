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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.language.functions.Generator;

public class CompiledColumn {

    private final Column column;
    private final int groupDepth;
    private final Generator generator;
    private final boolean hasAggregate;
    private final boolean requiresChildData;

    public CompiledColumn(final Column column,
                          final int groupDepth,
                          final Generator generator,
                          final boolean hasAggregate,
                          final boolean requiresChildData) {
        this.column = column;
        this.groupDepth = groupDepth;
        this.generator = generator;
        this.hasAggregate = hasAggregate;
        this.requiresChildData = requiresChildData;
    }

    public Column getColumn() {
        return column;
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

    @Override
    public String toString() {
        return "CompiledField{" +
                "column=" + column +
                ", groupDepth=" + groupDepth +
                ", generator=" + generator +
                '}';
    }
}
