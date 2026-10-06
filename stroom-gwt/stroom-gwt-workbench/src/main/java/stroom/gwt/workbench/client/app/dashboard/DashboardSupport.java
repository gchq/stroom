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


package stroom.gwt.workbench.client.app.dashboard;

import stroom.alert.client.event.AlertEvent;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.dashboard.client.main.DashboardSuperPresenter;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardResource;
import stroom.docref.DocRef;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.event.client.StaticEventBus;
import stroom.gwt.workbench.client.app.gin.dashboard.DashboardScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRenderer;
import stroom.security.shared.DocumentPermission;
import stroom.widget.help.client.presenter.HelpManager;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// What the dashboard batch's stories share: opening a dashboard in Stroom's real editor
/// (`DashboardSuperPresenter`, as `DashboardPlugin` does), its fixtures and the play helpers.
///
/// A story's dashboard is the JSON of a `DashboardDoc`, which [#docRoutes] serves from
/// `GET /dashboard/v1/{uuid}` (and echoes on `PUT`, the toolbar's Save), so the dashboard is read
/// from JSON exactly as Stroom reads a fetched one.
public final class DashboardSupport {

    /// The title of React's dashboard editor stories.
    public static final String TITLE = "App/Editors/DashboardEditor";

    /// Every `POST /dashboard/v1/search/{node}` request (a dashboard `SearchModel`'s poll).
    public static final RequestMatcher SEARCH = RequestMatcher.post(
            "/dashboard/v1/search/" + RequestMatcher.PATH_WILDCARD);

    /// The user's preferences with Stroom's dark theme, as React's stories show (the editors'
    /// Ace themes follow the preferences, e.g. `tomorrow_night`).
    public static final String DARK_PREFERENCES = StartupFixtures.DEFAULT_USER_PREFERENCES
            .replace("\"Light\"", "\"Dark\"")
            .replace("\"chrome\"", "\"tomorrow_night\"");

    /// The rows `alpha` and `beta` of React's table results.
    public static final String ALPHA_BETA_ROWS = "[{\"values\": [\"alpha\"], \"depth\": 0}, "
                                                 + "{\"values\": [\"beta\"], \"depth\": 0}]";

    // React's REFERENCED_QUERY
    private static final String REFERENCED_QUERY = "{\"uuid\": \"q-ref\", \"type\": \"Query\", "
                                                   + "\"name\": \"Referenced Query\", "
                                                   + "\"query\": \"from index\\nselect name\\n\"}";

    // See fixtures(...)
    private static final RestFixtures DEFAULTS = RestFixtures.builder()
            .route(SEARCH, RestReply.json(alphaBetaResponse("t1")))
            .route(QueryFixtures.SEARCH, RestReply.json(alphaBetaResponse("table")))
            .get("/query/v1/q-ref", RestReply.json(REFERENCED_QUERY))
            .post("/dataSource/v1/fetchDefaultExtractionPipeline", RestReply.noContent())
            .build();

    private DashboardSupport() {
        // Static utility
    }

    /// The fixtures of a dashboard story: the story's own routes, then the dashboard's
    /// ([#docRoutes]) and the defaults every dashboard story shares (added with `addAll`, so that a
    /// story's own route for the same request wins):
    ///
    /// | Request | Default reply |
    /// |---|---|
    /// | A dashboard search ([#SEARCH]) | React's `dashboardApiFixture('t1')`: `t1`'s rows `alpha` and `beta` |
    /// | A StroomQL search (`QueryFixtures.SEARCH`, an Embedded Query) | React's `TABLE_RESPONSE`: `alpha`, `beta` |
    /// | `GET /query/v1/q-ref` | React's `REFERENCED_QUERY` |
    /// | `POST /dataSource/v1/fetchDefaultExtractionPipeline` | none |
    /// | The result store's `destroy`/`terminate`, the StroomQL editor's requests | [QueryFixtures#editorRoutes] |
    /// | The Permissions tab | the user owns the dashboard |
    ///
    /// @param docJson The JSON of the `DashboardDoc`.
    /// @param routes  Adds the story's own routes, or does nothing.
    /// @return The fixtures' builder, e.g. to make them lenient.
    public static RestFixtures.Builder fixtures(final String docJson, final Consumer<RestFixtures.Builder> routes) {
        final RestFixtures.Builder builder = RestFixtures.builder();
        routes.accept(builder);
        final String uuid = String.valueOf(((Map<?, ?>) JsonValues.parse(docJson)).get("uuid"));
        builder.get("/dashboard/v1/" + uuid, RestReply.json(docJson))
                .put("/dashboard/v1/" + uuid, request -> RestReply.json(request.getBody()))
                .addAll(DEFAULTS);
        return DocumentEditors.ownerPermissions(QueryFixtures.editorRoutes(builder));
    }

