package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionDefinition {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final List<String> aliases;
    @JsonProperty
    private final String functionCategory;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final List<Signature> signatures;

    @JsonCreator
    public FunctionDefinition(@JsonProperty("name") final String name,
                              @JsonProperty("aliases") final List<String> aliases,
                              @JsonProperty("functionCategory") final String functionCategory,
                              @JsonProperty("description") final String description,
                              @JsonProperty("signatures") final List<Signature> signatures) {
        this.name = name;
        this.aliases = aliases;
        this.functionCategory = functionCategory;
        this.description = description;
        this.signatures = signatures;
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getFunctionCategory() {
        return functionCategory;
    }

    public String getDescription() {
        return description;
    }

    public List<Signature> getSignatures() {
        return signatures;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FunctionDefinition that = (FunctionDefinition) o;
        return Objects.equals(name, that.name) && Objects.equals(aliases, that.aliases) && functionCategory == that.functionCategory && Objects.equals(description, that.description) && Objects.equals(signatures, that.signatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, aliases, functionCategory, description, signatures);
    }

    @Override
    public String toString() {
        return "FunctionDefinition{" +
                "name='" + name + '\'' +
                ", aliases=" + aliases +
                ", functionCategory=" + functionCategory +
                ", description='" + description + '\'' +
                ", signatures=" + signatures +
                '}';
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Signature {
        @JsonProperty
        private final List<Arg> args;
        @JsonProperty
        private final Type returnType;
        @JsonProperty
        private final String returnDescription;
        @JsonProperty
        private final String description;

        @JsonCreator
        public Signature(@JsonProperty("args") final List<Arg> args,
                         @JsonProperty("returnType") final Type returnType,
                         @JsonProperty("returnDescription") final String returnDescription,
                         @JsonProperty("description") final String description) {
            this.args = args;
            this.returnType = returnType;
            this.returnDescription = returnDescription;
            this.description = description;
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
            return "Signature{" +
                    "args=" + args +
                    ", returnType=" + returnType +
                    ", returnDescription='" + returnDescription + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Signature signature = (Signature) o;
            return Objects.equals(args, signature.args) && returnType == signature.returnType && Objects.equals(returnDescription, signature.returnDescription) && Objects.equals(description, signature.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(args, returnType, returnDescription, description);
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static class Arg {
        @JsonProperty
        private final String name;
        @JsonProperty
        private final Type argType;
        @JsonProperty
        private final boolean isVarargs;
        @JsonProperty
        private final int minVarargsCount;
        @JsonProperty
        private final String description;

        @JsonCreator
        public Arg(@JsonProperty("name") final String name,
                   @JsonProperty("argType") final Type argType,
                   @JsonProperty("isVarargs") final boolean isVarargs,
                   @JsonProperty("minVarargsCount") final int minVarargsCount,
                   @JsonProperty("description") final String description) {
            this.name = name;
            this.argType = argType;
            this.isVarargs = isVarargs;
            this.minVarargsCount = minVarargsCount;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public Type getArgType() {
            return argType;
        }

        public boolean isVarargs() {
            return isVarargs;
        }

        public int getMinVarargsCount() {
            return minVarargsCount;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "Arg{" +
                    "name='" + name + '\'' +
                    ", argType=" + argType +
                    ", isVarargs=" + isVarargs +
                    ", minVarargsCount=" + minVarargsCount +
                    ", description='" + description + '\'' +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Arg arg = (Arg) o;
            return isVarargs == arg.isVarargs && minVarargsCount == arg.minVarargsCount && Objects.equals(name, arg.name) && Objects.equals(argType, arg.argType) && Objects.equals(description, arg.description);
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public enum Type {

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