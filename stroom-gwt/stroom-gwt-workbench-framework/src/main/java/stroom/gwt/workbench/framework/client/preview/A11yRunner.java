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

package stroom.gwt.workbench.framework.client.preview;

import stroom.gwt.workbench.framework.client.BrowserUtil;

import com.google.gwt.core.client.JavaScriptObject;

/// Runs accessibility checks on the story with axe-core, as React Storybook's accessibility addon
/// does, and posts the results to the manager. It also highlights elements in the story for the
/// addon.
///
/// axe-core isn't part of the workbench. The workbench server serves it from
/// `__workbench/axe.min.js` if it has been fetched (see the `workbenchFetchAxe` Gradle task);
/// without it the addon explains how to get it.
public final class A11yRunner {

    private static final String AXE_URL = "__workbench/axe.min.js";
    private static final String HIGHLIGHT_ID = "wbp-a11y-highlights";

    private A11yRunner() {
        // Static utility
    }

    /// Checks the story and posts the results to the manager.
    public static void run() {
        BrowserUtil.postToManager(BrowserUtil.A11Y_RESULT_MESSAGE, "running", null);
        runAxe(AXE_URL, BrowserUtil.A11Y_RESULT_MESSAGE);
    }

    /// Removes any highlights, e.g. because the story has been re-rendered so they no longer
    /// match the elements they outlined.
    public static void clearHighlights() {
        highlight(null);
    }

    private static native void runAxe(String axeUrl, String messageType) /*-{
        var post = function (status, detail) {
            @stroom.gwt.workbench.framework.client.BrowserUtil::postToManager(*)(messageType, status, detail);
        };
        if (!$wnd.__sbmAxeState) {
            $wnd.__sbmAxeState = {loading: false, running: false, rerun: false};
        }
        var state = $wnd.__sbmAxeState;
        // axe can only run once at a time, so remember a request made while it is loading or
        // running and run it again afterwards
        if (state.loading || state.running) {
            state.rerun = true;
            return;
        }
        var finished = function () {
            state.running = false;
            if (state.rerun) {
                state.rerun = false;
                @stroom.gwt.workbench.framework.client.preview.A11yRunner::runAxe(*)(axeUrl, messageType);
            }
        };
        // A shadow DOM target is an array of selectors from the outermost host inwards, which
        // highlight() splits again
        var targetToString = function (target) {
            return Array.isArray(target) ? target.map(String).join(' >>> ') : String(target);
        };
        var simplify = function (rules) {
            return rules.map(function (rule) {
                return {
                    id: rule.id,
                    help: rule.help,
                    description: rule.description,
                    impact: rule.impact || null,
                    helpUrl: rule.helpUrl,
                    nodes: rule.nodes.map(function (node) {
                        return {
                            html: node.html,
                            target: (node.target || []).map(targetToString),
                            failureSummary: node.failureSummary || null
                        };
                    })
                };
            });
        };
        var scan = function () {
            if (state.running) {
                state.rerun = true;
                return;
            }
            state.running = true;
            var root = $doc.getElementById('workbench-root') || $doc.body;
            var promise;
            try {
                // Without preloading, axe doesn't fetch the page's stylesheets itself, which
                // fails (404s) for stylesheets that import others relatively; no rule result
                // depends on it for stories
                promise = $wnd.axe.run(root, {preload: false});
            } catch (e) {
                promise = $wnd.Promise.reject(e);
            }
            promise.then(function (results) {
                post('ready', JSON.stringify({
                    violations: simplify(results.violations),
                    passes: simplify(results.passes),
                    incomplete: simplify(results.incomplete)
                }));
            })['catch'](function (error) {
                post('error', String(error && error.message ? error.message : error));
            }).then(finished, finished);
        };
        if ($wnd.axe) {
            scan();
            return;
        }
        state.loading = true;
        var script = $doc.createElement('script');
        script.src = axeUrl;
        script.onload = $entry(function () {
            state.loading = false;
            // This scan covers any requests made while loading
            state.rerun = false;
            scan();
        });
        script.onerror = $entry(function () {
            state.loading = false;
            state.rerun = false;
            // Remove the script so a later run tries again
            script.parentNode.removeChild(script);
            post('unavailable', null);
        });
        $doc.head.appendChild(script);
    }-*/;

    /// Outlines elements in the story, or removes the outlines.
    ///
    /// @param selectorsJson A JSON array of `{selector, colour}` objects, or null to remove.
    public static native void highlight(String selectorsJson) /*-{
        var old = $doc.getElementById(@stroom.gwt.workbench.framework.client.preview.A11yRunner::HIGHLIGHT_ID);
        if (old) {
            old.parentNode.removeChild(old);
        }
        if (!selectorsJson) {
            return;
        }
        // A 33 alpha suffix gives a translucent fill, but only for #rrggbb colours
        var fill = function (colour) {
            if (typeof colour !== 'string') {
                return 'transparent';
            }
            if (/^#[0-9a-f]{6}$/i.test(colour)) {
                return colour + '33';
            }
            if (/^#[0-9a-f]{3}$/i.test(colour)) {
                return '#' + colour.charAt(1) + colour.charAt(1) + colour.charAt(2) + colour.charAt(2)
                    + colour.charAt(3) + colour.charAt(3) + '33';
            }
            return 'transparent';
        };
        var container = $doc.createElement('div');
        container.id = @stroom.gwt.workbench.framework.client.preview.A11yRunner::HIGHLIGHT_ID;
        JSON.parse(selectorsJson).forEach(function (item) {
            var elements = @stroom.gwt.workbench.framework.client.preview.A11yRunner::findAll(*)(item.selector);
            Array.prototype.forEach.call(elements, function (el) {
                var rect = el.getBoundingClientRect();
                var box = $doc.createElement('div');
                box.className = 'wbp-a11y-highlight';
                box.style.left = (rect.left + $wnd.scrollX) + 'px';
                box.style.top = (rect.top + $wnd.scrollY) + 'px';
                box.style.width = rect.width + 'px';
                box.style.height = rect.height + 'px';
                if (typeof item.colour === 'string') {
                    box.style.borderColor = item.colour;
                }
                box.style.backgroundColor = fill(item.colour);
                container.appendChild(box);
            });
        });
        $doc.body.appendChild(container);
    }-*/;

    /// Finds the elements matching an axe target, which may be a path through shadow roots with
    /// the selectors separated by ` >>> `.
    ///
    /// @return The elements, which is empty if the selector isn't valid.
    private static native JavaScriptObject findAll(String selector) /*-{
        if (typeof selector !== 'string' || !selector) {
            return [];
        }
        try {
            var parts = selector.split(' >>> ');
            var scope = $doc;
            for (var i = 0; i < parts.length - 1; i++) {
                var host = scope.querySelector(parts[i]);
                if (!host || !host.shadowRoot) {
                    return [];
                }
                scope = host.shadowRoot;
            }
            return Array.prototype.slice.call(scope.querySelectorAll(parts[parts.length - 1]));
        } catch (e) {
            return [];
        }
    }-*/;

    /// Scrolls an element into view and outlines it.
    ///
    /// @param selector The element's CSS selector.
    /// @param colour   The colour of the outline.
    public static native void jumpTo(String selector, String colour) /*-{
        var el = @stroom.gwt.workbench.framework.client.preview.A11yRunner::findAll(*)(selector)[0];
        if (el) {
            el.scrollIntoView({block: 'center'});
            @stroom.gwt.workbench.framework.client.preview.A11yRunner::highlight(*)(
                JSON.stringify([{selector: selector, colour: colour}]));
        }
    }-*/;
}
