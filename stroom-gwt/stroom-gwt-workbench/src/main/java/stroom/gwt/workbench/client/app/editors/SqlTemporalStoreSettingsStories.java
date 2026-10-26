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
import stroom.gwt.workbench.client.app.gin.editors.EditorsScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.sqlstore.client.component.SqlTemporalStoreSettingsController;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Editors/SqlTemporalStoreSettings`, showing Stroom's real
/// [SqlTemporalStoreSettingsController] (a SQL temporal store's 'Settings' tab: its entry count and
/// Reset Store) with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /sqltemporalstore/v1/count` (a sequence: 42, then 0) | the entry count |
/// | `POST /sqltemporalstore/v1/clear` | Reset Store |
public final class SqlTemporalStoreSettingsStories {

    private static final String UUID = "store-1";
    private static final String COUNT = "/sqltemporalstore/v1/count";
    private static final String CLEAR = "/sqltemporalstore/v1/clear";

    private SqlTemporalStoreSettingsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/SqlTemporalStoreSettings", SqlTemporalStoreSettingsStories.class)
                // The entry count in its form group, and Reset Store, a button
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("42")).toBeVisible());
                    play.expect(play.getByText("Entry Count")).toBeVisible();
                    play.expect(play.getByRole("button", StroomDom.button("Reset Store"))).toBeEnabled();
                })
                // Reset Store, once confirmed, clears the store and shows the count again
                .story("ResetStore", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByText("42")).toBeVisible());
                    play.click(play.getByRole("button", StroomDom.button("Reset Store")));
                    final Play confirm = screen.within(screen.findByText(
                                    TextMatch.startingWith("This will remove all entries from the store."))
                            .closest(StroomDom.DIALOG));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CLEAR).toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.getByText("0")).toBeVisible());
                })
                // A user who may only view the store can't reset it
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("42")).toBeVisible());
                    play.expect(play.getByRole("button", StroomDom.button("Reset Store"))).toBeDisabled();
                });
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final RestFixtures fixtures = RestFixtures.builder()
                .post(COUNT, RestReply.json("42"), RestReply.json("0"))
                .post(CLEAR, RestReply.json("true"))
                .build();
        final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        final DocRef docRef = new DocRef(SqlTemporalStoreDoc.TYPE, UUID, "office");
        final SqlTemporalStoreDoc doc = SqlTemporalStoreDoc.builder().uuid(UUID).name("office").build();
        final SimplePanel frame = new SimplePanel();
        frame.getElement().getStyle().setProperty("width", "500px");
        frame.getElement().getStyle().setProperty("height", "200px");
        harness.add(frame);
        harness.afterStartUp(() -> {
            // As its document's opener says, which its tabs follow
            harness.getComponentRoot().provide(Contexts.READ_ONLY, ContextValue.of(readOnly));
            final SqlTemporalStoreSettingsController controller = harness.own(
                    injector.getSqlTemporalStoreSettingsController());
            frame.setWidget(controller.asWidget());
            controller.read(docRef, doc);
        });
        return harness.asWidget();
    }
}
