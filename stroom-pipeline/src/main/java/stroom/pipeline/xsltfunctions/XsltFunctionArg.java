package stroom.pipeline.xsltfunctions;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNumber;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XsltFunctionArg {

    /**
     * The name of the argument, typically lower camel case, e.g. inputValue. If the arg is
     * varargs then the name should be singular as numbers will be appended to it automatically
     * in the expression editor menus/snippets.
     */
    @JsonProperty("name")
    String name();

    /**
     * The type of the argument. Use the {@link Val} or {@link ValNumber} or interfaces
     * if multiple types are supported.
     */
    @JsonProperty("argType")
    XsltDataType argType();

    /**
     * @return True if the argument is optional. As arguments are positional rather than named
     * all arguments after an optional argument must also be optional. You can either make arguments
     * optional or define multiple overloaded signatures.
     */
    @JsonProperty("isOptional")
    boolean isOptional() default false;

    /**
     * @return True if this argument is a varargs parameter, i.e. arg...
     */
    @JsonProperty("isVarargs")
    boolean isVarargs() default false;

    /**
     * If the argument is a varargs argument then this specifies the minimum number of arguments
     * required.
     */
    @JsonProperty("minVarargsCount")
    int minVarargsCount() default 0;

    /**
     * A description of the argument.
     * <p>The description is assumed to be Markdown</p>
     */
    @JsonProperty("description")
    String description() default "";

    /**
     * If the argument takes a finite set of values then specify them here.
     */
    @JsonProperty("allowedValues")
    String[] allowedValues() default {};

    /**
     * If the argument has a default value set it here. It can then be used as a default value
     * for completion snippets and displayed in the menu help.
     * Default value is a string as it may be another expression, e.g. 'null()' or a field '${EventId}'.
     */
    @JsonProperty("defaultValue")
    String defaultValue() default "";

}
