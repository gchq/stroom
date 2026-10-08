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

import stroom.cell.info.client.SvgCell;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;

import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [SvgCell], matching `Widgets/Cell Renderers/SvgCell` in the React
/// Storybook. Each cell is a `CellWidget`, whose value is the cell's [Preset] (icon, title and
/// enabled state). A clickable cell (`new SvgCell(true)`) reports a click by updating the value,
/// which the `CellWidget` reports as a value change.
public final class SvgCellStories {

    // Arg names, the same as the React SvgCell's props
    private static final String ICON = "icon";
    private static final String TITLE = "title";
    private static final String ENABLED = "enabled";
    private static final String ON_CLICK = "onClick";

    private SvgCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/SvgCell", SvgCellStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.select(ICON, "INFO", "COPY", "DELETE", "ADD", "EDIT")
                        .description("The icon (GWT Preset's SvgImage).")
                        .typeName("SvgImage"))
                .argType(ArgType.text(TITLE).description("The icon's tooltip (GWT Preset's title)."))
                .argType(ArgType.bool(ENABLED).description("GWT Preset's enabled state.").defaultSummary("true"))
                // React's onClick is only given by ClickableIcons; a cell from the args is a static icon
                // (new SvgCell(false)), as GWT decides whether a cell is a button when it is created
                .args(Args.of(ICON, "INFO"))
                // Clickable icons: info and delete fire; copy is disabled
                .story("ClickableIcons", SvgCellStories::clickableIcons)
                .withPlay(play -> {
                    play.click(play.getByTitle("View info"));
                    play.expect(play.spy(ON_CLICK)).toHaveBeenCalledWith("info");
                    // The disabled icon does nothing (it once acted, though drawn as disabled)
                    play.click(play.getByTitle("Copy (disabled)"));
                    play.expect(play.spy(ON_CLICK)).not().toHaveBeenCalledWith("copy");
                    play.expect(play.spy(ON_CLICK)).toHaveBeenCalledTimes(1);
                })
                // A static (non-button) icon, with no click handler
                .story("StaticIcon", SvgCellStories::fromArgs)
                .withArgs(Args.of(ICON, "INFO", TITLE, "Information"));
    }

    /// A static icon made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final SvgImage icon = StoryArgs.toSvgImage(args.getString(ICON));
        if (icon == null) {
            // As React's SvgCell with no icon, which renders nothing
            return new InlineLabel();
        }
        final Preset preset = new Preset(icon,
                args.getString(TITLE),
                StoryArgs.getBoolean(args, ENABLED, true));
        return new CellWidget<>(new SvgCell(false), preset);
    }

    private static Widget clickableIcons(final StoryContext context) {
        final InlineLabel clicked = new InlineLabel(CellRendererWidgets.NO_ACTION);
        clicked.getElement().getStyle().setProperty("fontSize", "12px");
        final Spy onClick = context.fn(ON_CLICK);

        final CellWidget<Preset> info = pressable(new Preset(SvgImage.INFO, "View info", true), () -> {
            onClick.call("info");
            clicked.setText("info clicked");
        });
        final CellWidget<Preset> delete = pressable(new Preset(SvgImage.DELETE, "Delete", true), () -> {
            onClick.call("delete");
            clicked.setText("delete clicked");
        });
        // A disabled preset greys the icon (svgCell-disabled) and ignores clicks, so this never runs
        final CellWidget<Preset> copy = pressable(new Preset(SvgImage.COPY, "Copy (disabled)", false), () -> {
            onClick.call("copy");
            clicked.setText("copy clicked");
        });

        return StoryPanels.row(12, info, delete, copy, clicked);
    }

    // A clickable icon cell that runs onPress when it is pressed. A pressed SvgCell updates its value
    // with the same preset, and CellWidget only reports a value that changes, so the cell's updates
    // are caught here instead.
    private static CellWidget<Preset> pressable(final Preset preset, final Runnable onPress) {
        return new CellWidget<>(new SvgCell(true), preset) {
            @Override
            public void setValue(final Preset value, final boolean fireEvents, final boolean redraw) {
                if (fireEvents) {
                    onPress.run();
                } else {
                    super.setValue(value, false, redraw);
                }
            }
        };
    }
}
