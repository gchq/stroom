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

package stroom.query;

import stroom.query.api.Format.Type;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.VisLimit;

public class CompiledStructure {
    public static class FieldRef {
        private final Type type;
        private final int index;

        public FieldRef(final Type type, final int index) {
            this.type = type;
            this.index = index;
        }

        public Type getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }
    }

//    public static class Limit {
//        private final int size;
//
//        public Limit(final int size) {
//            this.size = size;
//        }
//
//        public int getSize() {
//            return size;
//        }
//    }

    public static class Sort {
        private final int index;
        private final int priority;
        private final SortDirection direction;

        public Sort(final int index, final int priority, final SortDirection direction) {
            this.index = index;
            this.priority = priority;
            this.direction = direction;
        }

        public int getIndex() {
            return index;
        }

        public Integer getPriority() {
            return priority;
        }

        public SortDirection getDirection() {
            return direction;
        }
    }

    public static class Field {
        private final FieldRef id;
        private final Sort sort;

        public Field(final FieldRef id, final Sort sort) {
            this.id = id;
            this.sort = sort;
        }

        public FieldRef getId() {
            return id;
        }

        public Sort getSort() {
            return sort;
        }
    }

    public static class Values {
        private final Field[] fields;
        private final VisLimit limit;

        public Values(final Field[] fields, final VisLimit limit) {
            this.fields = fields;
            this.limit = limit;
        }

        public Field[] getFields() {
            return fields;
        }

        public VisLimit getLimit() {
            return limit;
        }
    }

    public static class Nest {
        private final Field key;
        private final VisLimit limit;
        private final Nest nest;
        private final Values values;

        public Nest(final Field key, final VisLimit limit, final Nest nest, final Values values) {
            this.key = key;
            this.limit = limit;
            this.nest = nest;
            this.values = values;
        }

        public Field getKey() {
            return key;
        }

        public VisLimit getLimit() {
            return limit;
        }

        public Nest getNest() {
            return nest;
        }

        public Values getValues() {
            return values;
        }
    }

    public static class Structure {
        private final Nest nest;
        private final Values values;

        public Structure(final Nest nest, final Values values) {
            this.nest = nest;
            this.values = values;
        }

        public Nest getNest() {
            return nest;
        }

        public Values getValues() {
            return values;
        }
    }
}
