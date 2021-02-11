package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionSignature {

    @JsonProperty
    private final String name;
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

    @JsonCreator
    public FunctionSignature(@JsonProperty("name") final String name,
            @JsonProperty("aliases") final List<String> aliases,
            @JsonProperty("categoryPath") final List<String> categoryPath,
            @JsonProperty("args") final List<Arg> args,
            @JsonProperty("returnType") final Type returnType,
            @JsonProperty("returnDescription") final String returnDescription,
            @JsonProperty("description") final String description) {
        this.name = name;
        this.aliases = aliases;
        this.categoryPath = categoryPath;
        this.args = args;
        this.returnType = returnType;
        this.returnDescription = returnDescription;
        this.description = description;
    }

    /**
     * @return Once {@link FunctionSignature} for each name or alias with the name set to that name/alias
     * and no aliases.
     */
    public List<FunctionSignature> asAliases() {
        if (aliases.isEmpty()) {
            return Collections.singletonList(this);
        } else {
            final Set<String> allNames = getAllNames();

            return allNames
                    .stream()
                    .map(newPrimaryName -> asAlias(newPrimaryName, allNames))
                    .collect(Collectors.toList());
        }
    }

    private Set<String> getAllNames() {
        return Stream.concat(Stream.of(name), this.aliases.stream())
                .collect(Collectors.toSet());
    }

    private FunctionSignature asAlias(final String newPrimaryName, final Set<String> allNames) {
        if (allNames.contains(newPrimaryName)) {
            final List<String> newAliases = allNames.stream()
                    .filter(name2 -> !newPrimaryName.equals(name2))
                    .collect(Collectors.toList());

            return new FunctionSignature(
                    newPrimaryName,
                    newAliases,
                    categoryPath,
                    args,
                    returnType,
                    returnDescription,
                    description);
        } else {
            throw new RuntimeException(newPrimaryName + " is not a valid name or alias");
        }
    }

    public String getName() {
        return name;
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
                '}';
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FunctionSignature that = (FunctionSignature) o;
        return Objects.equals(name, that.name)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(categoryPath, that.categoryPath)
                && Objects.equals(args, that.args)
                && returnType == that.returnType
                && Objects.equals(returnDescription, that.returnDescription)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, aliases, categoryPath, args, returnType, returnDescription, description);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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

        @SuppressWarnings("checkstyle:needbraces")
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

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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

}
