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

package stroom.gwt.workbench.client.widgets.cellrenderers;

import stroom.cell.client.CellIcon;
import stroom.cell.client.IconCell;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [IconCell]. Each cell is a `CellWidget`, whose value is the cell's [CellIcon]
/// (icon, name and enabled state). A clickable cell (`new IconCell(true)`) reports a click by
/// updating the value, which the `CellWidget` reports as a value change.
public final class IconCellStories {

    // Arg names
    private static final String ICON = "icon";
    private static final String TITLE = "title";
    private static final String ENABLED = "enabled";
    private static final String ON_CLICK = "onClick";

    private IconCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/IconCell", IconCellStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.select(ICON, "INFO", "COPY", "DELETE", "ADD", "EDIT")
                        .description("The icon (GWT CellIcon's SvgImage).")
                        .typeName("SvgImage"))
                .argType(ArgType.text(TITLE).description("The icon's tooltip (GWT CellIcon's title)."))
                .argType(ArgType.bool(ENABLED).description("GWT CellIcon's enabled state.").defaultSummary("true"))
                // Only ClickableIcons has clickable cells; a cell from the args is a static icon
                // (new IconCell(false)), as GWT decides whether a cell is a button when it is created
                .args(Args.of(ICON, "INFO"))
                // Clickable icons: info and delete fire; copy is disabled
                .story("ClickableIcons", IconCellStories::clickableIcons)
                .withPlay(play -> {
                    play.click(play.getByTitle("View info"));
                    play.expect(play.spy(ON_CLICK)).toHaveBeenCalledWith("info");
                    // The disabled icon does nothing (it once acted, though drawn as disabled)
                    play.click(play.getByTitle("Copy (disabled)"));
                    play.expect(play.spy(ON_CLICK)).not().toHaveBeenCalledWith("copy");
                    play.expect(play.spy(ON_CLICK)).toHaveBeenCalledTimes(1);
                })
                // A static (non-button) icon, with no click handler
                .story("StaticIcon", IconCellStories::fromArgs)
                .withArgs(Args.of(ICON, "INFO", TITLE, "Information"));
    }

    /// A static icon made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final SvgImage icon = StoryArgs.toSvgImage(args.getString(ICON));
        if (icon == null) {
            // No icon: render nothing
            return new InlineLabel();
        }
        final CellIcon cellIcon = CellIcon.of(icon,
                args.getString(TITLE),
                StoryArgs.getBoolean(args, ENABLED, true));
        return new CellWidget<>(new IconCell(false), cellIcon);
    }

    private static Widget clickableIcons(final StoryContext context) {
        final InlineLabel clicked = new InlineLabel(CellRendererWidgets.NO_ACTION);
        clicked.getElement().getStyle().setProperty("fontSize", "12px");
        final Spy onClick = context.fn(ON_CLICK);

        final CellWidget<CellIcon> info = pressable(CellIcon.of(SvgImage.INFO, "View info"), () -> {
            onClick.call("info");
            clicked.setText("info clicked");
        });
        final CellWidget<CellIcon> delete = pressable(CellIcon.of(SvgImage.DELETE, "Delete"), () -> {
            onClick.call("delete");
            clicked.setText("delete clicked");
        });
        // A disabled cell icon greys the icon (icon-cell--disabled) and ignores clicks, so this never runs
        final CellWidget<CellIcon> copy = pressable(CellIcon.of(SvgImage.COPY, "Copy (disabled)", false), () -> {
            onClick.call("copy");
            clicked.setText("copy clicked");
        });

        return StoryPanels.row(12, info, delete, copy, clicked);
    }

    // A clickable icon cell that runs onPress when it is pressed. A pressed IconCell updates its value
    // with the same cell icon, and CellWidget only reports a value that changes, so the cell's updates
    // are caught here instead.
    private static CellWidget<CellIcon> pressable(final CellIcon cellIcon, final Runnable onPress) {
        return new CellWidget<>(new IconCell(true), cellIcon) {
            @Override
            public void setValue(final CellIcon value, final boolean fireEvents, final boolean redraw) {
                if (fireEvents) {
                    onPress.run();
                } else {
                    super.setValue(value, false, redraw);
                }
            }
        };
    }
}
