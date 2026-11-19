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

package stroom.widget.util.client;

import stroom.widget.datagrid.client.model.TextScale;

import com.google.gwt.core.client.JavaScriptObject;

/// Measures how much wider the page draws text than at the medium text size, and sets the page's
/// `--text-scale` to it, so that widths given at the medium size (grid columns, fields; see
/// `TextScale`) follow whatever widens text: the text size preference, text spacing a user sets
/// for themselves (WCAG 1.4.12), a platform's font, or letters rounded to whole pixels.
///
/// A reference text is drawn twice, out of sight (`text-scale-meter`, `text-scale-meter.css`): as
/// the page draws text, and at the medium size with no spacing. The scale is the ratio of their
/// widths, never less than the preference's own (`font.css`). It is measured when installed and
/// again whenever either width changes (fonts loading, the preference or a user's spacing
/// changing), when what has drawn widths at the old scale is told ([#CHANGE_EVENT]). At the
/// medium size with nothing set the two are alike, and the scale is exactly 1.
public final class TextScaleMeter {

    /// Letters, digits and spaces, in proportions like those of the text in a grid.
    static final String REFERENCE_TEXT = "The quick brown fox jumps over the lazy dog 2026-01-01 12:00:00";

    /// Ordinary text at the medium text size: 0.95rem of a root that is 0.95rem of 16px (`font.css`,
    /// `--font-size-13`).
    static final String MEDIUM_TEXT_SIZE = "14.44px";

    /// The event fired at the page's root element when the scale changes, for what has drawn widths
    /// at the old one (e.g. `DataGrid`'s columns).
    public static final String CHANGE_EVENT = "stroom-text-scale-change";

    /// The longest to wait for the text's font to load, in milliseconds.
    private static final int FONT_WAIT_MILLIS = 3000;

    // The installed meter: its update, and the font its text is drawn in
    private static JavaScriptObject meter;

    private TextScaleMeter() {
        // Static methods only
    }

    /// Start measuring, once for the page. Call it before any grid is made, so that the first
    /// columns are drawn at the measured scale.
    public static void install() {
        if (meter == null) {
            meter = installNative(REFERENCE_TEXT, MEDIUM_TEXT_SIZE, CHANGE_EVENT);
        }
    }

    /// Run something once the page's text font has loaded and the scale has been measured in it
    /// (or after [#FONT_WAIT_MILLIS] if it doesn't load), so that what it draws and checks sees the
    /// scale the page settles at, not one measured in a fallback font. Installs the meter first.
    ///
    /// @param then What to run.
    public static void whenMeasured(final Runnable then) {
        install();
        whenMeasuredNative(meter, then, FONT_WAIT_MILLIS);
    }

    private static native void whenMeasuredNative(JavaScriptObject meter, Runnable then, int waitMillis) /*-{
        var done = false;
        var run = $entry(function () {
            if (!done) {
                done = true;
                meter.update();
                then.@java.lang.Runnable::run()();
            }
        });
        var fonts = $doc.fonts;
        if (!fonts || !fonts.load) {
            run();
            return;
        }
        fonts.load(meter.font()).then(run, run);
        $wnd.setTimeout(run, waitMillis);
    }-*/;

    private static native JavaScriptObject installNative(String text,
                                                         String mediumSize,
                                                         String changeEvent) /*-{
        var doc = $doc;
        var meter = doc.createElement('div');
        meter.className = 'text-scale-meter';
        meter.setAttribute('aria-hidden', 'true');
        var drawn = doc.createElement('span');
        drawn.className = 'text-scale-meter__text';
        drawn.textContent = text;
        var medium = doc.createElement('span');
        medium.className = 'text-scale-meter__medium';
        medium.textContent = text;
        // Inline and important, so that no style sheet (a user's spacing included) reaches them
        medium.style.setProperty('font-size', mediumSize, 'important');
        medium.style.setProperty('letter-spacing', 'normal', 'important');
        medium.style.setProperty('word-spacing', 'normal', 'important');
        meter.appendChild(drawn);
        meter.appendChild(medium);
        doc.body.appendChild(meter);

        var current = null;
        var update = function () {
            var html = doc.documentElement;
            // The preference's own scale, without the one set here
            html.style.removeProperty('--text-scale');
            var preference = @stroom.widget.datagrid.client.model.TextScale::parse(Ljava/lang/String;)(
                $wnd.getComputedStyle(html).getPropertyValue('--text-scale'));
            var scale = @stroom.widget.datagrid.client.model.TextScale::measured(DDD)(
                drawn.getBoundingClientRect().width, medium.getBoundingClientRect().width, preference);
            html.style.setProperty('--text-scale', String(scale));
            if (current !== null && current !== scale) {
                html.dispatchEvent(new $wnd.CustomEvent(changeEvent));
            }
            current = scale;
        };
        update();
        if ($wnd.ResizeObserver) {
            var observer = new $wnd.ResizeObserver($entry(update));
            observer.observe(drawn);
            observer.observe(medium);
        }
        return {
            update: update,
            font: function () {
                return $wnd.getComputedStyle(drawn).font;
            }
        };
    }-*/;
}
