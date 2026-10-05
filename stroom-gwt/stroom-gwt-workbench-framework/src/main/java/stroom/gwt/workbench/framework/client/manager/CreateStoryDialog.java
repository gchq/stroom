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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.BrowserUtil;
import stroom.gwt.workbench.framework.client.story.StoryUrls;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;

/// The 'Add a new story' dialog opened by the + button next to the search box, as in React
/// Storybook. It lists the components that could have stories and creates a stories class for
/// the one chosen, which the workbench shows once it has been recompiled.
public class CreateStoryDialog {

    private static final String COMPONENTS_URL = "__workbench/api/components?q=";
    private static final String CREATE_URL = "__workbench/api/create-story?class=";
    private static final String ATTR_CLASS = "data-class";
    private static final String ATTR_STORY = "data-go-to-story";
    private static final int SEARCH_DELAY_MILLIS = 200;

    private Element backdrop;
    private Element results;
    private InputElement input;
    private HandlerRegistration keyRegistration;
    private Timer searchTimer;
    // Bumped on each search and each time the dialog opens or closes, so stale responses are
    // ignored
    private int searchGeneration;
    // The element that had the focus before the dialog opened, to give it back on close
    private Element previousFocus;
    // True if the current mouse press started on the backdrop rather than in the dialog
    private boolean mouseDownOnBackdrop;

    /// Opens the dialog.
    public void show() {
        if (backdrop != null) {
            return;
        }
        Popover.hideCurrent();
        searchGeneration++;
        previousFocus = BrowserUtil.getActiveElement();
        final DivElement div = Document.get().createDivElement();
        div.setClassName("wbm-modal-backdrop");
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"wbm-modal\" role=\"dialog\" aria-modal=\"true\" "
                                   + "aria-label=\"Create stories for a component\">"
                                   + "<div class=\"wbm-modal__header\"><div>"
                                   + "<div class=\"wbm-modal__title\">Create stories for a component</div>"
                                   + "<div class=\"wbm-modal__description\">Choose a GWT widget and a stories "
                                   + "class with a default story is created for it</div></div>"
                                   + "<button type=\"button\" class=\"wbm-icon-button wbm-modal__close\" "
                                   + "aria-label=\"Close\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-cross");
        builder.appendHtmlConstant("</button></div><label class=\"wbm-modal__search\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-search");
        builder.appendHtmlConstant("<input type=\"text\" placeholder=\"./**/client/**/*.java\" spellcheck=\"false\" "
                                   + "autocomplete=\"off\" aria-label=\"Search for a component\"></label>"
                                   + "<div class=\"wbm-modal__results\"></div></div>");
        div.setInnerSafeHtml(builder.toSafeHtml());
        Document.get().getBody().appendChild(div);
        backdrop = div;
        results = BrowserUtil.querySelector(div, ".wbm-modal__results");
        input = BrowserUtil.querySelector(div, "input").cast();

