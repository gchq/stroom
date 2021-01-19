package stroom.dashboard.expression.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FunctionSignature {

    /**
     * The single category of functions that this function signature belongs to.
     * Defined as an array to allow us to not have one by default.
     */
    FunctionCategory[] category() default {};

    /**
     * An array of sub-categories that this function belongs to. The sub-categories represent a path
     * in a tree of categories from root to leaf. E.g. if the main category is String the sub categories
     * could be [Conversion, Case], i.e. String -> Conversion -> Case.
     */
    String[] subCategories() default {};

    /**
     * The description of what this signature of the function does.
     * You must specify either this or {@link FunctionDef#commonDescription()}
     */
    String description() default "";

    FunctionArg[] args();

    /**
     * The single return type of the function.
     * You must specify either this or {@link FunctionDef#commonReturnType()}
     * Defined as an array to allow to be optional.
     */
    Class<? extends Val>[] returnType() default {};

    /**
     * The description of what this signature of the function returns.
     * You should specify either this or {@link FunctionDef#commonReturnDescription()}
     */
    String returnDescription() default "";
}
