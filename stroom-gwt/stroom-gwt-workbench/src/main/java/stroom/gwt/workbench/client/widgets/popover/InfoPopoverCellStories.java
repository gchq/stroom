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

import stroom.cell.info.client.InfoColumn;
import stroom.data.table.client.MyCellTable;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.view.TooltipViewImpl;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;

/// Stories for Stroom's [InfoColumn].
///
/// An `InfoColumn` is a grid column of info icons; clicking one shows the row's details in a
/// [TooltipPresenter] to the right of the cell, as Stroom's monitoring screens (e.g.
/// `ProcessorListPresenter`) do. The story shows a one-row table with just that column.
public final class InfoPopoverCellStories {

    private static final String BODY = "Popover body content";
    // The trigger is an icon in a table cell, found by its cell's class
    private static final String ICON = ".svgCell-icon";
    private static final String NEIGHBOUR = "popover-neighbour";

    private InfoPopoverCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // InfoColumn's icon is always SvgPresets.INFO
        registry.component("Widgets/Popover/InfoPopoverCell", InfoPopoverCellStories.class)
                .layout(StoryLayout.CENTERED)
                // Clicking the info icon opens the anchored popover; clicking outside closes it
                .story("OpensAndCloses", InfoPopoverCellStories::render)
                .withPlay(InfoPopoverCellStories::opensAndCloses)
                // The icon is the popover's trigger: a second click on it
                .story("TriggerClickClosesAndStaysClosed", InfoPopoverCellStories::render)
                .withPlay(play -> {
                    final Play body = play.screen();
                    final Query icon = play.querySelector(ICON);

                    play.click(icon);
                    play.waitFor(() -> play.expect(body.getByText(BODY)).toBeInTheDocument());

                    play.click(icon);
                    // The icon is the popup's anchor, so its mousedown doesn't auto-hide the popup
                    // and its click toggles it shut (it once auto-hid it and showed it again)
                    play.waitFor(() -> play.expect(body.queryByText(BODY)).toBeNull());
                    // ... and it stays closed
                    play.sleep(50);
                    play.expect(body.queryByText(BODY)).toBeNull();
                });
    }

    private static void opensAndCloses(final Play play) {
        final Play body = play.screen();
        // A button beside the cell, to prove the dismissing click is not swallowed
        final Query neighbour = body.querySelector("#" + NEIGHBOUR);

        // Body not shown until opened
        play.expect(body.queryByText(BODY)).toBeNull();
        // Open
        play.click(play.querySelector(ICON));
        play.waitFor(() -> play.expect(body.getByText(BODY)).toBeInTheDocument());

        // One outside click does both jobs: dismisses the popover AND presses the button
        play.click(neighbour);
        play.waitFor(() -> play.expect(body.queryByText(BODY)).toBeNull());
        play.expect(play.spy(NEIGHBOUR)).toHaveBeenCalledTimes(1);
    }

    private static Widget render(final StoryContext context) {
        final StoryPopups popups = StoryPopups.create(context);
        final TooltipPresenter tooltipPresenter = new TooltipPresenter(popups.getEventBus(), new TooltipViewImpl());
        final InfoColumn<String> infoColumn = new InfoColumn<String>() {
            @Override
            protected void showInfo(final String row,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                tooltipPresenter.show(SafeHtmlUtils.fromSafeConstant("<div>" + BODY + "</div>"), popupPosition, anchor);
            }
        };
        final MyCellTable<String> table = new MyCellTable<>(1);
        table.addColumn(infoColumn);
        table.setRowData(0, Collections.singletonList("row"));
        table.setRowCount(1);

        // The neighbour button is added as the story renders, so that the spy that counts its
        // clicks is registered then
        final Spy neighbourClicks = context.fn(NEIGHBOUR);
        final Button neighbour = new Button("Neighbour");
        neighbour.getElement().setId(NEIGHBOUR);
        neighbour.addClickHandler(event -> neighbourClicks.call());

        final FlowPanel panel = new FlowPanel();
        panel.add(table);
        panel.add(neighbour);
        return panel;
    }
}
