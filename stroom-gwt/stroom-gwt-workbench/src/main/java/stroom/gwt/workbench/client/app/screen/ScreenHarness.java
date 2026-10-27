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


package stroom.gwt.workbench.client.app.screen;

import stroom.alert.client.AlertPlugin;
import stroom.alert.client.component.CommonAlert;
import stroom.alert.client.component.CommonAlertController;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.event.AskStroomAiTableRequest;
import stroom.dispatch.client.QuietTaskMonitorFactory;
import stroom.dispatch.client.RestFactory;
import stroom.event.client.StaticEventBus;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.client.app.rest.FixtureDispatcher;
import stroom.gwt.workbench.client.app.rest.FixtureUploads;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RecordedUpload;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.rest.TimerReplyScheduler;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.hyperlink.client.OpenHyperlinkRequest;
import stroom.preferences.client.DateTimeFormatter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.security.shared.AppPermission;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.AppRoot;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.menu.client.presenter.Menu;
import stroom.widget.menu.client.presenter.MenuController;
import stroom.widget.menu.client.presenter.MenuManager;
import stroom.widget.menu.client.presenter.MenuRequest;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupManager;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.ContentFocus;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import org.fusesource.restygwt.client.Defaults;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// Everything a real Stroom screen (presenter and view) needs to run in a story with fake data:
///
/// * a [ScreenGinjector] with an event bus, Stroom's real `RestFactory` and the services screens
///   commonly need ([UiConfigCache], [DateTimeFormatter], a configurable
///   [StorySecurityContext]...);
/// * a [FixtureDispatcher] that answers the screen's REST requests from the story's
///   [RestFixtures], followed by the [StartupFixtures], so no server is needed;
/// * a [StoryUploadTransport] that answers the screen's file uploads (Stroom's `FileChooser`)
///   from the fixtures' upload replies;
/// * a [PopupManager], so that dialogs shown with `ShowPopupEvent` appear, on the page's body
///   as in Stroom, where play functions find them with `play.screen()`, and Stroom's [Menu], so
///   that menus shown with `ShowMenuEvent` (e.g. a grid's 'Actions...' cell) appear too;
/// * spies on the requests made, unhandled requests, alerts, confirmations, downloads and
///   uploads, which play functions check with `play.expect(play.spy(name))`.
///
/// The story creates its controller or presenter with `new`, passing it what the harness provides,
/// then returns [#asWidget()], e.g.
/// ```
/// final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
/// new AboutController(harness.getEventBus(), new About(), harness.getRestFactory(),
///         harness.getUiConfigCache(), harness.getDateTimeFormatter()).show(harness.getAppRoot());
/// return harness.asWidget();
/// ```
///
/// Everything a harness creates is its own (its injector's `RestFactory` sends requests with its
/// own dispatcher), so a rendering may have more than one, e.g. for two screens with different
/// fixtures or users; each answers its own screen's requests, and they record into the
/// rendering's spies of the same names.
///
/// When the story renders again (rerun, rewind, args changed, another story) the harness is
/// disposed through [StoryContext#addCleanUp(Runnable)], which it registers first, so even a
/// harness whose construction failed is undone: its dispatcher cancels the pending replies and
/// drops any later request (an old presenter keeps its injector's `RestFactory`, so its timers and
/// chained requests can only reach this dispatcher), its upload transport does the same for
/// uploads and restores Stroom's default transport, Stroom's `UiConfigCache` stops refreshing,
/// the [StoryLocationManager] removes its window-closing handler, the popups it opened are hidden
/// through Stroom's own `HidePopupEvent` (which unbinds their presenters), the timers and handlers
/// registered with [#addTimer(Timer)] and [#addRegistration(HandlerRegistration)] are stopped, the
/// story's root, which owns what the story shows ([#show(Controller)], [#own(HasControllerNode)]), is
/// disposed, and the event bus is switched off.
public final class ScreenHarness {

