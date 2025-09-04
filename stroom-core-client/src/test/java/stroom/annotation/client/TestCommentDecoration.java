package stroom.annotation.client;

import stroom.widget.util.client.HtmlBuilder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestCommentDecoration {

    @Test
    void test() {
        final HtmlBuilder builder = new HtmlBuilder();
        AnnotationEditPresenter.decorateComment(
                builder,
                "This is related to #6 and #3, and events 739:10, 739:14 and 739:22 is where it happens");

        assertThat(builder.toSafeHtml().toString())
                .isEqualTo("safe: \"This is related to " +
                           "<u class=\"annotationLink\" annotationId=\"6\">#6</u>" +
                           " and " +
                           "<u class=\"annotationLink\" annotationId=\"3\">#3</u>" +
                           ", " +
                           "and events " +
                           "<u class=\"annotationLink\" eventId=\"739:10\">739:10</u>" +
                           ", " +
                           "<u class=\"annotationLink\" eventId=\"739:14\">739:14</u>" +
                           " and " +
                           "<u class=\"annotationLink\" eventId=\"739:22\">739:22</u>" +
                           " " +
                           "is where it happens\"");
    }
}
