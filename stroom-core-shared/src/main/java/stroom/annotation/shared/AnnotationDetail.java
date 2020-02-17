package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AnnotationDetail {
    @JsonProperty
    private final Annotation annotation;
    @JsonProperty
    private final List<AnnotationEntry> entries;

    @JsonCreator
    public AnnotationDetail(@JsonProperty("annotation") final Annotation annotation,
                            @JsonProperty("entries") final List<AnnotationEntry> entries) {
        this.annotation = annotation;
        this.entries = entries;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public List<AnnotationEntry> getEntries() {
        return entries;
    }
}