    /// The name of the spy that records each REST request, as `METHOD /path?query body`.
    public static final String REQUEST_SPY = "request";
    /// The name of the spy that records each request that no fixture matched.
    public static final String UNHANDLED_REQUEST_SPY = "unhandledRequest";
    /// The name of the spy that records each alert (error/warning/info message) the screen shows,
    /// as `LEVEL: message`.
    public static final String ALERT_SPY = "alert";
    /// The name of the spy that records each confirmation the screen asks for, as
    /// `LEVEL: message`.
    public static final String CONFIRM_SPY = "confirm";
    /// The name of the spy that records each download, as the URL relative to the host page,
    /// e.g. `resourcestore/my-notes.md?uuid=k1`.
    public static final String DOWNLOAD_SPY = "download";
    /// The name of the spy that records each file upload with three arguments: the URL relative to
    /// the host page (`importfile.rpc` for all of Stroom's uploads), the chosen file's name and
    /// its content (null unless it is text of at most 64 KiB).
    public static final String UPLOAD_SPY = "upload";

    private static final String ERROR_STYLE = "screen-harness-error";
    private static final String CONTENT_STYLE = "screen-harness-content";
    // The harness of the story shown, for plays (a page shows one story at a time)
    private static ScreenHarness shown;

    // The fields set in the constructor are null in a harness whose construction failed part way,
    // which dispose() copes with
    private final StoryContext context;
    private final FixtureDispatcher dispatcher;
    private final StoryUploadTransport uploadTransport;
    private final ScreenGinjector injector;
    private final StoryEventBus eventBus;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final StoryLocationManager locationManager;
    private final PopupTracker<MyPresenterWidget<?>> popups = new PopupTracker<>();
    private final List<Runnable> cleanUps = new ArrayList<>();
    private final List<MyPresenterWidget<?>> popupsToClose = new ArrayList<>();
    private final Map<String, Spy> spies = new HashMap<>();
    private final FlowPanel host = new FlowPanel();
    private boolean disposed;
    // Whether the leak check's probe is wanted (see LeakProbe)
    private final boolean probe;
    // The owner of the dialogs of what the story makes itself (see getAppRoot)
    private final AppRoot appRoot = new AppRoot();
    // Owns what the story shows (see own), made when first needed
    private ControllerNode componentRoot;
    private Controller shownComponent;
    private MyPresenterWidget<?> shownContent;
    // The number of clean ups registered before the screen was opened, so that the probe's close
    // runs those its opening registered (e.g. a story's save handler)
    private int probeCleanUpMark;
    // The number of widgets shown before the screen was opened, so that the probe's close removes
    // only those its opening added
    private int probeWidgetMark;
    // The number of popups to close on clean up before the screen was opened, so that the probe's
    // close closes, and forgets, those its opening registered
    private int probePopupsToCloseMark;

    private ScreenHarness(final Builder builder) {
        this.context = builder.context;
        // Register the clean up first, so that if anything below fails the partly built harness
        // is still undone
        context.addCleanUp(this::dispose);
        probe = LeakProbe.isRequested();

        // Spies must be registered as the story renders, for plays to check they weren't called
        for (final String name : new String[]{
                REQUEST_SPY, UNHANDLED_REQUEST_SPY, ALERT_SPY, CONFIRM_SPY, DOWNLOAD_SPY, UPLOAD_SPY}) {
            fn(name);
        }
        final RestFixtures fixtures = builder.buildFixtures();
        dispatcher = new FixtureDispatcher(fixtures, Defaults::getServiceRoot,
                new TimerReplyScheduler(), new HarnessListener());
        // FileChooserController's transport is static: this harness answers uploads until it is
        // disposed (or another harness is created)
        uploadTransport = new StoryUploadTransport(
                new FixtureUploads(fixtures, new TimerReplyScheduler(), new HarnessUploadListener()));
        uploadTransport.install();

        injector = builder.injector != null
                ? builder.injector
                : GWT.create(ScreenGinjector.class);
        if (injector.getStoryDispatcher().hasDelegate()) {
            // Its singletons (REST factory, security context...) belong to another harness
            throw new IllegalStateException("The injector has already been used by another screen harness; "
                    + "create a new one with GWT.create(...) each time the story renders");
        }
        injector.getStoryDispatcher().setDelegate(dispatcher);
        eventBus = injector.getStoryEventBus();
        restFactory = injector.getRestFactory();
        // Created now, as it starts a refresh timer that must be stopped when disposed
        uiConfigCache = injector.getUiConfigCache();
        locationManager = injector.getLocationManager();
        locationManager.setDownloadListener(url -> spy(DOWNLOAD_SPY, url));

        injector.getSecurityContext()
                .setUser(builder.startup.getUser())
                .setAppPermissions(builder.startup.getAppPermissions());

        // Track the popups so they can be hidden through Stroom's own path when disposed
        eventBus.addHandler(ShowPopupEvent.getType(), event -> popups.onShow(event.getPresenterWidget()));
        eventBus.addHandler(HidePopupEvent.getType(), event -> popups.onHide(event.getPresenterWidget()));
        new PopupManager(eventBus);
        // Widgets with no event bus of their own show their dialogs on Stroom's static one, as in
        // Stroom
        new StaticEventBus(eventBus);
        // Show menus (ShowMenuEvent, e.g. from a grid's ActionMenuCell) as Stroom's MenuManager does
        new MenuManager(eventBus, this::createMenuController, appRoot);
        // and the edit menu on text fields, in place of the browser's
        addCleanUp(ContextMenuManager.install(eventBus::fireEvent));
        // and the menus that widgets with no event bus ask for (MenuRequest), as Stroom's
        // CorePresenter relays them
        addCleanUp(MenuRequest.relay(event -> eventBus.fireEvent(event))::removeHandler);
        // and the links and Stroom AI questions they ask for (a data grid's context menu's)
        addCleanUp(OpenHyperlinkRequest.relay(event -> eventBus.fireEvent(event))::removeHandler);
        addCleanUp(AskStroomAiTableRequest.relay(event -> eventBus.fireEvent(event))::removeHandler);

        // Record alerts and confirmations, and optionally show them as Stroom does
        eventBus.addHandler(AlertEvent.getType(), event -> spy(ALERT_SPY, describe(event)));
        eventBus.addHandler(ConfirmEvent.getType(), event -> spy(CONFIRM_SPY, describe(event)));
        if (builder.realAlerts) {
            final AlertPlugin alertPlugin = new AlertPlugin(eventBus,
                    () -> new CommonAlertController(eventBus, new CommonAlert()),
                    getAppRoot());
        }
    }

