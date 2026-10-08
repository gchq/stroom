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

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;

import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The play helpers of the dashboard batch's stories: Stroom's dashboard markup and the
/// requests its searches make.
final class DashboardPlays {

    /// The label of a component's tab.
    /// Differs from React: a dashboard's component tabs are Stroom link tabs, with no `role="tab"`.
    static final String COMPONENT_TAB = ".tabLayout-tabBar .linkTab-label";

    /// The class of a selected link tab (React's `aria-selected="true"`).
    static final String SELECTED_TAB = "linkTab-selected";

    /// A tab layout (React's panel, `[data-panel-path]`).
    static final String PANEL = ".tabLayout";

    /// A splitter between panels (React's `.flexLayout-splitter`, the same class).
    static final String SPLITTER = ".flexLayout-splitter";

    /// A Query component's own Execute Query button (React's `componentRunButton`).
    static final String QUERY_RUN_BUTTON = ".QueryViewImpl .QueryButtons-button";

    private DashboardPlays() {
        // Static utility
    }

    /// Waits for the dashboard to open: Stroom fetches it and lays out its components after the
    /// story has rendered (React's editor renders at once).
    ///
    /// @param play The play.
    static void opened(final Play play) {
        play.waitFor(5000, () -> play.expect(play.querySelectorAll(COMPONENT_TAB).nth(0)).toBeInTheDocument());
    }

    /// @param play The play.
    /// @param name The component's name.
    /// @return The label of the component's tab.
    static Query tab(final Play play, final String name) {
        // Waits for it: a nested split's tab bars are laid out after the first, so under load a
        // tab may not be drawn yet when the dashboard first shows
        return play.findByText(name, COMPONENT_TAB);
    }

    /// @param play The play.
    /// @param name The component's name.
    /// @return The component's tab (the label's link tab), e.g. to check whether it is selected.
    static Query linkTab(final Play play, final String name) {
        return tab(play, name).closest(".linkTab");
    }

    /// @param play  The play.
    /// @param title The button's title, e.g. `Execute Query`.
    /// @return A button of the dashboard's own query toolbar (React's `dashToolbarButton`).
    static Query toolbarButton(final Play play, final String title) {
        return play.within(play.querySelector(".QueryToolbarViewImpl-inner")).getByTitle(title);
    }

    /// Clicks a Query component's own Execute Query button (React's `componentRunButton`).
    ///
    /// @param play  The play.
    /// @param index The index of the Query component on the page.
    static void runQuery(final Play play, final int index) {
        // Waits until it is a run button: while a query is still running it is a stop button, so
        // under load a second run (e.g. after changing a filter) could stop the first instead
        play.waitFor(() -> play.expect(play.querySelectorAll(QUERY_RUN_BUTTON).nth(index)).toHaveClass("play"));
        play.click(play.querySelectorAll(QUERY_RUN_BUTTON).nth(index));
    }

    /// Clicks the dashboard toolbar's Execute Query, which runs every query of the dashboard.
    /// Differs from React: an Embedded Query has no Execute button of its own in GWT (its panel shows
    /// only its results), so the stories run it with the dashboard's.
    ///
    /// @param play The play.
    static void runAll(final Play play) {
        play.click(toolbarButton(play, "Execute Query"));
    }

    /// Enters (or leaves) design mode with the toolbar's toggle.
    ///
    /// @param play  The play.
    /// @param enter Whether to enter design mode.
    static void designMode(final Play play, final boolean enter) {
        play.click(play.getByTitle(enter
                ? "Enter Design Mode"
                : "Exit Design Mode"));
    }

    /// Selects a component's tab, if it isn't selected, by clicking it.
    ///
    /// @param play The play.
    /// @param name The component's name.
    static void selectTab(final Play play, final String name) {
        final Query label = tab(play, name);
        final Query link = linkTab(play, name);
        play.run("select the tab " + name, () -> {
            if (!link.element().get().hasClassName(SELECTED_TAB)) {
                final Element element = label.element().get();
                final double[] centre = point(element, 0.5, 0.5, 0, 0);
                dispatchMouse(element, "mousedown", centre[0], centre[1], 1);
                dispatchMouse(element, "mouseup", centre[0], centre[1], 0);
                dispatchMouse(element, "click", centre[0], centre[1], 0);
            }
        });
        play.waitFor(() -> play.expect(linkTab(play, name)).toHaveClass(SELECTED_TAB));
    }

