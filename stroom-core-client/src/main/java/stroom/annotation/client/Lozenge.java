package stroom.annotation.client;

import stroom.annotation.shared.AnnotationTag;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.security.client.presenter.ClassNameBuilder;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;

public class Lozenge {

    public static final String LOZENGE = "lozenge";

    public static SafeHtml create(final AnnotationTag annotationTag) {
        return create(annotationTag.getStyle(), annotationTag.getName());
    }

    public static SafeHtml create(final ConditionalFormattingStyle formattingStyle,
                                  final String name) {
        final HtmlBuilder builder = new HtmlBuilder();
        append(builder, formattingStyle, name);
        return builder.toSafeHtml();
    }

    public static void append(final HtmlBuilder builder,
                              final AnnotationTag annotationTag) {
        if (annotationTag != null) {
            append(builder, annotationTag.getStyle(), annotationTag.getName());
        }
    }

    public static void append(final HtmlBuilder builder,
                              final ConditionalFormattingStyle formattingStyle,
                              final String name) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        classNameBuilder.addClassName(LOZENGE);
        if (formattingStyle != null) {
            classNameBuilder.addClassName(formattingStyle.getCssClassName());
        }

        builder.div(div -> {
            div.append(name);
        }, Attribute.className(classNameBuilder.build()));
    }
}
