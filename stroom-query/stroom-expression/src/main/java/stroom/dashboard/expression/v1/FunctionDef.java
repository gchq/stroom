package stroom.dashboard.expression.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FunctionDef {

    String name();
//    String displayName();

    String[] aliases() default {};

    FunctionCategory category();

    String description();

    FunctionSignature[] signatures();

}
