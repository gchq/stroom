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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.view.TooltipViewImpl;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's tooltips.
///
/// Stroom has two kinds: a [TooltipPresenter] popup shown next to a widget at a `PopupLocation`,
/// and the native `title` that Stroom gives its toolbar buttons ([InlineSvgButton]).
public final class TooltipStories {

    private TooltipStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Overlays/Tooltip", TooltipStories.class)
                .layout(StoryLayout.CENTERED)
                // Tooltip — all four positions
                .story("Positions", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final FlowPanel row = StoryPanels.row(32,
                            hoverTooltip(popups, "Hover me (bottom)", "This appears below", PopupLocation.BELOW),
                            hoverTooltip(popups, "Hover me (top)", "This appears above", PopupLocation.ABOVE),
                            hoverTooltip(popups, "Hover me (right)", "This appears to the right",
                                    PopupLocation.RIGHT),
                            hoverTooltip(popups, "Hover me (left)", "This appears to the left",
                                    PopupLocation.LEFT));
                    final Style style = row.getElement().getStyle();
                    style.setProperty("padding", "32px");
                    style.setProperty("justifyContent", "center");
                    return row;
                })
                // Tooltip — on icon buttons (GWT pattern for toolbar items)
                .story("OnIcon", context -> {
                    // Stroom's toolbar buttons show their title as the browser's own tooltip, so it
                    // has no position or styling.
                    final FlowPanel row = StoryPanels.row(16,
                            iconButton(SvgImage.ADD, "Add a new item"),
                            iconButton(SvgImage.DELETE, "Delete selected item"),
                            iconButton(SvgImage.EDIT, "Edit properties"),
                            iconButton(SvgImage.FILTER, "Filter results"));
                    row.getElement().getStyle().setProperty("padding", "16px");
                    return row;
                })
                // Tooltip — disabled (no tooltip shown)
                .story("Disabled", context -> {
                    // A disabled tooltip: none is shown
                    final FlowPanel panel = new FlowPanel();
                    panel.getElement().getStyle().setProperty("padding", "24px");
                    panel.add(button("No tooltip (disabled)"));
                    return panel;
                });
    }

    /// A button that shows a [TooltipPresenter] beside it while the pointer is over it.
    private static Widget hoverTooltip(final StoryPopups popups,
                                       final String text,
                                       final String tooltip,
                                       final PopupLocation location) {
        final Button button = button(text);
        final TooltipPresenter tooltipPresenter = new TooltipPresenter(popups.getEventBus(), new TooltipViewImpl());
        final boolean[] showing = {false};
        button.addDomHandler(event -> {
            if (!showing[0]) {
                showing[0] = true;
                tooltipPresenter.show(tooltip, new PopupPosition(new Rect(button.getElement()), location));
            }
        }, MouseOverEvent.getType());
        button.addDomHandler(event -> {
            if (showing[0]) {
                showing[0] = false;
                HidePopupEvent.builder(tooltipPresenter).fire();
            }
        }, MouseOutEvent.getType());
        return button;
    }

    private static Button button(final String text) {
        final Button button = new Button();
        button.setText(text);
        return button;
    }

    private static Widget iconButton(final SvgImage icon, final String title) {
        final InlineSvgButton button = new InlineSvgButton();
        button.setSvg(icon);
        button.setTitle(title);
        button.setEnabled(true);
        return button;
    }
}
