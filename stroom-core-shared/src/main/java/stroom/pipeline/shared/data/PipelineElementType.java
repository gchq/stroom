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

package stroom.pipeline.shared.data;

import stroom.docref.HasDisplayValue;
import stroom.docref.HasType;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"type", "category", "roles", "icon"})
public class PipelineElementType implements Comparable<PipelineElementType>, HasType, HasDisplayValue {

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

    // Type names for some elements so the UI can refer to them without having
    // to use hard coded strings.
    public static final String TYPE_COMBINED_PARSER = "CombinedParser";
    public static final String TYPE_DS_PARSER = "DSParser";
    public static final String TYPE_XML_FRAGMENT_PARSER = "XMLFragmentParser";

    @JsonProperty
    private final String type;
    @JsonProperty
    private final String displayValue;
    @JsonProperty
    private final Category category;
    @JsonProperty
    private final String[] roles;
    @JsonProperty
    private final SvgImage icon;

    @JsonIgnore
    private Set<String> roleSet;

    @JsonCreator
    public PipelineElementType(@JsonProperty("type") final String type,
                               @JsonProperty("displayValue") final String displayValue,
                               @JsonProperty("category") final Category category,
                               @JsonProperty("roles") final String[] roles,
                               @JsonProperty("icon") final SvgImage icon) {
        this.type = type;
        this.displayValue = displayValue;
        this.category = category;
        this.roles = roles;
        this.icon = icon;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public Category getCategory() {
        return category;
    }

    public String[] getRoles() {
        return roles;
    }

    public SvgImage getIcon() {
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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


    // --------------------------------------------------------------------------------


    @SuppressWarnings("TextBlockMigration") // Because GWT :-(
    public enum Category implements HasDisplayValue {
        INTERNAL(
                "Internal",
                -1,
                "For internal user only."),
        READER(
                "Reader",
                0,
                "Reader elements decode the data in raw byte form using the Feed's configured " +
                        "character encoding.\n" +
                        "Some of them will also transform the data at the character level before the " +
                        "data are parsed into a structured form."),
        PARSER(
                "Parser",
                1,
                "Parser elements parse raw text data that has an expected structure " +
                        "(e.g. XML, JSON, CSV) " +
                        "into XML events (elements, attributes, text, etc) that can be further validated or " +
                        "transformed using XSLT.\n" +
                        "The choice of Parser will be dictated by the structure of the data.\n" +
                        "If no Reader is used before the Parser, the Parser will also do the job of a simple Reader " +
                        "and decode the raw bytes using the Feed's configured character encoding."),
        FILTER(
                "Filter",
                2,
                "Filter elements work with XML events that have been generated by a _parser_.\n" +
                        "They can consume the events without modifying them, e.g. _RecordCountFilter_ or " +
                        "modify them in some way, e.g. _XSLTFilter_.\n" +
                        "Multiple filters can be used one after another with each using the output from the " +
                        "last as its input."),
        WRITER(
                "Writer",
                3,
                "Writers consume XML events (from _Parsers_ and _Filters_) and convert them into a " +
                        "stream of bytes\n" +
                        "using the character encoding configured on the _Writer_ (if applicable).\n" +
                        "The output data can then be fed to a Destination."),
        DESTINATION(
                "Destination",
                4,
                "Destination elements consume a stream of bytes from a _Writer_ and persist then " +
                        "to a destination.\n" +
                        "This could be a file on a file system or to Stroom's stream store.");

        private final String displayValue;
        private final int order;
        private final String description;

        Category(final String displayValue, final int order, final String description) {
            this.displayValue = displayValue;
            this.order = order;
            this.description = description;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        public int getOrder() {
            return order;
        }

        public String getDescription() {
            return description;
        }
    }
}