    // As Stroom's GIN module creates them for its Menu
    private MenuController createMenuController() {
        return new MenuController(eventBus, new Menu(), this::createMenuController);
    }

    /// Creates the harness for a story, with the default start-up fixtures and security context
    /// (the `admin` user). Create it each time the story renders.
    ///
    /// @param context  The story's context, to make spies and register the harness's clean up.
    /// @param fixtures The replies to the screen's REST requests.
    /// @return The harness.
    public static ScreenHarness create(final StoryContext context, final RestFixtures fixtures) {
        return builder(context, fixtures).build();
    }

    /// @param context  The story's context, to make spies and register the harness's clean up.
    /// @param fixtures The replies to the screen's REST requests.
    /// @return A builder for a harness with options, e.g. the user and their permissions.
    public static Builder builder(final StoryContext context, final RestFixtures fixtures) {
        return new Builder(context, fixtures);
    }

    /// @return The injector, for the services that screens need.
    public ScreenGinjector getInjector() {
        return injector;
    }

    /// @return The event bus to give the screen's presenters.
    public EventBus getEventBus() {
        return eventBus;
    }

    /// @return The event bus as [HasHandlers], e.g. for firing Stroom's `XxxEvent.fire(...)`.
    public HasHandlers getHasHandlers() {
        return eventBus::fireEvent;
    }

    /// @return Stroom's real REST factory, whose requests are answered by the fixtures.
    public RestFactory getRestFactory() {
        return restFactory;
    }

    /// @return The security context, to configure before creating the screen.
    public StorySecurityContext getSecurityContext() {
        return injector.getSecurityContext();
    }

    /// @return The injector's UI config cache, which fetches the UI config from the fixtures.
    public UiConfigCache getUiConfigCache() {
        return uiConfigCache;
    }

    /// @return The injector's date/time formatter.
    public DateTimeFormatter getDateTimeFormatter() {
        return injector.getDateTimeFormatter();
    }

    /// @return The injector's user preferences manager.
    public UserPreferencesManager getUserPreferencesManager() {
        return injector.getUserPreferencesManager();
    }

    /// Gets a spy of this rendering, e.g. to record the events a screen fires. Call it as the
    /// story renders (so that a play can check the spy was *not* called), then call the spy, or
    /// [#spy(String, String)], when the screen acts.
    ///
    /// @param name The name of the spy, e.g. `onOpenDoc`.
    /// @return The spy, the same one each time for a name.
    public Spy fn(final String name) {
        return spies.computeIfAbsent(name, context::fn);
    }

