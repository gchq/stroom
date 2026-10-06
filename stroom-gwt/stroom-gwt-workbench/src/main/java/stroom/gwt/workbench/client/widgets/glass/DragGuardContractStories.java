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


package stroom.gwt.workbench.client.widgets.glass;

import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for the glass that Stroom's [ThinSplitLayoutPanel] puts over the page while a splitter
/// is dragged, so that the drag isn't lost when the pointer crosses an iframe, matching
/// `Widgets/Glass/Drag guard contract` in the React Storybook.
public final class DragGuardContractStories {

    // Differs from React: the splitter's glass has no class (React's is `.popupPanel-dragGlass`,
    // which is the class of the glass Stroom's dialogs raise while they are dragged). It is a div
    // on the body with an inline opacity of 0 and a resize cursor, so is found by those.
    private static final String GLASS = "body > div[style*='opacity: 0'][style*='resize']";
    private static final String DRAGGER = ".thinSplitLayoutPanel-Dragger";
    private static final int FIRST_SIZE = 300;

    private DragGuardContractStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Glass/Drag guard contract", DragGuardContractStories.class)
                .layout(StoryLayout.CENTERED)
                // Nothing is over the page until a drag starts
                .story("NoGlassWhenIdle", context -> splitOverIframe())
                .withPlay(play -> {
                    // React's isGlassShowing() and its glass element are the same check here: the
                    // glass is only on the page while it is up
                    play.expect(play.screen().querySelector(GLASS)).toBeNull();
                })
                // A splitter drag raises the glass for its duration and drops it on mouseup
                .story("SplitterDragRaisesAndDropsGlass", context -> splitOverIframe())
                .withPlay(play -> {
                    final Query glass = play.screen().querySelector(GLASS);
                    play.fireEvent().mouseDown(play.querySelector(DRAGGER), EventInit.create().at(300, 150));
                    play.waitFor(() -> play.expect(glass).toBeInTheDocument());

                    play.fireEvent().mouseMove(play.body(), EventInit.create().at(360, 150));
                    play.expect(glass).toBeInTheDocument();

                    play.fireEvent().mouseUp(play.body());
                    play.waitFor(() -> play.expect(glass).toBeNull());
                })
                // The glass must be invisible, must cover the viewport, and must still receive events
                .story("GlassIsTransparentCoveringAndHittable", context -> splitOverIframe())
                .withPlay(DragGuardContractStories::transparentCoveringAndHittable)
                // An interrupted drag must not strand the glass over the page
                .story("GlassIsDroppedIfTheDragIsInterrupted", context -> splitOverIframe())
                .withPlay(play -> {
                    final Query glass = play.screen().querySelector(GLASS);
                    play.fireEvent().mouseDown(play.querySelector(DRAGGER), EventInit.create().at(300, 150));
                    play.waitFor(() -> play.expect(glass).toBeInTheDocument());
                    // React: the widget's unmount clean up runs the same teardown as mouseup
                    play.fireEvent().mouseUp(play.body());
                    play.waitFor(() -> play.expect(glass).toBeNull());
                    play.expect(play.screen().querySelectorAll(GLASS)).toHaveLength(0);
                });
    }

    private static void transparentCoveringAndHittable(final Play play) {
        final Query glass = play.screen().querySelector(GLASS);
        play.fireEvent().mouseDown(play.querySelector(DRAGGER), EventInit.create().at(300, 150));
        play.waitFor(() -> play.expect(glass).toBeInTheDocument());

        play.expect("glass must receive events, not pass them through", glass.computedStyle("pointer-events"))
                .not().toBe("none");
        play.expect("glass must be visible to hit-testing", glass.computedStyle("visibility")).toBe("visible");
        // Differs from React: the glass doesn't tint the page because it is white with an opacity
        // of 0 (as GWT's own SplitLayoutPanel's is), not because its background is transparent.
        play.expect("glass must not tint the page", glass.computedStyle("opacity")).toBe("0");
        play.expect(glass.computedStyle("background-color")).toBe("rgb(255, 255, 255)");
        play.expect("glass carries the drag cursor", glass.computedStyle("cursor")).toBe("col-resize");
        // Differs from React: the glass is sized to the body's scroll size less 1px, so is 1px
        // short of the viewport in each direction.
        play.expect("glass must span the viewport width", glass.width())
                .toBeGreaterThanOrEqual(Window.getClientWidth() - 1);
        play.expect("glass must span the viewport height", glass.height())
                .toBeGreaterThanOrEqual(Window.getClientHeight() - 1);

        play.fireEvent().mouseUp(play.body());
        play.waitFor(() -> play.expect(glass).toBeNull());
    }

    /// A splitter with an iframe on one side, the shape that needs the glass.
    private static Widget splitOverIframe() {
        final ThinSplitLayoutPanel split = new ThinSplitLayoutPanel();
        final Label editor = new Label("editor pane");
        editor.getElement().getStyle().setProperty("padding", "8px");
        split.addWest(editor, FIRST_SIZE);

        final IFrameElement iframe = Document.get().createIFrameElement();
        iframe.setTitle("preview");
        iframe.setAttribute("srcdoc", "<body style='margin:0;font:12px sans-serif'>preview iframe</body>");
        final Style iframeStyle = iframe.getStyle();
        iframeStyle.setProperty("width", "100%");
        iframeStyle.setProperty("height", "100%");
        iframeStyle.setProperty("border", "0");
        final FlowPanel preview = new FlowPanel();
        preview.getElement().appendChild(iframe);
        split.add(preview);

        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("height", "320px");
        style.setProperty("width", "700px");
        style.setProperty("border", "1px solid #444");
        // A layout panel needs a size to lay its children out in
        style.setProperty("position", "relative");
        split.setSize("100%", "100%");
        frame.add(split);
        return frame;
    }
}
