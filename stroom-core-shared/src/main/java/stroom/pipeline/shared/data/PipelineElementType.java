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

package stroom.pipeline.shared.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"type", "category", "roles", "icon"})
public class PipelineElementType implements Comparable<PipelineElementType>, HasType {
    public static final String ROLE_SOURCE = "source";
    public static final String ROLE_DESTINATION = "destination";
    public static final String ROLE_TARGET = "target";
    public static final String ROLE_HAS_TARGETS = "hasTargets";
    public static final String ROLE_READER = "reader";
    public static final String ROLE_PARSER = "parser";
    public static final String ROLE_WRITER = "writer";
    /**
     * Pipeline elements that mutate the input provided to them to produce
     * different output, e.g. XSLT filter.
     */
    public static final String ROLE_MUTATOR = "mutator";
    /**
     * Pipeline elements that validate provided input and produce a set of
     * indicators to show where the input is invalid.
     */
    public static final String ROLE_VALIDATOR = "validator";
    /**
     * Pipeline elements that have code associated with them that alters their
     * behaviour, e.g. XSLT filter or various parser types.
     */
    public static final String ROLE_HAS_CODE = "hasCode";
    /**
     * Add this type to elements that we want to appear in the pipeline tree in
     * simple mode.
     */
    public static final String VISABILITY_SIMPLE = "simple";
    /**
     * Add this type to elements that we want to appear in the pipeline tree in
     * stepping mode.
     */
    public static final String VISABILITY_STEPPING = "stepping";

    @JsonProperty
    private final String type;
    @JsonProperty
    private final Category category;
    @JsonProperty
    private final String[] roles;
    @JsonProperty
    private final String icon;

    @JsonIgnore
    private Set<String> roleSet;

    @JsonCreator
    public PipelineElementType(@JsonProperty("type") final String type,
                               @JsonProperty("category") final Category category,
                               @JsonProperty("roles") final String[] roles,
                               @JsonProperty("icon") final String icon) {
        this.type = type;
        this.category = category;
        this.roles = roles;
        this.icon = icon;
    }

    @Override
    public String getType() {
        return type;
    }

    public Category getCategory() {
        return category;
    }

    public String[] getRoles() {
        return roles;
    }

    public String getIcon() {
        return icon;
    }

    public boolean hasRole(final String role) {
        if (roleSet == null) {
            if (roles == null || roles.length == 0) {
                roleSet = Collections.emptySet();
            } else {
                roleSet = new HashSet<>(Arrays.asList(roles));
            }
        }
        return roleSet.contains(role);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PipelineElementType that = (PipelineElementType) o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return type;
    }

    @Override
    public int compareTo(final PipelineElementType o) {
        return type.compareTo(o.type);
    }

    public enum Category implements HasDisplayValue {
        INTERNAL("Internal", -1), READER("Reader", 0), PARSER("Parser", 1), FILTER("Filter", 2), WRITER("Writer",
                3), DESTINATION("Destination", 4);

        private final String displayValue;
        private final int order;

        Category(final String displayValue, final int order) {
            this.displayValue = displayValue;
            this.order = order;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        public int getOrder() {
            return order;
        }
    }
}