    /// Records a call of a spy, which is also shown in the Actions addon, e.g. when the screen
    /// fires an event that a play checks. Calls after the harness is disposed are ignored, so an
    /// old rendering can't add to the new rendering's spies.
    ///
    /// @param name   The name of the spy, e.g. `onOpenDoc`, which should have been got with
    ///               [#fn(String)] as the story rendered.
    /// @param detail Detail of the call, or null.
    public void spy(final String name, final String detail) {
        if (!disposed) {
            fn(name).call(detail);
        }
    }

    /// Adds a widget to the story, e.g. a screen that is not shown in a dialog.
    ///
    /// @param widget The widget.
    public void add(final Widget widget) {
        host.add(widget);
    }

    /// Runs an action (e.g. creating the screen) once the harness has done what Stroom does at
    /// start-up, before it shows any screen: loaded the UI config into its [UiConfigCache] and the
    /// user's preferences into its `CurrentPreferences` (`UserPreferencesManager.setCurrentPreferences`,
    /// keeping the workbench page's own theme classes, which Stroom would replace with the user's).
    /// Screens rely on both:
    ///
    /// * some read the cached config at once, rather than with `UiConfigCache.get(consumer)`, which
    ///   fetches it first;
    /// * editors (`EditorPresenter`) read the editor preferences, which are null until set.
    ///
    /// The action isn't run if the story has rendered again meanwhile; a failure in it is reported
    /// as the story's error, as any other.
    ///
    /// @param action What to do, e.g. create the screen and show it with [#show].
    public void afterStartUp(final Runnable action) {
        Objects.requireNonNull(action);
        final QuietTaskMonitorFactory taskMonitorFactory = new QuietTaskMonitorFactory();
        uiConfigCache.refresh(config -> {
            if (!disposed) {
                getUserPreferencesManager().fetch(preferences -> {
                    if (!disposed) {
                        applyUserPreferences(preferences);
                        if (probe) {
                            // For the leak check: opens the screen, and lets the page close it, as
                            // closing its tab does, and open it again. What it shows belongs to the
                            // story's root, which its clean ups dispose
                            final Runnable open = () -> {
                                probeCleanUpMark = cleanUps.size();
                                probeWidgetMark = host.getWidgetCount();
                                probePopupsToCloseMark = popupsToClose.size();
                                action.run();
                            };
                            open.run();
                            LeakProbe.install(this, open);
                        } else {
                            action.run();
                        }
                    }
                }, taskMonitorFactory);
            }
        }, taskMonitorFactory);
    }

    // As Stroom does when the user logs in, but keeping the page's classes and giving Stroom the
    // workbench's theme. The editor theme is left out, so that Ace follows the theme (as Stroom's
    // default editor theme does), rather than e.g. the fixture's light 'stroom_light'.
    private void applyUserPreferences(final UserPreferences preferences) {
        final Element html = Document.get().getDocumentElement();
        final String pageClasses = html.getClassName();
        getUserPreferencesManager().setCurrentPreferences(preferences.copy()
                .theme(StroomThemeDecorator.getStroomThemeName())
                .editorTheme(null)
                .build());
        html.setClassName(pageClasses);
    }

    /// Shows a screen that Stroom shows in a tab (e.g. Jobs, Nodes, a document editor), filling
    /// the story's canvas, the full height of the page, as Stroom's content pane gives a tab's
    /// content all its space. The story's root ([#getComponentRoot()]) owns it, as [#own] does, so
    /// it is disposed, with everything it owns, when the story renders again or the leak probe
    /// closes it.
    ///
    /// @param screen The screen, a component or a presenter.
    /// @param <C>    The screen's type.
    /// @return The screen.
    public <C extends Controller> C show(final C screen) {
        return fill(screen, true);
    }

    /// Shows a component that needs no owner (one with no controller, e.g. the pipeline's tree) as
    /// [#show] shows a screen, filling the story's canvas.
    ///
    /// @param component The component.
    /// @param <W>       The component's type.
    /// @return The component.
    public <W extends Widget> W showWidget(final W component) {
        fillWidget(component, component, true);
        shown = this;
        return component;
    }

    /// Shows a page that Stroom shows on its own, not in a tab (e.g. the sign in page), filling the
    /// story's canvas and owned as [#show] does. The page decides where its focus goes itself, so
    /// the test runner's focus check, which focuses a screen as the tab pane does, doesn't apply.
    ///
    /// @param page The page, a component or a presenter.
    /// @param <C>  The page's type.
    /// @return The page.
    public <C extends Controller> C showPage(final C page) {
        return fill(page, false);
    }