        // A click that started in the dialog, e.g. selecting text, mustn't close it when the
        // mouse is released over the backdrop
        BrowserUtil.addListener(div, "mousedown",
                event -> mouseDownOnBackdrop = Element.as(event.getEventTarget()) == backdrop);
        BrowserUtil.addListener(div, "click", event -> onClick(Element.as(event.getEventTarget())));
        BrowserUtil.addListener(input, "input", event -> scheduleSearch());
        keyRegistration = Event.addNativePreviewHandler(preview -> {
            if (preview.getTypeInt() != Event.ONKEYDOWN) {
                return;
            }
            final NativeEvent event = preview.getNativeEvent();
            if (event.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                hide();
            } else if (event.getKeyCode() == KeyCodes.KEY_TAB) {
                trapFocus(event);
            }
        });
        BrowserUtil.focus(input);
    }

    private void hide() {
        searchGeneration++;
        if (searchTimer != null) {
            searchTimer.cancel();
            searchTimer = null;
        }
        if (backdrop != null) {
            backdrop.removeFromParent();
            backdrop = null;
        }
        if (keyRegistration != null) {
            keyRegistration.removeHandler();
            keyRegistration = null;
        }
        // Give the focus back to where it was, e.g. the + button
        if (previousFocus != null && previousFocus.getParentElement() != null) {
            previousFocus.focus();
        }
        previousFocus = null;
    }

    /// Keeps the focus within the dialog, as for any modal dialog, by wrapping from the last
    /// focusable element to the first and back.
    private void trapFocus(final NativeEvent event) {
        final NodeList<Element> focusable = BrowserUtil.querySelectorAll(backdrop,
                "button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex='-1'])");
        if (focusable.getLength() == 0) {
            event.preventDefault();
            return;
        }
        final Element first = focusable.getItem(0);
        final Element last = focusable.getItem(focusable.getLength() - 1);
        final Element active = BrowserUtil.getActiveElement();
        final boolean inside = active != null && backdrop.isOrHasChild(active);
        if (event.getShiftKey() && (!inside || active == first)) {
            event.preventDefault();
            last.focus();
        } else if (!event.getShiftKey() && (!inside || active == last)) {
            event.preventDefault();
            first.focus();
        }
    }

    private void scheduleSearch() {
        if (searchTimer != null) {
            searchTimer.cancel();
        }
        searchTimer = new Timer() {
            @Override
            public void run() {
                search(input.getValue());
            }
        };
        searchTimer.schedule(SEARCH_DELAY_MILLIS);
    }

    private void search(final String query) {
        if (query == null || query.trim().isEmpty()) {
            results.setInnerHTML("");
            return;
        }
        final int generation = ++searchGeneration;
        BrowserUtil.request("GET", COMPONENTS_URL + BrowserUtil.encodeQueryParam(query.trim()), (status, body) -> {
            if (generation != searchGeneration || backdrop == null) {
                return;
            }
            if (status != 200) {
                showMessage(errorMessage(status, body), true);
                return;
            }
            final JSONValue value = parse(body);
            final JSONArray components = value != null
                    ? value.isArray()
                    : null;
            if (components == null) {
                showMessage("The workbench server returned an unexpected response.", true);
                return;
            }
            showComponents(components);
        });
    }

    private void showComponents(final JSONArray components) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        int count = 0;
        for (int i = 0; i < components.size(); i++) {
            final JSONValue value = components.get(i);
            final JSONObject component = value != null
                    ? value.isObject()
                    : null;
            final String className = getString(component, "className");
            if (className == null) {
                continue;
            }
            count++;
            builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-modal__result\" " + ATTR_CLASS + "=\"")
                    .appendEscaped(className)
                    .appendHtmlConstant("\">");
            MenuHtml.appendIcon(builder, "wbm-icon wbm-modal__result-icon", "wbm-icon-component");
            builder.appendHtmlConstant("<span><span class=\"wbm-modal__result-name\">")
                    .appendEscaped(orDefault(getString(component, "name"), className) + ".java")
                    .appendHtmlConstant("</span><span class=\"wbm-modal__result-path\">")
                    .appendEscaped(orDefault(getString(component, "path"), ""))
                    .appendHtmlConstant("</span></span></button>");
        }
        if (count == 0) {
            builder.appendHtmlConstant("<div class=\"wbm-modal__message\">No components found</div>");
        }
        results.setInnerSafeHtml(builder.toSafeHtml());
    }

    private void create(final String className) {
        showMessage("Creating stories for " + className + "...", false);
        final int generation = searchGeneration;
        BrowserUtil.request("POST", CREATE_URL + BrowserUtil.encodeQueryParam(className), (status, body) -> {
            if (backdrop == null || generation != searchGeneration) {
                return;
            }
            if (status != 200) {
                showMessage(errorMessage(status, body), true);
                return;
            }
            final JSONValue value = parse(body);
            final JSONObject result = value != null
                    ? value.isObject()
                    : null;
            final String file = getString(result, "file");
            final String storyId = getString(result, "storyId");
            if (file == null || storyId == null) {
                showMessage("The workbench server returned an unexpected response.", true);
                return;
            }
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            builder.appendHtmlConstant("<div class=\"wbm-modal__message\"><strong>Story created</strong><code>")
                    .appendEscaped(file)
                    .appendHtmlConstant("</code><span>It has been added to the stories' registry. The workbench "
                                        + "shows it once recompiled, which happens when the page is reloaded if "
                                        + "the code server is running.</span><button type=\"button\" "
                                        + "class=\"wbm-secondary-button\" " + ATTR_STORY + "=\"")
                    .appendEscaped(storyId)
                    .appendHtmlConstant("\">Reload and go to story</button></div>");
            results.setInnerSafeHtml(builder.toSafeHtml());
        });
    }

    private void showMessage(final String message, final boolean error) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"wbm-modal__message" + (error
                        ? " wbm-modal__message--error"
                        : "") + "\">")
                .appendEscaped(message)
                .appendHtmlConstant("</div>");
        results.setInnerSafeHtml(builder.toSafeHtml());
    }

    private void onClick(final Element target) {
        if ((target == backdrop && mouseDownOnBackdrop)
            || BrowserUtil.closest(target, ".wbm-modal__close") != null) {
            hide();
            return;
        }
        final Element result = BrowserUtil.closest(target, "[" + ATTR_CLASS + "]");
        if (result != null) {
            create(result.getAttribute(ATTR_CLASS));
            return;
        }
        final Element goTo = BrowserUtil.closest(target, "[" + ATTR_STORY + "]");
        if (goTo != null) {
            Window.Location.assign(StoryUrls.managerUrl(goTo.getAttribute(ATTR_STORY)));
        }
    }

    /// @param status The HTTP status of a failed API request.
    /// @param body   The response body.
    /// @return A message describing the failure.
    static String errorMessage(final int status, final String body) {
        if (status == 0) {
            return "The workbench server can't be reached.";
        }
        final JSONValue value = parse(body);
        if (value != null && value.isObject() != null) {
            final String error = getString(value.isObject(), "error");
            if (error != null) {
                return error;
            }
        }
        return "The workbench server returned an error (" + status + ").";
    }

    /// @return The parsed JSON, or null if the text is null or isn't valid JSON.
    private static JSONValue parse(final String text) {
        if (text == null) {
            return null;
        }
        try {
            return JSONParser.parseStrict(text);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static String orDefault(final String value, final String defaultValue) {
        return value != null
                ? value
                : defaultValue;
    }

    private static String getString(final JSONObject object, final String key) {
        if (object == null) {
            return null;
        }
        final JSONValue value = object.get(key);
        return value != null && value.isString() != null
                ? value.isString().stringValue()
                : null;
    }
}
