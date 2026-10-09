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


package stroom.gwt.workbench.client.widgets.overlays;

import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.view.TooltipViewImpl;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [TooltipPresenter] shown at a point.
public final class TooltipWidgetStories {

    private static final int CURSOR_OFFSET = 12;

    // Arg names
    private static final String TEXT = "text";
    private static final String VISIBLE = "visible";
    private static final String X = "x";
    private static final String Y = "y";

    private TooltipWidgetStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Overlays/TooltipWidget", TooltipWidgetStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.text(TEXT).description("The tooltip text."))
                .argType(ArgType.bool(VISIBLE).description("Whether the tooltip is shown."))
                .argType(ArgType.number(X).description("Left position in viewport pixels."))
                .argType(ArgType.number(Y).description("Top position in viewport pixels."))
                .args(Args.of(TEXT, "Tooltip text", VISIBLE, true, X, 40, Y, 40))
                // Always-visible tooltip at a fixed position
                .story("Visible", TooltipWidgetStories::fromArgs)
                .withArgs(Args.of(TEXT, "This is a tooltip", VISIBLE, true, X, 60, Y, 60))
                // Hover the target to follow the cursor with a tooltip
                .story("FollowsCursor", TooltipWidgetStories::followsCursor);
    }

    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        if (StoryArgs.getBoolean(args, VISIBLE, true)) {
            final StoryPopups popups = StoryPopups.create(context);
            final TooltipPresenter tooltipPresenter = new TooltipPresenter(popups.getEventBus(),
                    new TooltipViewImpl());
            final Long x = StoryArgs.getLong(args, X);
            final Long y = StoryArgs.getLong(args, Y);
            tooltipPresenter.show(args.getString(TEXT, ""), new PopupPosition(
                    x == null
                            ? 0
                            : x.intValue(),
                    y == null
                            ? 0
                            : y.intValue()));
        }
        // The tooltip is a popup, so nothing is rendered in place
        return new FlowPanel();
    }

    private static Widget followsCursor(final StoryContext context) {
        final StoryPopups popups = StoryPopups.create(context);
        final TooltipPresenter tooltipPresenter = new TooltipPresenter(popups.getEventBus(), new TooltipViewImpl());
        final Label target = new Label("Hover me");
        final Style style = target.getElement().getStyle();
        style.setProperty("width", "240px");
        style.setProperty("height", "120px");
        style.setProperty("border", "1px dashed var(--panel__border-color)");
        style.setProperty("display", "grid");
        style.setProperty("placeItems", "center");

        final boolean[] showing = {false};
        target.addDomHandler(event -> {
            // A Stroom popup can't be moved once shown, so show it again at the new point
            if (showing[0]) {
                HidePopupEvent.builder(tooltipPresenter).fire();
            }
            showing[0] = true;
            tooltipPresenter.show("Following the cursor", new PopupPosition(
                    event.getClientX() + CURSOR_OFFSET,
                    event.getClientY() + CURSOR_OFFSET));
        }, MouseMoveEvent.getType());
        target.addDomHandler(event -> {
            if (showing[0]) {
                showing[0] = false;
                HidePopupEvent.builder(tooltipPresenter).fire();
            }
        }, MouseOutEvent.getType());
        return target;
    }
}