    /// A story's renderer for a dashboard with the default options.
    ///
    /// @param docJson The JSON of the `DashboardDoc`.
    /// @param routes  Adds the story's own routes, or does nothing.
    /// @return The renderer.
    public static StoryRenderer story(final String docJson, final Consumer<RestFixtures.Builder> routes) {
        return story(docJson, routes, options -> {
        });
    }

    /// A story's renderer for a dashboard.
    ///
    /// @param docJson The JSON of the `DashboardDoc`.
    /// @param routes  Adds the story's own routes, or does nothing.
    /// @param options The story's options, see [Options].
    /// @return The renderer.
    public static StoryRenderer story(final String docJson,
                                      final Consumer<RestFixtures.Builder> routes,
                                      final Consumer<Options> options) {
        final Map<?, ?> doc = (Map<?, ?>) JsonValues.parse(docJson);
        final RestFixtures fixtures = fixtures(docJson, routes).build();
        return context -> render(context,
                fixtures,
                String.valueOf(doc.get("uuid")),
                String.valueOf(doc.get("name")),
                options);
    }

    /// A dashboard search's reply (`DashboardSearchResponse`, complete) with a table result.
    ///
    /// @param componentId The table component's id.
    /// @param fields      The JSON array of the table's columns.
    /// @param rows        The JSON array of its rows.
    /// @param totalRows   The total number of rows.
    /// @return The reply's JSON.
    public static String tableResponse(final String componentId,
                                       final String fields,
                                       final String rows,
                                       final int totalRows) {
        return QueryFixtures.response(true, QueryFixtures.tableResult(componentId, fields, rows, totalRows));
    }

    /// The rows `alpha` and `beta` of React's table results, with their `Name` column.
    ///
    /// @param componentId The table component's id.
    /// @return The reply's JSON.
    public static String alphaBetaResponse(final String componentId) {
        return tableResponse(componentId, "[" + DashboardDocs.NAME_FIELD + "]", ALPHA_BETA_ROWS, 2);
    }