    /// For a play: the screen the story shows (the last given to [#show] or [#showPage]), e.g. to
    /// act on it as its owner does.
    ///
    /// @param <C> The component's type.
    /// @return The component.
    /// @throws IllegalStateException If the story shows none.
    @SuppressWarnings("unchecked")
    public static <C extends Controller> C shownComponent() {
        if (shown == null || shown.shownComponent == null) {
            throw new IllegalStateException("The story shows no component");
        }
        return (C) shown.shownComponent;
    }

    /// For a play: the presenter the story shows (the last given to [#show] or [#showPage]), e.g.
    /// to act on it as Stroom does (reading a document again when it is locked).
    ///
    /// @param <P> The presenter's type.
    /// @return The presenter.
    /// @throws IllegalStateException If the story shows none.
    @SuppressWarnings("unchecked")
    public static <P extends MyPresenterWidget<?>> P shownContent() {
        if (shown == null || shown.shownContent == null) {
            throw new IllegalStateException("The story shows no presenter");
        }
        return (P) shown.shownContent;
    }

    /// For a play: closes the story's components as closing a document's tab does, disposing the
    /// story's root and so everything it owns. Their widgets stay on the page.
    ///
    /// @throws IllegalStateException If the story shows none.
    public static void closeShownComponents() {
        if (shown == null || shown.componentRoot == null) {
            throw new IllegalStateException("The story shows no component");
        }
        shown.componentRoot.dispose();
    }

    /// The owner of the dialogs of what the story makes itself rather than through an injector
    /// (e.g. Stroom's `Menu` or `CurrentUser`), as the injected `AppRoot` is in Stroom. It lasts as
    /// long as the harness, as Stroom's does the page: its dialogs are disposed when hidden, and
    /// the story's clean ups hide them.
    ///
    /// @return The app root.
    public AppRoot getAppRoot() {
        return appRoot;
    }

    /// The story's root in the ownership tree, as an open document's tab is in Stroom: it owns the
    /// components the story shows and provides them with a task monitor, as Stroom's tab does, which
    /// fires task events on the harness's bus. It is disposed with the story's clean ups.
    ///
    /// @return The root.
    public ControllerNode getComponentRoot() {
        if (componentRoot == null || componentRoot.isDisposed()) {
            final ControllerNode root = ControllerNode.root("story");
            root.provide(Contexts.TASK_MONITOR, ContextValue.of(new DefaultTaskMonitorFactory(getHasHandlers())));
            componentRoot = root;
            addCleanUp(root::dispose);
        }
        return componentRoot;
    }

    // Shows a screen filling the canvas; a tab's screen is exposed to the focus check
    private <C extends Controller> C fill(final C screen, final boolean tab) {
        own(screen);
        fillWidget(screen.asWidget(), screen, tab);
        shownComponent = screen;
        if (screen instanceof final MyPresenterWidget<?> presenter) {
            shownContent = presenter;
        }
        shown = this;
        return screen;
    }

    private void fillWidget(final Widget widget, final Object focusContent, final boolean tab) {
        final SimplePanel content = new SimplePanel();
        content.setStyleName(CONTENT_STYLE);
        content.getElement().getStyle().setProperty("position", "relative");
        content.getElement().getStyle().setProperty("width", "100%");
        // The page's height within the story's padding, so that the page doesn't scroll and the
        // bottom of the screen shows
        content.getElement().getStyle().setProperty("height", StroomThemeDecorator.CANVAS_HEIGHT);
        widget.getElement().getStyle().setProperty("position", "absolute");
        widget.getElement().getStyle().setProperty("inset", "0");
        content.setWidget(widget);
        add(content);
        if (tab) {
            // For the test runner's focus check (test-runner/lib/focus-check.mjs): focuses the
            // screen as Stroom's tab pane does when it opens it
            exposeContentFocus(focusContent, content.getElement());
        }
    }

