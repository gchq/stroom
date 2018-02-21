package stroom.pipeline.factory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If a Pipeline Property is for a DocRef, it must also be annotated with this to indicate
 * the types of DocRef that are valid
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PipelinePropertyDocRef {
    String[] types();
}
