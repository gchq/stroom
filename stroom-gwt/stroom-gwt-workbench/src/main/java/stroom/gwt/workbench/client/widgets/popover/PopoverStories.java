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


package stroom.gwt.workbench.client.widgets.popover;

import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.view.TooltipViewImpl;
import stroom.widget.util.client.Rect;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;

/// Stories for Stroom's anchored popover.
///
/// The popover is the shell behind the info tooltips of Stroom's monitoring screens, which show a
/// [TooltipPresenter] (an auto-hiding `POPUP`) at a `PopupPosition` beside a rect.
public final class PopoverStories {

    private static final String ON_CLOSE = "onClose";

    private PopoverStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Popover/Popover", PopoverStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The panel renders its body, positioned against the anchor
                .story("Default", context -> {
                    final Spy onClose = context.fn(ON_CLOSE);
                    final StoryPopups popups = StoryPopups.create(context);
                    final TooltipPresenter tooltipPresenter = new TooltipPresenter(popups.getEventBus(),
                            new TooltipViewImpl());
                    popups.addCleanUp(popups.getEventBus().addHandler(HidePopupEvent.getType(), event -> {
                        if (event.getPresenterWidget() == tooltipPresenter) {
                            onClose.call();
                        }
                    })::removeHandler);
                    // An anchor {left: 100, top: 100, right: 120, bottom: 120}, to the right as
                    // InfoColumn asks
                    tooltipPresenter.show(SafeHtmlUtils.fromSafeConstant("<div>Anchored panel body</div>"),
                            new PopupPosition(new Rect(100, 120, 100, 120), PopupLocation.RIGHT));
                    return new FlowPanel();
                })
                .withPlay(play -> play.expect(play.screen().findByText("Anchored panel body")).toBeInTheDocument());
    }
}