    /// Opens a component's tab menu.
    /// Differs from React: Stroom opens a tab's menu when its selected tab is clicked
    /// (`FlexLayout.finishSelection`), not on a context menu; a click on a tab that isn't selected
    /// selects it, so the tab is selected first.
    ///
    /// @param play The play.
    /// @param name The component's name.
    static void openTabMenu(final Play play, final String name) {
        selectTab(play, name);
        play.click(tab(play, name));
        play.screen().findByText("Rename", StroomDom.MENU_ITEM_TEXT);
    }

    /// @param screen The play of the page's body.
    /// @param text   The item's text.
    /// @return A menu item (Stroom's menu items have no `menuitem` role).
    static Query menuItem(final Play screen, final String text) {
        return screen.findByText(text, StroomDom.MENU_ITEM_TEXT);
    }

    /// @param screen The play of the page's body.
    /// @param text   The item's text.
    /// @return An item of a menu of plain items (`SimpleMenuItem`, e.g. Add Component's).
    static Query simpleMenuItem(final Play screen, final String text) {
        return screen.findByText(text, ".menuItem-simpleText");
    }

    /// @param screen  The play of the page's body.
    /// @param caption The dialog's caption.
    /// @return The dialog, once shown.
    static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    /// Clicks a part of a results table's column header, as the query batch's
    /// `QueryEditorStories.clickHeader` does: `MyDataGrid` only handles a header's mouse buttons
    /// once the mouse has moved over it.
    ///
    /// @param play   The play.
    /// @param target The part of the header to click.
    static void clickHeader(final Play play, final Query target) {
        play.fireEvent().mouseMove(target, EventInit.create().atCentreOf(target));
        play.sleep(200);
        play.fireEvent().mouseDown(target, EventInit.create().atCentreOf(target).buttons(1));
        play.fireEvent().mouseUp(target, EventInit.create().atCentreOf(target));
        play.fireEvent().click(target, EventInit.create().atCentreOf(target));
    }

    /// @param play The play.
    /// @param name The column's name.
    /// @return The label of a results table's column header.
    static Query header(final Play play, final String name) {
        return play.getByText(name, ".column-top .column-label");
    }

    /// Drags with the mouse, as Stroom's `FlexLayout` reads it (mouse events, the move and release
    /// captured from anywhere on the page): presses the main button at the centre of `from`, moves
    /// in steps to a point of `to`, and releases it there. The point is given as fractions of the
    /// target's size plus an offset in pixels, read when the step runs.
    /// Differs from React: React's port drags with pointer events.
    ///
    /// @param play The play.
    /// @param from The element to press, e.g. a tab.
    /// @param to   The element the point is relative to.
    /// @param fx   The fraction of the target's width.
    /// @param fy   The fraction of the target's height.
    /// @param dx   The offset in pixels right of that.
    /// @param dy   The offset in pixels below that.
    static void drag(final Play play,
                     final Query from,
                     final Query to,
                     final double fx,
                     final double fy,
                     final double dx,
                     final double dy) {
        final Query source = from;
        final Query target = to;
        play.run("drag " + from.describe() + " to " + to.describe(), () -> {
            final Element sourceElement = source.element().get();
            final Element targetElement = target.element().get();
            final double[] start = point(sourceElement, 0.5, 0.5, 0, 0);
            final double[] end = point(targetElement, fx, fy, dx, dy);
            dispatchMouse(sourceElement, "mousedown", start[0], start[1], 1);
            for (int i = 1; i <= 5; i++) {
                dispatchMouse(sourceElement.getOwnerDocument().getBody(), "mousemove",
                        start[0] + (end[0] - start[0]) * i / 5,
                        start[1] + (end[1] - start[1]) * i / 5,
                        1);
            }
            dispatchMouse(sourceElement.getOwnerDocument().getBody(), "mouseup", end[0], end[1], 0);
        });
    }

    // A point of an element, in client coordinates
    private static double[] point(final Element element,
                                  final double fx,
                                  final double fy,
                                  final double dx,
                                  final double dy) {
        final double[] rect = rect(element);
        return new double[]{rect[0] + rect[2] * fx + dx, rect[1] + rect[3] * fy + dy};
    }

