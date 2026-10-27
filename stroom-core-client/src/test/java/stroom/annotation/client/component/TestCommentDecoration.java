/*
 * Copyright 2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.annotation.client.component;

import stroom.widget.util.client.HtmlBuilder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestCommentDecoration {

    @Test
    void test() {
        final HtmlBuilder builder = new HtmlBuilder();
        AnnotationEditController.decorateComment(
                builder,
                "This is related to #6 and #3, and events 739:10, 739:14 and 739:22 is where it happens");

        assertThat(builder.toSafeHtml().toString())
                .isEqualTo("safe: \"This is related to " +
                           "<u class=\"annotationLink\" annotationId=\"6\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Annotation 6\">#6</u>" +
                           " and " +
                           "<u class=\"annotationLink\" annotationId=\"3\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Annotation 3\">#3</u>" +
                           ", " +
                           "and events " +
                           "<u class=\"annotationLink\" eventId=\"739:10\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Event 739:10\">739:10</u>" +
                           ", " +
                           "<u class=\"annotationLink\" eventId=\"739:14\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Event 739:14\">739:14</u>" +
                           " and " +
                           "<u class=\"annotationLink\" eventId=\"739:22\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Event 739:22\">739:22</u>" +
                           " " +
                           "is where it happens\"");
    }

    @Test
    void testAdjacentReferencesAreSpacedApart() {
        final HtmlBuilder builder = new HtmlBuilder();
        AnnotationEditController.decorateComment(builder, "#5#6 and 1:2#7");

        // An em space between links with nothing between them, so each is a target of its own
        assertThat(builder.toSafeHtml().asString())
                .isEqualTo("<u class=\"annotationLink\" annotationId=\"5\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Annotation 5\">#5</u>\u2003"
                           + "<u class=\"annotationLink\" annotationId=\"6\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Annotation 6\">#6</u>"
                           + " and "
                           + "<u class=\"annotationLink\" eventId=\"1:2\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Event 1:2\">1:2</u>\u2003"
                           + "<u class=\"annotationLink\" annotationId=\"7\" role=\"link\" tabindex=\"0\" "
                           + "aria-label=\"Annotation 7\">#7</u>");
    }
}
