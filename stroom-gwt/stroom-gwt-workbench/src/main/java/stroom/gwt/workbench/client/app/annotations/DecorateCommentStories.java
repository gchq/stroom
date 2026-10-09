/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.app.annotations;

import stroom.annotation.client.EditAnnotationEvent;
import stroom.data.client.presenter.ShowDataEvent;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.main.AnnotationFixtures;
import stroom.gwt.workbench.client.app.main.ContentStorySupport;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// The `App/Annotations/decorateComment` stories: Stroom's
/// `AnnotationEditPresenter.decorateComment`, which turns `#<digits>` at the start of a word into
/// an annotation link and `<streamId>:<eventId>` into an event link.
///
/// `decorateComment` is a package-private static method of `AnnotationEditPresenter`, used only to
/// render the comments in an annotation's history, so these stories show the real annotation editor
/// with the comments as its history entries (see [AnnotationFixtures]) and check the links it
/// renders (`<u>` elements), and what pressing them does (`EditAnnotationEvent` and
/// `ShowDataEvent`, recorded by the spy `opened`).
public final class DecorateCommentStories {

    /// The name of the spy recording the links opened.
    static final String OPENED = "opened";

    private static final String[] COMMENTS = {
            "see #42 and 1234:5 done",
            // A token must start a word: mid-word is not a link
            "a#42",
            "x1234:5",
            // '#' with no digits, or a stream id with no ':event', stay plain text
            "#notanid",
            "1234 alone",
            // GWT leaves 'start' true after a token, so adjacent references both link
            "#5#6",
            // No links: a single plain text node
            "just a comment"};

    private DecorateCommentStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Annotations/decorateComment", DecorateCommentStories.class)
                // The stories show the annotation editor, so they fill the page
                .layout(StoryLayout.FULLSCREEN)
                // Annotation (#id) and event (stream:event) references become links; text between them
                // is plain. Each comment's rendering is shown as its text with each link in brackets
                .story("DecoratesLinks", context -> render(context, COMMENTS))
                .withPlay(play -> {
                    play.findByText("just a comment");
                    final Query history = play.querySelector(".annotationHistoryInner");
                    play.expect("the comments as rendered", () -> commentShapes(history.element().get()))
                            .toEqual(Arrays.asList(
                                    "see |[#42]| and |[1234:5]| done",
                                    "a#42",
                                    "x1234:5",
                                    "#notanid",
                                    "1234 alone",
                                    "[#5]|[#6]",
                                    "just a comment"));
                    ContentStorySupport.expectNoProblems(play);
                })
                // The links open what they refer to
                .story("LinksInvokeHandlers", context -> render(context, "#7 then 100:200"))
                .withPlay(play -> {
                    play.findByText("#7");
                    final Query links = play.querySelectorAll("u.annotationLink");
                    play.expect(links).toHaveLength(2);
                    // Stroom opens a link on a mousedown on it
                    play.fireEvent().mouseDown(links.nth(0));
                    play.fireEvent().mouseDown(links.nth(1));
                    play.waitFor(() -> play.expect(play.spy(OPENED).callCount()).toBe(2));
                    play.expect(play.spy(OPENED)).toHaveBeenNthCalledWith(1, "ann:7");
                    play.expect(play.spy(OPENED)).toHaveBeenNthCalledWith(2, "evt:100:200");
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    // Each comment body's nodes: text as it is, each link in brackets, joined with '|'
    private static List<String> commentShapes(final Element history) {
        final List<String> shapes = new ArrayList<>();
        final NodeList<Element> bodies = history.getElementsByTagName("div");
        for (int i = 0; i < bodies.getLength(); i++) {
            final Element body = bodies.getItem(i);
            if (body.hasClassName("annotationHistoryCommentBody")) {
                final List<String> parts = new ArrayList<>();
                final NodeList<Node> nodes = body.getChildNodes();
                for (int j = 0; j < nodes.getLength(); j++) {
                    final Node node = nodes.getItem(j);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        parts.add("[" + Element.as(node).getInnerText() + "]");
                    } else {
                        parts.add(node.getNodeValue());
                    }
                }
                shapes.add(String.join("|", parts));
            }
        }
        return shapes;
    }

    private static Widget render(final StoryContext context, final String... comments) {
        final List<String> entries = new ArrayList<>();
        for (int i = 0; i < comments.length; i++) {
            entries.add(AnnotationFixtures.entry(i + 1, "COMMENT", "Alice", 1700000000000L + i, comments[i], null));
        }
        final RestFixtures fixtures = AnnotationFixtures.editorRoutes(RestFixtures.builder(),
                "[" + String.join(", ", entries) + "]").build();
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        harness.fn(OPENED);
        harness.addRegistration(harness.getEventBus().addHandler(EditAnnotationEvent.getType(), event ->
                harness.spy(OPENED, "ann:" + event.getAnnotationId())));
        harness.addRegistration(harness.getEventBus().addHandler(ShowDataEvent.getType(), event ->
                harness.spy(OPENED, "evt:" + event.getSourceLocation().getMetaId() + ":"
                        + (event.getSourceLocation().getRecordIndex() + 1))));
        harness.afterStartUp(() -> AnnotationFixtures.open(injector, harness, 42L));
        return harness.asWidget();
    }
}
