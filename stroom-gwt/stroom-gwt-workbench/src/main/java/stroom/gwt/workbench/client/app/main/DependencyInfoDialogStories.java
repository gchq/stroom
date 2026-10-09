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


package stroom.gwt.workbench.client.app.main;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.importexport.client.presenter.DependenciesInfoPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/DependencyInfoDialog`, showing Stroom's real [DependenciesInfoPresenter]
/// (the Dependencies screen's 'Properties' dialog).
///
/// In Stroom the Dependencies screen's action menu fires `ShowDependenciesInfoDialogEvent`, which
/// the presenter's GWTP proxy passes on to it. The story fires the event, with the presenter (from
/// GIN) registered as its handler. The dialog makes no requests.
public final class DependencyInfoDialogStories {

    private DependencyInfoDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DependencyInfoDialog", DependencyInfoDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Caption "Dependency Information", plain Type/UUID/Name
                .story("DependencyInfo", context -> render(context, new DocRef("XSLT", "x-1", "Alpha XSLT")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Dependency Information");
                    // One block, exactly GWT's three lines.
                    // GWT shows them in a read only text area (its value, not text)
                    play.expect(info(screen)).toBe("Type: XSLT\nUUID: x-1\nName: Alpha XSLT");
                    expectNoRequests(play);
                })
                // A missing dependency has no document behind it; the dialog renders from the DocRef
                .story("MissingDependencyStillRenders", context ->
                        render(context, new DocRef("Feed", "f-gone", "Missing Feed")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Dependency Information");
                    play.expect(info(screen)).toMatch(TextMatch.containing("UUID: f-gone"));
                    play.expect(info(screen)).toMatch(TextMatch.containing("Name: Missing Feed"));
                    expectNoRequests(play);
                });
    }

    // The dialog's read only text area's value
    private static Value<String> info(final Play screen) {
        return screen.querySelector("textarea.info-layout").value();
    }

    private static void expectNoRequests(final Play play) {
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalled();
        ContentStorySupport.expectNoProblems(play);
    }

    private static Widget render(final StoryContext context, final DocRef docRef) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .injector(injector)
                .withoutStartupFixtures()
                .build();
        // As the presenter's GWTP proxy would
        final DependenciesInfoPresenter presenter = injector.getDependenciesInfoPresenter();
        harness.addRegistration(harness.getEventBus()
                .addHandler(ShowDependenciesInfoDialogEvent.getType(), presenter));
        ShowDependenciesInfoDialogEvent.fire(harness.getHasHandlers(), docRef);
        return harness.asWidget();
    }
}
