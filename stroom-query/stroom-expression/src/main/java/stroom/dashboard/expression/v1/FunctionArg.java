package stroom.dashboard.expression.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FunctionArg {

    /**
     * The name of the argument, typically lower camel case, e.g. inputValue. If the arg is
     * varargs then the name should be singular as numbers will be appended to it automatically
     * in the expression editor menus/snippets.
     */
    String name();

    /**
     * The type of the argument. Use the {@link Val} or {@link ValNumber} or interfaces
     * if multiple types are supported.
     */
    Class<? extends Val> argType();

    /**
     * @return True if the argument is optional. As arguments are positional rather than named
     * all arguments after an optional argument must also be optional. You can either make arguments
     * optional or define multiple overloaded signatures.
     */
    boolean isOptional() default false;

    /**
     * @return True if this argument is a varargs parameter, i.e. arg...
     */
    boolean isVarargs() default false;

    /**
     * If the argument is a varargs argument then this specifies the minimum number of arguments
     * required.
     */
    int minVarargsCount() default 0;

    /**
     * A description of the argument.
     */
    String description() default "";

    /**
     * If the argument takes a finite set of values then specify them here.
     */
    String[] allowedValues() default {};

    /**
     * If the argument has a default value set it here. It can then be used as a default value
     * for completion snippets and displayed in the menu help.
     * Default value is a string as it may be another expression, e.g. 'null()' or a field '${EventId}'.
     */
    String defaultValue() default "";
}
