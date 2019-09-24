package stroom.annotation.shared;

import java.util.List;

public class AnnotationDetail {
    private final Annotation annotation;
    private final List<AnnotationEntry> entries;

    public AnnotationDetail(final Annotation annotation, final List<AnnotationEntry> entries) {
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
