package stroom.dashboard.expression.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(FunctionSignatures.class)
public @interface FunctionSignature {

//    String[] args() default {};
//    ArgType[] argTypes() default {};
//    ArgType returnType();
//    String[] argDescriptions() default {};
    FunctionArg[] args();
    ArgType returnType();
    String description() default "";
}
