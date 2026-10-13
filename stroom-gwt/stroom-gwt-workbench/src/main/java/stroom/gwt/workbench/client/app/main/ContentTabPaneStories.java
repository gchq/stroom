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

import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.ContentManager;
import stroom.docref.DocRef;
import stroom.document.client.HasMultipleInstances;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

/// Stories of `App/Main/ContentTabPane`, showing Stroom's real [ContentTabPanePresenter] (the
/// document tabs) and its tab menu, built by Stroom's real `DocumentPluginEventManager` for a tab's
/// `ShowTabMenuEvent`.
///
/// The tabs hold stand-in editors (`DocumentTab`, showing the document's type and name) opened
/// through Stroom's `ContentManager`; a Dashboard tab is `HasMultipleInstances`, as Stroom's
/// `DashboardPresenter` is. 'Duplicate Tab' fires `OpenDocumentEvent` with `duplicate` set (for the
/// document's plugin to open another instance), which the `onOpenDocument` spy records. No requests
/// are made.
public final class ContentTabPaneStories {

    /// The name of the spy recording the documents asked to be opened (Stroom's `OpenDocumentEvent`).
    static final String ON_OPEN_DOCUMENT = "onOpenDocument";

    private static final DocRef DASHBOARD = new DocRef("Dashboard", "d1", "My Dashboard");
    private static final DocRef DICTIONARY = new DocRef("Dictionary", "c1", "Countries");

    private ContentTabPaneStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ContentTabPane", ContentTabPaneStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A Dashboard tab's menu offers 'Duplicate Tab', which opens a second tab for the
                // same document
                .story("DuplicateDashboardTab", context -> render(context, DASHBOARD))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(tabs(play, "My Dashboard")).toHaveLength(1));
                    // GWT's tabs have no 'tab' role, and the menu is shown on a
                    // secondary button's mouseup on a tab rather than a contextmenu event
                    play.fireEvent().mouseUp(tabs(play, "My Dashboard").nth(0), EventInit.create().button(2));
                    play.click(screen.findByText("Duplicate Tab"));
                    // Opening the second tab is the document plugin's job (Stroom's
                    // DashboardPlugin opens another instance), so the play checks that the tab pane's
                    // menu asks for a duplicate of the tab's document
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN_DOCUMENT))
                            .toHaveBeenCalledWith("Dashboard d1 duplicate=true"));
                    ContentStorySupport.expectNoProblems(play);
                })
                // A document that isn't multi-instance (a Dictionary) has no 'Duplicate Tab'
                .story("NoDuplicateForSingleInstance", context -> render(context, DICTIONARY))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(tabs(play, "Countries")).toHaveLength(1));
                    play.fireEvent().mouseUp(tabs(play, "Countries").nth(0), EventInit.create().button(2));
                    // The menu opens (Close is there) but has no Duplicate Tab
                    screen.findByText("Close");
                    play.expect(screen.queryByText("Duplicate Tab")).toBeNull();
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    // The tab bar's tabs for a document (their labels; the editor shows the name too, with its type)
    private static Query tabs(final Play play, final String name) {
        return play.getAllByText(TextMatch.startingWith(name), ".curveTab-text");
    }

    private static void open(final ContentManager contentManager,
                             final EventBus eventBus,
                             final DocRef docRef,
                             final int instance) {
        final DocumentTab tab = "Dashboard".equals(docRef.getType())
                ? new MultiInstanceDocumentTab(eventBus, docRef, instance)
                : new DocumentTab(eventBus, docRef);
        contentManager.open(event -> event.getCallback().closeTab(true), tab, tab);
    }

    private static Widget render(final StoryContext context, final DocRef docRef) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .injector(injector)
                .build();
        final EventBus eventBus = harness.getEventBus();
        // The tab pane, registered for the content events as its GWTP proxy would be
        final ContentTabPanePresenter pane = injector.getContentTabPanePresenter();
        harness.addRegistration(eventBus.addHandler(OpenContentTabEvent.getType(), pane));
        harness.addRegistration(eventBus.addHandler(CloseContentTabEvent.getType(), pane));
        harness.addRegistration(eventBus.addHandler(SelectContentTabEvent.getType(), pane));
        // The tab menu
        harness.addCleanUp(injector.getDocumentPluginEventManager()::unbind);
        final ContentManager contentManager = injector.getContentManager();
        harness.fn(ON_OPEN_DOCUMENT);
        harness.addRegistration(eventBus.addHandler(OpenDocumentEvent.getType(), event ->
                harness.spy(ON_OPEN_DOCUMENT, event.getDocRef().getType() + " " + event.getDocRef().getUuid()
                        + " duplicate=" + event.isDuplicate())));
        harness.addContent(pane);
        harness.afterStartUp(() -> open(contentManager, eventBus, docRef, 0));
        return harness.asWidget();
    }

    // --------------------------------------------------------------------------------

    /// A stand-in document editor tab.
    private static class DocumentTab extends ContentTabPresenter<View> {

        private final DocRef docRef;

        DocumentTab(final EventBus eventBus, final DocRef docRef) {
            this(eventBus, docRef, docRef.getType() + " " + docRef.getName());
        }

        DocumentTab(final EventBus eventBus, final DocRef docRef, final String text) {
            super(eventBus, new TextView(text));
            this.docRef = docRef;
        }

        @Override
        public DocRef getDocRef() {
            return docRef;
        }

        @Override
        public SvgImage getIcon() {
            return "Dashboard".equals(docRef.getType())
                    ? SvgImage.DOCUMENT_DASHBOARD
                    : SvgImage.DOCUMENT_DICTIONARY;
        }

        @Override
        public String getLabel() {
            return docRef.getName();
        }

        @Override
        public String getType() {
            return docRef.getType();
        }
    }

    // --------------------------------------------------------------------------------

    /// A stand-in editor for a document that can be open more than once, as a Dashboard can.
    private static final class MultiInstanceDocumentTab extends DocumentTab implements HasMultipleInstances {

        private int instance;

        MultiInstanceDocumentTab(final EventBus eventBus, final DocRef docRef, final int instance) {
            super(eventBus, docRef, docRef.getType() + " " + docRef.getName() + " [" + instance + "]");
            this.instance = instance;
        }

        @Override
        public String getLabel() {
            return super.getLabel() + getInstanceString();
        }

        @Override
        public int getInstance() {
            return instance;
        }

        @Override
        public void setInstance(final int instance) {
            this.instance = instance;
        }
    }

    // --------------------------------------------------------------------------------

    /// A view showing some text.
    private static final class TextView extends ViewImpl {

        private final Label label;

        TextView(final String text) {
            label = new Label(text);
        }

        @Override
        public Widget asWidget() {
            return label;
        }
    }
}
