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

import stroom.content.client.event.OpenContentTabEvent;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.PresenterWidget;

/// Stories matching `App/Main/docPlugin` in the React Storybook: a document plugin transforms the
/// document on its way to the server when it is saved, not as it is edited (GWT's
/// `DocPresenter.onWrite`).
///
/// The React story uses a stand-in plugin that mirrors the XML Schema plugin's `onWrite`. Here it is
/// Stroom's real XML Schema plugin and editor (`XMLSchemaPlugin`, `XMLSchemaPresenter`): the story
/// opens the document as the explorer does (`OpenDocumentEvent`, handled by Stroom's
/// `DocumentPluginEventManager`, which decorates the reference and asks the plugin to load it) and
/// shows the tab the plugin opens. Saving goes the same way (the tab's Save button fires
/// `SaveDocumentEvent`). The React `DocApi` becomes `XmlSchemaResource` routes:
/// `fetch` → `GET /xmlSchema/v1/x-1`, `update` → `PUT /xmlSchema/v1/x-1` (an echo, checked on the
/// request spy), plus the editor's schema validation (`POST /xmlSchema/v1/validate`) and the
/// explorer's `decorate` and `getFromDocRef`.
public final class DocPluginStories {

    private static final DocRef DOC_REF = new DocRef("XMLSchema", "x-1", "MySchema");

    private static final String DOC = """
            {"type": "XMLSchema", "uuid": "x-1", "name": "MySchema", "data": "original",
              "namespaceURI": "  urn:example  ", "systemId": "  keep-my-spaces  "}""";

    private static final String PATH = "/xmlSchema/v1/x-1";

    // Differs from React: Stroom's schema text is edited in an Ace editor
    private static final String ACE_CONTENT = ".ace_content";

    // The explorer's requests: the plugin decorates the reference and gets the document's node
    private static final RestFixtures FIXTURES = TreeFixtures.explorerRoutes(TreeFixtures.fixtureTree())
            .post(TreeFixtures.DECORATE, ContentStorySupport::decorate)
            .get(PATH, RestReply.json(DOC))
            .put(PATH, request -> RestReply.json(request.getBody()))
            .post("/xmlSchema/v1/validate", RestReply.json("{\"ok\": true}"))
            // The Permissions tab's users (none)
            .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json(
                    "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, "
                            + "\"exact\": true}}"))
            .build();

    private DocPluginStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/docPlugin", DocPluginStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // onWrite runs at save time: the editor keeps what was typed, and the server gets
                // the schema text and namespace URI trimmed (but not the system id)
                .story("OnWriteTransformsAtSaveTime", DocPluginStories::render)
                .withPlay(play -> {
                    // The schema opens on its Graphical tab; its text is on the Text tab
                    play.click(play.findByText("Text", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(5000, () -> play.expect(play.querySelector(ACE_CONTENT)).toBeInTheDocument());
                    // Replace the schema text with padded text: the editor keeps it as typed.
                    // Differs from React: the text is typed into the Ace editor (focused by a click
                    // on its text) rather than a text area
                    play.click(play.querySelector(ACE_CONTENT));
                    play.keyboard("{Control>}a{/Control}  <xs:schema/>  ");
                    play.waitFor(() -> play.expect(play.querySelector(ACE_CONTENT).textContent())
                            .toMatch(TextMatch.containing("<xs:schema/>")));
                    // Differs from React: GWT writes only the tabs that have been shown, and the
                    // namespace URI and system id are on the Settings tab (its onWrite trims the URI), so
                    // the Settings tab is shown before saving
                    play.click(play.getByText("Settings", StroomDom.LINK_TAB_LABEL));
                    play.findByDisplayValue("keep-my-spaces");
                    // Save: onWrite runs, so the server gets the trimmed text and namespace URI, and
                    // the system id as typed
                    play.click(play.getByTitle("Save"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(PATH)
                                    .withJsonBodyContaining("{\"data\": \"<xs:schema/>\", "
                                            + "\"namespaceURI\": \"urn:example\", "
                                            + "\"systemId\": \"  keep-my-spaces  \"}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        // Stroom's document plugin machinery: the event manager opens and saves documents with the
        // plugin registered for their type
        harness.addCleanUp(injector.getDocumentPluginEventManager()::unbind);
        harness.addCleanUp(injector.getXMLSchemaPlugin()::unbind);
        // The tab the plugin opens is shown as the story's content, as the tab pane would show it
        harness.addRegistration(harness.getEventBus().addHandler(OpenContentTabEvent.getType(), event ->
                harness.addContent((PresenterWidget<?>) event.getLayer())));
        // As the explorer does when the document is opened
        harness.afterStartUp(() -> OpenDocumentEvent.fire(harness.getHasHandlers(), DOC_REF, true));
        return harness.asWidget();
    }
}