    /// Renders a dashboard story: creates the batch's injector and a harness (with Stroom's real
    /// alerts and the dark theme's preferences), gives the user OWNER permission on every document,
    /// registers the dashboard's component types and, once Stroom has started, opens the dashboard.
    ///
    /// @param context  The story's context.
    /// @param fixtures The story's fixtures, including the dashboard's ([#docRoutes]).
    /// @param uuid     The dashboard's UUID.
    /// @param name     The dashboard's name.
    /// @param options  Other options: the harness's builder, link parameters and what to do once
    ///                 the dashboard is open.
    /// @return The story's widget.
    public static Widget render(final StoryContext context,
                                final RestFixtures fixtures,
                                final String uuid,
                                final String name,
                                final Consumer<Options> options) {
        final Options opts = new Options();
        options.accept(opts);
        final DashboardScreenGinjector injector = GWT.create(DashboardScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .startup(startup -> startup.userPreferences(DARK_PREFERENCES));
        opts.harnessOptions.accept(builder);
        final ScreenHarness harness = builder.build();
        harness.getSecurityContext().setDocumentPermission(DocumentPermission.OWNER);
        opts.setup.accept(harness, injector);
        // As Stroom's app does at start-up (eager singletons): form help buttons fire their
        // events on the static event bus, which the help manager shows, and the component plugins
        // register the component types
        new StaticEventBus(harness.getEventBus());
        new HelpManager(harness.getEventBus());
        registerComponentTypes(injector);
        harness.afterStartUp(() -> {
            final DashboardSuperPresenter presenter = injector.getDashboardSuperPresenter();
            if (opts.linkParams != null) {
                // As DashboardPlugin.openParameterisedDashboard does for a link's params
                presenter.setParamsFromLink(opts.linkParams);
            }
            open(harness, new DocRef(DashboardDoc.TYPE, uuid, name), presenter, opts.afterOpen);
        });
        return harness.asWidget();
    }

    /// Creates every component plugin of the injector, so that each registers its component type
    /// in the dashboard's `ComponentRegistry`, as Stroom's eager singletons do.
    ///
    /// @param injector The story's injector.
    public static void registerComponentTypes(final DashboardScreenGinjector injector) {
        injector.getQueryPlugin();
        injector.getTablePlugin();
        injector.getVisPlugin();
        injector.getTextPlugin();
        injector.getEmbeddedQueryPlugin();
        injector.getKeyValueInputPlugin();
        injector.getListInputPlugin();
        injector.getTextInputPlugin();
        injector.getTableFilterPlugin();
    }

    // As DashboardPlugin (DocumentPlugin) does: fetch, check EDIT, read, show the tab, select it
    private static void open(final ScreenHarness harness,
                             final DocRef docRef,
                             final DashboardSuperPresenter presenter,
                             final Consumer<DashboardSuperPresenter> afterOpen) {
        final DashboardResource resource = GWT.create(DashboardResource.class);
        harness.addRegistration(harness.getEventBus().addHandler(SaveDocumentEvent.getType(), event -> {
            if (event.getTabData() == presenter) {
                save(harness, docRef, presenter, resource);
            }
        }));
        harness.getRestFactory()
                .create(resource)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(doc -> {
                    presenter.getDefaultTab().ifPresent(presenter::selectTab);
                    harness.getSecurityContext().hasDocumentPermission(
                            docRef,
                            DocumentPermission.EDIT,
                            allowUpdate -> {
                                presenter.read(docRef, doc, !allowUpdate);
                                harness.addContent(presenter);
                                // As Stroom's content pane does when it selects the tab
                                ContentTabSelectionChangeEvent.fire(harness.getHasHandlers(), presenter);
                                afterOpen.accept(presenter);
                            },
                            error -> AlertEvent.fireErrorFromException(harness.getHasHandlers(), error, null),
                            presenter);
                })
                .onFailure(error -> AlertEvent.fireError(harness.getHasHandlers(),
                        "Unable to load document " + docRef,
                        error.getMessage(),
                        null))
                .taskMonitorFactory(presenter)
                .exec();
    }

    // As DocumentPlugin.save
    private static void save(final ScreenHarness harness,
                             final DocRef docRef,
                             final DashboardSuperPresenter presenter,
                             final DashboardResource resource) {
        if (presenter.isDirty()) {
            final DashboardDoc document = presenter.write(presenter.getEntity());
            if (document != null) {
                harness.getRestFactory()
                        .create(resource)
                        .method(res -> res.update(document.getUuid(), document))
                        .onSuccess(saved -> presenter.read(docRef, saved, presenter.isReadOnly()))
                        .onFailure(error -> AlertEvent.fireError(harness.getHasHandlers(),
                                "Unable to save document " + docRef,
                                error.getMessage(),
                                null))
                        .taskMonitorFactory(presenter)
                        .exec();
            }
        }
    }

    /// Checks that the story had no alerts and made no request without a fixture.
    ///
    /// @param play The play.
    public static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    // --------------------------------------------------------------------------------


    /// The options of [#render].
    public static final class Options {

        private Consumer<ScreenHarness.Builder> harnessOptions = builder -> {
        };
        private String linkParams;
        private Consumer<DashboardSuperPresenter> afterOpen = presenter -> {
        };
        private BiConsumer<ScreenHarness, DashboardScreenGinjector> setup = (harness, injector) -> {
        };

        private Options() {
        }

        /// @param harnessOptions Changes the harness's options, e.g. its app permissions.
        /// @return These options.
        public Options harness(final Consumer<ScreenHarness.Builder> harnessOptions) {
            this.harnessOptions = harnessOptions;
            return this;
        }

        /// @param linkParams The parameters of the link that opened the dashboard (React's
        ///                   `linkParams`), e.g. `env=prod other=x`.
        /// @return These options.
        public Options linkParams(final String linkParams) {
            this.linkParams = linkParams;
            return this;
        }

        /// @param setup What to do once the harness is built, before the dashboard opens, e.g. handle
        ///              the app's events as Stroom's app would.
        /// @return These options.
        public Options setup(final BiConsumer<ScreenHarness, DashboardScreenGinjector> setup) {
            this.setup = setup;
            return this;
        }

        /// @param afterOpen What to do once the dashboard is open, e.g. handle its events.
        /// @return These options.
        public Options afterOpen(final Consumer<DashboardSuperPresenter> afterOpen) {
            this.afterOpen = afterOpen;
            return this;
        }
    }
}
