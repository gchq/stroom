package stroom.annotation.shared;

import java.util.List;

public class AnnotationDetail {
    private Annotation annotation;
    private List<AnnotationEntry> entries;

    public AnnotationDetail() {
    }

    public AnnotationDetail(final Annotation annotation, final List<AnnotationEntry> entries) {
        this.annotation = annotation;
        this.entries = entries;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(final Annotation annotation) {
        this.annotation = annotation;
    }

    public List<AnnotationEntry> getEntries() {
        return entries;
    }

    public void setEntries(final List<AnnotationEntry> entries) {
        this.entries = entries;
    }
}