    // An element's left, top, width and height
    private static native double[] rect(Element element) /*-{
        var r = element.getBoundingClientRect();
        return [r.left, r.top, r.width, r.height];
    }-*/;

    // Fires a mouse event of the main button
    private static native void dispatchMouse(Element element,
                                             String type,
                                             double x,
                                             double y,
                                             int buttons) /*-{
        var view = element.ownerDocument.defaultView;
        element.dispatchEvent(new view.MouseEvent(type, {
            bubbles: true, cancelable: true, view: view, clientX: x, clientY: y, button: 0, buttons: buttons
        }));
    }-*/;

    /// Fires the window's `beforeunload` event, as closing the browser's tab or window does, which
    /// Stroom's `LocationManager` reports as a `WindowCloseEvent`.
    /// Differs from React: React's port listens for `pagehide`.
    ///
    /// @param play The play.
    static void closeWindow(final Play play) {
        play.run("window.dispatchEvent(new Event('beforeunload'))", DashboardPlays::fireBeforeUnload);
    }

    private static native void fireBeforeUnload() /*-{
        $wnd.dispatchEvent(new $wnd.Event('beforeunload', {cancelable: true}));
    }-*/;

    /// @param spy     The request spy.
    /// @param matcher The requests to find.
    /// @return The requests the spy recorded that match.
    static List<RecordedRequest> requests(final Spy spy, final RequestMatcher matcher) {
        final List<RecordedRequest> list = new ArrayList<>();
        for (final List<Object> call : spy.getCalls()) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if (matcher.matches(request)) {
                list.add(request);
            }
        }
        return list;
    }

    /// @param json A JSON value, e.g. a request's body.
    /// @param path The members (strings) and items (integers) to follow.
    /// @return The value at the path, or null if there is none.
    static Object at(final Object json, final Object... path) {
        Object value = json instanceof final String text
                ? JsonValues.parse(text)
                : json;
        for (final Object key : path) {
            if (key instanceof final Integer index && value instanceof final List<?> list) {
                value = index < list.size()
                        ? list.get(index)
                        : null;
            } else if (value instanceof final Map<?, ?> map) {
                value = map.get(key);
            } else {
                return null;
            }
        }
        return value;
    }

    /// @param body A search request's body.
    /// @param key  A parameter's key.
    /// @return The value of the parameter in the request's `params` (a dashboard search's
    /// `search.params` or a StroomQL search's `queryContext.params`), or null.
    static String param(final String body, final String key) {
        final Object json = JsonValues.parse(body);
        Object params = at(json, "search", "params");
        if (params == null) {
            params = at(json, "queryContext", "params");
        }
        if (params instanceof final List<?> list) {
            for (final Object param : list) {
                if (key.equals(at(param, "key"))) {
                    return String.valueOf(at(param, "value"));
                }
            }
        }
        return null;
    }

    /// @param body A dashboard search request's body.
    /// @return The ids of the components whose results it asks for.
    static List<String> componentIds(final String body) {
        final List<String> ids = new ArrayList<>();
        if (at(body, "componentResultRequests") instanceof final List<?> list) {
            for (final Object request : list) {
                ids.add(String.valueOf(at(request, "componentId")));
            }
        }
        return ids;
    }

    /// @param body        A dashboard search request's body.
    /// @param componentId A component's id.
    /// @return The request for the component's results, or null.
    static Object componentRequest(final String body, final String componentId) {
        if (at(body, "componentResultRequests") instanceof final List<?> list) {
            for (final Object request : list) {
                if (componentId.equals(at(request, "componentId"))) {
                    return request;
                }
            }
        }
        return null;
    }

    /// @param item An expression item (JSON).
    /// @return The value of its first term, depth first (React's `firstTermValue`), or null.
    static String firstTermValue(final Object item) {
        if (at(item, "children") instanceof final List<?> children) {
            for (final Object child : children) {
                final String value = firstTermValue(child);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        final Object value = at(item, "value");
        return value == null || "".equals(value)
                ? null
                : String.valueOf(value);
    }

    /// Checks that the story had no alerts and made no request without a fixture.
    ///
    /// @param play The play.
    static void expectNoProblems(final Play play) {
        DashboardSupport.expectNoProblems(play);
    }
}