    /// Exposes `window.__workbenchContentFocus()`, which focuses the screen as Stroom's tab pane does
    /// when it opens it ([ContentFocus]) and reports where the focus went: `{inside, tabStop, name}`,
    /// whether it is in the screen, on one of its tab stops (rather than the screen itself, which
    /// takes the focus when it has none), and the name of what has it.
    private static native void exposeContentFocus(Object presenter, Element content) /*-{
        $wnd.__workbenchContentFocus = function () {
            @stroom.widget.util.client.ContentFocus::focus(Ljava/lang/Object;)(presenter);
            var active = $doc.activeElement;
            var inside = !!active && active !== $doc.body && content.contains(active);
            var firstTabStop = @stroom.widget.util.client.ContentFocus::firstTabStop(*)(content);
            return {
                inside: inside,
                tabStop: inside && firstTabStop !== null,
                name: active ? (active.getAttribute('title') || active.getAttribute('aria-label')
                    || active.getAttribute('placeholder') || active.textContent.trim().substring(0, 40)
                    || active.localName) : null
            };
        };
    }-*/;

    /// For the leak check's probe (see [LeakProbe]): closes the screen as closing a Stroom tab does,
    /// hiding the popups and removing the widgets its opening added and, if asked, disposing the
    /// presenters made while opening it (as `DocumentPlugin` does) and running the clean ups its
    /// opening registered.
    ///
    /// @param dispose Whether to dispose the screen's presenters and run its clean ups.
    void probeClose(final boolean dispose) {
        // Popups that must be asked to close are, as their Close buttons would, and forgotten, else
        // the list would keep each opening's popup
        for (int i = popupsToClose.size() - 1; i >= probePopupsToCloseMark; i--) {
            final MyPresenterWidget<?> popup = popupsToClose.remove(i);
            runSafely("closing a popup", () -> HidePopupRequestEvent.builder(popup)
                    .action(DialogAction.CLOSE)
                    .fire());
        }
        // Popups (dialogs) are hidden as Stroom hides them, which unbinds their presenters
        for (final MyPresenterWidget<?> popup : List.copyOf(popups.getOpenPopups())) {
            HidePopupEvent.builder(popup).autoClose(true).ok(false).fire();
        }
        for (int i = host.getWidgetCount() - 1; i >= probeWidgetMark; i--) {
            host.remove(i);
        }
        if (dispose) {
            // The clean ups the opening registered, as rendering the story again would run them,
            // among them disposing the story's root and what it owns
            for (int i = cleanUps.size() - 1; i >= probeCleanUpMark; i--) {
                runSafely("running a clean up", cleanUps.remove(i));
            }
        }
    }

    /// @return For the leak check's probe, the number of widgets the screen's opening has shown,
    /// and of popups shown.
    int probeShownCount() {
        return host.getWidgetCount() - probeWidgetMark + popups.getOpenPopups().size();
    }

    /// @return For the leak check's probe, the number of REST requests not yet answered.
    int probePendingCount() {
        return dispatcher.getPendingCount();
    }

    /// @return The story's widget, which holds any widgets added. Dialogs are shown on the
    /// page's body, not in this.
    public Widget asWidget() {
        return host;
    }

    /// Registers something to undo when the story renders again, after the harness has stopped
    /// the REST requests and hidden its popups. Clean ups run in the reverse order they were added.
    ///
    /// @param cleanUp What to do.
    public void addCleanUp(final Runnable cleanUp) {
        cleanUps.add(Objects.requireNonNull(cleanUp));
    }

    /// Cancels a timer when the story renders again, e.g. one the story starts, or a screen's
    /// polling timer that the story can reach.
    ///
    /// @param timer The timer.
    /// @return The timer.
    public Timer addTimer(final Timer timer) {
        addCleanUp(timer::cancel);
        return timer;
    }

    /// Removes a handler when the story renders again, e.g. one the story adds to a widget or to
    /// a bus other than the harness's.
    ///
    /// @param registration The handler's registration.
    /// @return The registration.
    public HandlerRegistration addRegistration(final HandlerRegistration registration) {
        addCleanUp(registration::removeHandler);
        return registration;
    }

    /// Has the story's root own something the story makes and shows itself (e.g. in a frame of its
    /// own, or as a dialog), as an open tab's root does in Stroom, unless something already owns
    /// it: it is disposed, with everything it owns, when the story renders again or the leak probe
    /// closes the screen.
    ///
    /// @param screen The component or presenter.
    /// @param <C>    Its type.
    /// @return It.
    public <C extends HasControllerNode> C own(final C screen) {
        final ControllerNode node = screen.getControllerNode();
        if (node.parent() == null) {
            getComponentRoot().adopt(node);
        }
        return screen;
    }

