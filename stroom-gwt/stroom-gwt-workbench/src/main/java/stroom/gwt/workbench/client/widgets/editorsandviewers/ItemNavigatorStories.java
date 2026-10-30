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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.data.client.component.ItemNavigator;
import stroom.data.client.component.ItemNavigatorController;
import stroom.data.client.component.ItemSelection;
import stroom.data.client.component.ItemSelectionController;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.Count;
import stroom.util.shared.HasItems;
import stroom.util.shared.OffsetRange;

import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [ItemNavigator] (the data viewer's "Record 3 of 10" link and its First,
/// Previous, Next, Last and Refresh buttons) and the item selection dialog its link opens
/// ([ItemSelectionController]), with records shown one at a time.
public final class ItemNavigatorStories {

    private static final String ON_SET_ITEM_NO = "onSetItemNo";
    private static final String ON_REFRESH = "onRefresh";
    private static final String DIALOG = "Select Record";
    private static final long TEN = 10;
    private static final long THREE_BILLION = 3_000_000_000L;

    private ItemNavigatorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/ItemNavigator", ItemNavigatorStories.class)
                .layout(StoryLayout.CENTERED)
                // The third of ten records
                .story("Default", context -> render(context, TEN))
                .withPlay(play -> {
                    play.findByText("Record 3 of 10");
                    // A toolbar button is disabled with aria-disabled, staying in the tab order
                    play.expect(play.getByTitle("First record")).not().toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByTitle("Last record")).not().toHaveAttribute("aria-disabled", "true");
                })
                // The buttons move between the records; the first has no First or Previous
                .story("Buttons", context -> render(context, TEN))
                .withPlay(play -> {
                    play.click(play.getByTitle("Next record"));
                    play.findByText("Record 4 of 10");
                    // Previous from the second record reaches the first, where First and Previous
                    // can't be pressed
                    play.click(play.getByTitle("First record"));
                    play.findByText("Record 1 of 10");
                    play.click(play.getByTitle("Next record"));
                    play.findByText("Record 2 of 10");
                    play.click(play.getByTitle("Previous record"));
                    play.findByText("Record 1 of 10");
                    play.expect(play.getByTitle("First record")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByTitle("Previous record")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByTitle("Next record")).not().toHaveAttribute("aria-disabled", "true");
                    // The refresh button, found by its class as its title has its shortcut too
                    play.click(play.querySelector(".refresh-button"));
                    play.expect(play.spy(ON_REFRESH)).toHaveBeenCalledTimes(1);
                })
                // The link opens the dialog on the record shown; OK goes to the record chosen
                .story("SelectRecord", context -> render(context, TEN))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.getByRole("button", "Select Record (Record 3 of 10)"));
                    final Play dialog = screen.within(screen.findByText(DIALOG).closest(StroomDom.DIALOG));
                    final Query itemNo =
                            dialog.getByRole("spinbutton", "Item number");
                    play.expect(itemNo).toHaveValue("3");
                    play.expect(dialog.querySelector(".item-selection__count")).toHaveTextContent("of 10");
                    play.clear(itemNo);
                    play.type(itemNo, "7");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText(DIALOG)).toBeNull());
                    // Zero based
                    play.expect(play.spy(ON_SET_ITEM_NO)).toHaveBeenCalledWith("6");
                    play.findByText("Record 7 of 10");
                })
                // An item beyond the largest int is chosen as it is: it was once cut to an int
                .story("SelectLargeRecordNumber", context -> render(context, THREE_BILLION))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.getByRole("button", TextMatch.containing("Select Record (Record 3 of")));
                    final Play dialog = screen.within(screen.findByText(DIALOG).closest(StroomDom.DIALOG));
                    final Query itemNo = dialog.getByRole("spinbutton", "Item number");
                    play.clear(itemNo);
                    play.type(itemNo, "2999999999");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText(DIALOG)).toBeNull());
                    play.expect(play.spy(ON_SET_ITEM_NO)).toHaveBeenCalledWith("2999999998");
                })
                // Cancel leaves the record shown
                .story("SelectRecordCancel", context -> render(context, TEN))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.getByRole("button", "Select Record (Record 3 of 10)"));
                    final Play dialog = screen.within(screen.findByText(DIALOG).closest(StroomDom.DIALOG));
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(DIALOG)).toBeNull());
                    play.expect(play.spy(ON_SET_ITEM_NO)).not().toHaveBeenCalled();
                    play.findByText("Record 3 of 10");
                });
    }

    private static Widget render(final StoryContext context, final long count) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
        final ItemNavigatorController navigator = harness.own(new ItemNavigatorController(
                () -> new ItemSelectionController(harness.getEventBus(), new ItemSelection()))
                .attach(new ItemNavigator()));
        navigator.setDisplay(new Records(navigator, count, context.fn(ON_SET_ITEM_NO), context.fn(ON_REFRESH)));
        harness.add(navigator.asWidget());
        return harness.asWidget();
    }


    // --------------------------------------------------------------------------------


    /// Records, one shown at a time, starting with the third.
    private static final class Records implements HasItems {

        private final ItemNavigatorController navigator;
        private final long count;
        private final Spy onSetItemNo;
        private final Spy onRefresh;
        private long offset = 2;

        private Records(final ItemNavigatorController navigator,
                        final long count,
                        final Spy onSetItemNo,
                        final Spy onRefresh) {
            this.navigator = navigator;
            this.count = count;
            this.onSetItemNo = onSetItemNo;
            this.onRefresh = onRefresh;
        }

        @Override
        public String getName() {
            return "Record";
        }

        @Override
        public OffsetRange getItemRange() {
            return new OffsetRange(offset, 1L);
        }

        @Override
        public void setItemNo(final long itemNo) {
            onSetItemNo.call(String.valueOf(itemNo));
            show(itemNo);
        }

        @Override
        public Count<Long> getTotalItemsCount() {
            return Count.exactly(count);
        }

        @Override
        public boolean areNavigationControlsVisible() {
            return true;
        }

        @Override
        public int getMaxItemsPerPage() {
            return 1;
        }

        @Override
        public void firstPage() {
            show(0);
        }

        @Override
        public void nextPage() {
            show(Math.min(count - 1, offset + 1));
        }

        @Override
        public void previousPage() {
            show(Math.max(0, offset - 1));
        }

        @Override
        public void lastPage() {
            show(count - 1);
        }

        @Override
        public void refresh() {
            onRefresh.call();
            navigator.refreshNavigator();
        }

        private void show(final long offset) {
            this.offset = offset;
            navigator.refreshNavigator();
        }
    }
}
