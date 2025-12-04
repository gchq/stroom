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

package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class QueryHelpFunctionSignature extends QueryHelpData {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String helpAnchor;
    @JsonProperty
    private final List<String> aliases;
    @JsonProperty
    private final List<String> categoryPath;
    @JsonProperty
    private final List<Arg> args;
    @JsonProperty
    private final Type returnType;
    @JsonProperty
    private final String returnDescription;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final OverloadType overloadType;

    @JsonCreator
    public QueryHelpFunctionSignature(@JsonProperty("name") final String name,
                                      @JsonProperty("helpAnchor") final String helpAnchor,
                                      @JsonProperty("aliases") final List<String> aliases,
                                      @JsonProperty("categoryPath") final List<String> categoryPath,
                                      @JsonProperty("args") final List<Arg> args,
                                      @JsonProperty("returnType") final Type returnType,
                                      @JsonProperty("returnDescription") final String returnDescription,
                                      @JsonProperty("description") final String description,
                                      @JsonProperty("overloadType") final OverloadType overloadType) {
        this.name = name;
        this.helpAnchor = helpAnchor;
        this.aliases = aliases;
        this.categoryPath = categoryPath;
        this.args = args;
        this.returnType = returnType;
        this.returnDescription = returnDescription;
        this.description = description;
        this.overloadType = overloadType;
    }

    public String getName() {
        return name;
    }

    public String getHelpAnchor() {
        return helpAnchor;
    }

    public List<String> getAliases() {
        return aliases;
    }

    @JsonIgnore
    public String getPrimaryCategory() {
        return categoryPath.isEmpty()
                ? null
                : categoryPath.get(0);
    }

    public List<String> getCategoryPath() {
        return categoryPath;
    }

    public Optional<String> getCategory(final int depth) {
        return depth < categoryPath.size()
                ? Optional.ofNullable(categoryPath.get(depth))
                : Optional.empty();
    }

    public List<Arg> getArgs() {
        return args;
    }

    public Type getReturnType() {
        return returnType;
    }

    public String getReturnDescription() {
        return returnDescription;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @return True if this function signature is overloaded within its category
     */
    public OverloadType getOverloadType() {
        return overloadType;
    }

    @Override
    public String toString() {
        return "FunctionSignature{" +
                "name='" + name + '\'' +
                ", aliases=" + aliases +
                ", categoryPath=" + categoryPath +
                ", args=" + args +
                ", returnType=" + returnType +
                ", returnDescription='" + returnDescription + '\'' +
                ", description='" + description + '\'' +
                ", overloadType='" + overloadType + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryHelpFunctionSignature that = (QueryHelpFunctionSignature) o;
        return Objects.equals(name, that.name)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(categoryPath, that.categoryPath)
                && Objects.equals(args, that.args)
                && returnType == that.returnType
                && Objects.equals(returnDescription, that.returnDescription)
                && Objects.equals(description, that.description)
                && Objects.equals(overloadType, that.overloadType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name, aliases, categoryPath, args, returnType, returnDescription, description, overloadType);
    }


    // --------------------------------------------------------------------------------


    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Arg {

        @JsonProperty
        private final String name;
        @JsonProperty
        private final Type argType;
        @JsonProperty
        private final boolean optional;
        @JsonProperty
        private final boolean varargs;
        @JsonProperty
        private final int minVarargsCount;
        @JsonProperty
        private final String description;
        @JsonProperty
        private final List<String> allowedValues;
        @JsonProperty
        private final String defaultValue;

        @JsonCreator
        public Arg(@JsonProperty("name") final String name,
                   @JsonProperty("argType") final Type argType,
                   @JsonProperty("optional") final boolean optional,
                   @JsonProperty("varargs") final boolean varargs,
                   @JsonProperty("minVarargsCount") final int minVarargsCount,
                   @JsonProperty("description") final String description,
                   @JsonProperty("allowedValues") final List<String> allowedValues,
                   @JsonProperty("defaultValue") final String defaultValue) {
            this.name = name;
            this.argType = argType;
            this.optional = optional;
            this.varargs = varargs;
            this.minVarargsCount = minVarargsCount;
            this.description = description;
            this.allowedValues = allowedValues;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public Type getArgType() {
            return argType;
        }

        public boolean isOptional() {
            return optional;
        }

        public boolean isVarargs() {
            return varargs;
        }

        public int getMinVarargsCount() {
            return minVarargsCount;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getAllowedValues() {
            return allowedValues;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Arg arg = (Arg) o;
            return optional == arg.optional
                    && varargs == arg.varargs
                    && minVarargsCount == arg.minVarargsCount
                    && Objects.equals(name, arg.name)
                    && argType == arg.argType
                    && Objects.equals(description, arg.description)
                    && Objects.equals(allowedValues, arg.allowedValues)
                    && Objects.equals(defaultValue, arg.defaultValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name,
                    argType,
                    optional,
                    varargs,
                    minVarargsCount,
                    description,
                    allowedValues,
                    defaultValue);
        }

        @Override
        public String toString() {
            return "Arg{" +
                    "name='" + name + '\'' +
                    ", argType=" + argType +
                    ", optional=" + optional +
                    ", varargs=" + varargs +
                    ", minVarargsCount=" + minVarargsCount +
                    ", description='" + description + '\'' +
                    ", allowedValues=" + allowedValues +
                    ", defaultValue=" + defaultValue +
                    '}';
        }

    }


    // --------------------------------------------------------------------------------


    public enum Type {

        UNKNOWN("Unknown"),
        BOOLEAN("Boolean"),
        DOUBLE("Double"),
        ERROR("Error"),
        INTEGER("Integer"),
        LONG("Long"),
        NULL("Null"),
        NUMBER("Number"),
        STRING("String");

        private final String name;

        Type(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


    // --------------------------------------------------------------------------------

    public enum OverloadType {
        NOT_OVERLOADED,
        OVERLOADED_IN_CATEGORY,
        OVERLOADED_GLOBALLY;
    }

}