    /// Owns a popup as [#own] does, and closes it as the user would, with its Close button (a
    /// `HidePopupRequestEvent` with `DialogAction.CLOSE`), when the story renders again, before the
    /// harness hides the popups that are still open. Use it for a popup whose presenter stops
    /// something only when asked to close, e.g. one that polls the server until its
    /// `onHideRequest` cancels its timer (the harness's own hiding, with `HidePopupEvent`, skips
    /// that). The REST requests made meanwhile are dropped, as the dispatcher is disposed first.
    ///
    /// @param popup The popup's presenter.
    /// @param <P>   The presenter type.
    /// @return The presenter.
    public <P extends MyPresenterWidget<?>> P ownPopup(final P popup) {
        popupsToClose.add(own(Objects.requireNonNull(popup)));
        return popup;
    }

    /// @return True once the story has rendered again and the harness has been disposed.
    public boolean isDisposed() {
        return disposed;
    }

    private void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (shown == this) {
            shown = null;
        }
        // Stop the network first, so nothing below can start new requests or receive replies
        if (dispatcher != null) {
            dispatcher.dispose();
        }
        if (uploadTransport != null) {
            runSafely("restoring Stroom's upload transport", uploadTransport::dispose);
        }
        if (uiConfigCache != null) {
            runSafely("stopping the UI config refresh", uiConfigCache::stopRefreshing);
        }
        if (locationManager != null) {
            runSafely("removing the window-closing handler", locationManager::removeWindowClosingHandler);
        }

        // Close the popups that must be asked to close (e.g. to stop polling), as their Close
        // buttons would
        for (final MyPresenterWidget<?> popup : popupsToClose) {
            runSafely("closing a popup", () -> HidePopupRequestEvent.builder(popup)
                    .action(DialogAction.CLOSE)
                    .fire());
        }
        // Hide the popups as Stroom would, which unbinds their presenters and restores the focus.
        // The bus is still live for this, but spies ignore calls now the harness is disposed.
        for (final MyPresenterWidget<?> popup : popups.getOpenPopups()) {
            runSafely("hiding a popup", () -> HidePopupEvent.builder(popup).autoClose(true).ok(false).fire());
        }
        for (int i = cleanUps.size() - 1; i >= 0; i--) {
            runSafely("running a clean up", cleanUps.get(i));
        }
        cleanUps.clear();

