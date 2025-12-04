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

package stroom.dashboard.impl.vis;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VisSettings {

    private Data data;
    private Tab[] tabs;

    public VisSettings() {
        // Default constructor necessary for JSON object instantiation.
    }

    @JsonProperty("data")
    public Data getData() {
        return data;
    }

    @JsonProperty("tabs")
    public Tab[] getTabs() {
        return tabs;
    }

    public static class Limit {

        private String enabled;
        private String size;

        public Limit() {
            // Default constructor necessary for JSON object instantiation.
        }

        @JsonProperty("enabled")
        public String getEnabled() {
            return enabled;
        }

        @JsonProperty("size")
        public String getSize() {
            return size;
        }
    }

    public static class Sort {

        private String enabled;
        private String priority;
        private String direction;

        public Sort() {
            // Default constructor necessary for JSON object instantiation.
        }

        @JsonProperty("enabled")
        public String getEnabled() {
            return enabled;
        }

        @JsonProperty("priority")
        public String getPriority() {
            return priority;
        }

        @JsonProperty("direction")
        public String getDirection() {
            return direction;
        }
    }

    public static class Field {

        private String id;
        private Sort sort;

        public Field() {
            // Default constructor necessary for JSON object instantiation.
        }

        public Field(final String id) {
            this(id, null);
        }

        public Field(final String id, final Sort sort) {
            this.id = id;
            this.sort = sort;
        }

        @JsonProperty("id")
        public String getId() {
            return id;
        }

        @JsonProperty("sort")
        public Sort getSort() {
            return sort;
        }
    }

    public static class Values {

        private Field[] fields;
        private Limit limit;

        public Values() {
            // Default constructor necessary for JSON object instantiation.
        }

        public Values(final Field[] fields, final Limit limit) {
            this.fields = fields;
            this.limit = limit;
        }

        @JsonProperty("fields")
        public Field[] getFields() {
            return fields;
        }

        @JsonProperty("limit")
        public Limit getLimit() {
            return limit;
        }
    }

    public static class Nest {

        private Field key;
        private Limit limit;
        private Nest nest;
        private Values values;

        public Nest() {
            // Default constructor necessary for JSON object instantiation.
        }

        public Nest(final Field key) {
            this.key = key;
        }

        public Nest(final Field key, final Limit limit, final Nest nest, final Values values) {
            this.key = key;
            this.limit = limit;
            this.nest = nest;
            this.values = values;
        }

        @JsonProperty("key")
        public Field getKey() {
            return key;
        }

        @JsonProperty("limit")
        public Limit getLimit() {
            return limit;
        }

        @JsonProperty("nest")
        public Nest getNest() {
            return nest;
        }

        @JsonProperty("values")
        public Values getValues() {
            return values;
        }
    }

    public static class Structure {

        private Nest nest;
        private Values values;

        public Structure() {
            // Default constructor necessary for JSON object instantiation.
        }

        public Structure(final Nest nest, final Values values) {
            this.nest = nest;
            this.values = values;
        }

        @JsonProperty("nest")
        public Nest getNest() {
            return nest;
        }

        @JsonProperty("values")
        public Values getValues() {
            return values;
        }
    }

    public static class Data {

        private Structure structure;

        public Data() {
            // Default constructor necessary for JSON object instantiation.
        }

        public Data(final Structure structure) {
            this.structure = structure;
        }

        @JsonProperty("structure")
        public Structure getStructure() {
            return structure;
        }
    }

    public static class Control {

        private String id;
        private String type;
        private String label;
        private String[] values;
        private String defaultValue;

        public Control() {
            // Default constructor necessary for JSON object instantiation.
        }

        @JsonProperty("id")
        public String getId() {
            return id;
        }

        @JsonProperty("type")
        public String getType() {
            return type;
        }

        @JsonProperty("label")
        public String getLabel() {
            return label;
        }

        @JsonProperty("values")
        public String[] getValues() {
            return values;
        }

        @JsonProperty("defaultValue")
        public String getDefaultValue() {
            return defaultValue;
        }
    }

    public static class Tab {

        private String name;
        private Control[] controls;

        public Tab() {
            // Default constructor necessary for JSON object instantiation.
        }

        @JsonProperty("name")
        public String getName() {
            return name;
        }

        @JsonProperty("controls")
        public Control[] getControls() {
            return controls;
        }
    }
}
