package stroom.dashboard.expression.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FunctionDefinition {

    String name();
//    String displayName();
    String[] aliases() default {};
    FunctionCategory category();
    String description();
    FunctionSignature[] signatures();

    public static enum FunctionCategory {
        DATE("Date"),
        MATHS("Mathematics");

        private final String name;

        FunctionCategory(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
