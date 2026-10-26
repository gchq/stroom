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

package stroom.gwt.workbench.client.app.editors;

import stroom.docref.DocRef;
import stroom.floormap.client.component.FloorMapEventStoreSettings;
import stroom.floormap.client.component.FloorMapEventStoreSettingsController;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Editors/FloorMapEventStoreSettings`, showing Stroom's real
/// [FloorMapEventStoreSettingsController] (a floor map event store's 'Settings' tab: how long an
/// entity stays on the map, and the Plan B settings a user may change once data exists) for a
/// store whose events expire after 2 days. It makes no REST calls.
public final class FloorMapEventStoreSettingsStories {

    private static final String UUID = "event-store-1";
    private static final String ON_WRITE = "onWrite";
    private static final String EXPIRY = "Event Expiry";

    // What the story shows, for a play to write the store it shows
    private static ScreenHarness shownHarness;
    private static FloorMapEventStoreSettingsController shown;
    private static FloorMapEventStoreDoc shownDoc;

    private FloorMapEventStoreSettingsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/FloorMapEventStoreSettings", FloorMapEventStoreSettingsStories.class)
                // The store's event expiry, then its general, condense and retention settings, with
                // condense's warning; no key or value schema
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(expiry(play)).toHaveValue("2"));
                    play.expect(play.getByText("General", "label")).toBeInTheDocument();
                    play.expect(play.getByText("This will cause repeating events to disappear from the map."))
                            .toBeVisible();
                    play.expect(play.getByText("Data Retention", "label")).toBeInTheDocument();
                    play.expect(play.queryByText("Key Schema", "label")).toBeNull();
                })
                // The expiry's amount and unit are named, in a group named by its label (they had
                // no names: the group had no identity)
                .story("ExpiryIsNamed", context -> render(context, false))
                .withPlay(play -> {
                    final Play group = play.within(play.findByRole("group", EXPIRY));
                    play.expect(group.getByRole("spinbutton", "Amount")).toHaveValue("2");
                    play.expect(group.getByRole("combobox", "Unit")).toBeInTheDocument();
                })
                // A user who may only view the store can't change its expiry
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(expiry(play)).toHaveValue("2"));
                    play.expect(expiry(play)).toBeDisabled();
                })
                // A changed expiry is written, in the unit read
                .story("ExpiryIsWritten", context -> render(context, false))
                .withPlay(play -> {
                    final Query expiry = expiry(play);
                    play.waitFor(() -> play.expect(expiry).toHaveValue("2"));
                    play.click(expiry);
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(expiry).toHaveValue("3"));
                    play.run("write the store", () -> {
                        final SimpleDuration written = shown.write(shownDoc).getEventExpiry();
                        shownHarness.spy(ON_WRITE, written.getTime() + " " + written.getTimeUnit());
                    });
                    play.expect(play.spy(ON_WRITE)).toHaveBeenCalledWith("3 DAYS");
                });
    }

    private static Query expiry(final Play play) {
        // The group is named by its label, and its two controls by what each is
        return play.within(play.getByRole("group", EXPIRY)).getByRole("spinbutton", "Amount");
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.builder().build());
        harness.fn(ON_WRITE);
        final FloorMapEventStoreDoc doc = FloorMapEventStoreDoc.eventStoreBuilder()
                .uuid(UUID)
                .name("office events")
                .eventExpiry(SimpleDuration.builder().time(2).timeUnit(TimeUnit.DAYS).build())
                .build();
        final SimplePanel frame = new SimplePanel();
        frame.getElement().getStyle().setProperty("width", "600px");
        frame.getElement().getStyle().setProperty("height", "640px");
        harness.add(frame);
        harness.afterStartUp(() -> {
            // As its document's opener says, which its tabs follow
            harness.getComponentRoot().provide(Contexts.READ_ONLY, ContextValue.of(readOnly));
            final FloorMapEventStoreSettingsController controller = harness.own(
                    new FloorMapEventStoreSettingsController(harness.getEventBus(), new FloorMapEventStoreSettings()));
            frame.setWidget(controller.asWidget());
            controller.read(new DocRef(FloorMapEventStoreDoc.TYPE, UUID, "office events"), doc);
            shownHarness = harness;
            shown = controller;
            shownDoc = doc;
        });
        return harness.asWidget();
    }
}
