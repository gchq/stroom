package stroom.dashboard.expression.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FunctionArg {

    String name();

    Class<? extends Val> argType();

    boolean isVarargs() default false;

    int minVarargsCount() default 0;

    String description() default "";
}