        if (eventBus != null) {
            eventBus.dispose();
        }
        if (injector != null) {
            runSafely("disposing the security context", () -> injector.getSecurityContext().dispose());
        }
    }

    private void runSafely(final String what, final Runnable runnable) {
        try {
            runnable.run();
        } catch (final RuntimeException e) {
            consoleError("Error " + what + " while cleaning up a screen story: " + e);
        }
    }

    private static String describe(final CommonAlertEvent<?> event) {
        return event.getLevel() + ": " + (event.getMessage() != null
                ? event.getMessage().asString()
                : "");
    }

    /// Shows a problem in the story and reports it as an uncaught exception, so that the play
    /// function (and the test runner) fails.
    private void fail(final String message) {
        final Label label = new Label(message);
        label.setStyleName(ERROR_STYLE);
        label.getElement().setAttribute("role", "alert");
        label.getElement().getStyle().setProperty("color", "#c00");
        label.getElement().getStyle().setProperty("fontFamily", "monospace");
        label.getElement().getStyle().setProperty("whiteSpace", "pre-wrap");
        host.insert(label, 0);
        GWT.reportUncaughtException(new IllegalStateException("Screen story failed: " + message));
    }

    // GWT.log does nothing in compiled JavaScript, so report to the browser's console
    private static void warn(final String message) {
        consoleWarn(message);
    }

    private static native void consoleWarn(String message) /*-{
        if ($wnd.console && $wnd.console.warn) {
            $wnd.console.warn(message);
        }
    }-*/;

    private static native void consoleError(String message) /*-{
        if ($wnd.console && $wnd.console.error) {
            $wnd.console.error(message);
        }
    }-*/;

    // --------------------------------------------------------------------------------


    /// Builds a [ScreenHarness] with options.
    public static final class Builder {

        private final StoryContext context;
        private final RestFixtures fixtures;
        private final StartupFixtures.Builder startup = StartupFixtures.builder();
        private boolean startupFixtures = true;
        private boolean realAlerts;
        private ScreenGinjector injector;

        private Builder(final StoryContext context, final RestFixtures fixtures) {
            this.context = Objects.requireNonNull(context);
            this.fixtures = Objects.requireNonNull(fixtures);
        }

        /// @param user The current user, for both the security context and the start-up fixtures
        ///             (session info, app permissions).
        /// @return This builder.
        public Builder user(final UserRef user) {
            startup.user(user);
            return this;
        }

        /// @param permissions The current user's app permissions (default `ADMINISTRATOR`), for
        ///                    both the security context and the start-up fixtures.
        /// @return This builder.
        public Builder appPermissions(final AppPermission... permissions) {
            startup.appPermissions(permissions);
            return this;
        }

        /// @param uiConfigJson The `uiConfig` part of the extended UI config fixture.
        /// @return This builder.
        public Builder uiConfig(final String uiConfigJson) {
            startup.uiConfig(uiConfigJson);
            return this;
        }

        /// @param configurer Changes other start-up fixtures, e.g. the user preferences.
        /// @return This builder.
        public Builder startup(final Consumer<StartupFixtures.Builder> configurer) {
            configurer.accept(startup);
            return this;
        }

        /// Leaves out the start-up fixtures, so the story's fixtures must answer every request.
        ///
        /// @return This builder.
        public Builder withoutStartupFixtures() {
            startupFixtures = false;
            return this;
        }

        /// Shows alerts and confirmations in Stroom's real alert dialog (as well as recording
        /// them), e.g. for stories ported from ones that use `ApiErrorAlertHost`, or that click OK
        /// in a confirmation.
        ///
        /// @return This builder.
        public Builder realAlerts() {
            realAlerts = true;
            return this;
        }

        /// Uses an injector that extends [ScreenGinjector] with the screen's presenters, e.g. one of
        /// the area injectors in `stroom.gwt.workbench.client.app.gin`, in place of a plain
        /// [ScreenGinjector], so the story can get its presenter from GIN as Stroom does:
        /// ```
        /// final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        /// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
        /// final JobPresenter presenter = injector.getJobPresenter();
        /// ```
        /// Create a new injector for each harness (i.e. each time the story renders): its
        /// singletons (event bus, REST factory, security context...) belong to the harness.
        ///
        /// @param injector A new injector, not yet used by another harness.
        /// @return This builder.
        public Builder injector(final ScreenGinjector injector) {
            this.injector = Objects.requireNonNull(injector);
            return this;
        }

        /// @return The harness.
        public ScreenHarness build() {
            return new ScreenHarness(this);
        }

        private RestFixtures buildFixtures() {
            return startupFixtures
                    ? fixtures.followedBy(startup.build())
                    : fixtures;
        }
    }

    // --------------------------------------------------------------------------------


    /// Records the requests and reports problems with them.
    private final class HarnessListener implements FixtureDispatcher.Listener {

        @Override
        public void onRequest(final RecordedRequest request) {
            spy(REQUEST_SPY, request.describeWithBody());
        }

        @Override
        public void onUnhandledRequest(final RecordedRequest request, final String message, final boolean strict) {
            warn(message + (request.getBody() != null
                    ? "\nBody: " + request.getBody()
                    : ""));
            spy(UNHANDLED_REQUEST_SPY, request.describeWithBody());
            if (strict) {
                fail(message);
            }
        }

        @Override
        public void onFixtureError(final RecordedRequest request, final String message) {
            warn(message);
            fail(message);
        }

        @Override
        public void onDroppedRequest(final RecordedRequest request) {
            warn("A request from a previous rendering of the story was dropped: " + request.describe());
        }
    }

    // --------------------------------------------------------------------------------


    /// Records the uploads and reports problems with them, as [HarnessListener] does for requests.
    private final class HarnessUploadListener implements FixtureUploads.Listener {

        @Override
        public void onUpload(final RecordedUpload upload) {
            if (!disposed) {
                fn(UPLOAD_SPY).call(upload.getUrl(), upload.getFileName(), upload.getContent());
            }
        }

        @Override
        public void onUnhandledUpload(final RecordedUpload upload, final String message, final boolean strict) {
            warn(message);
            spy(UNHANDLED_REQUEST_SPY, upload.describe());
            if (strict) {
                fail(message);
            }
        }

        @Override
        public void onDroppedUpload(final RecordedUpload upload) {
            warn("An upload from a previous rendering of the story was dropped: " + upload.describe());
        }
    }
}
