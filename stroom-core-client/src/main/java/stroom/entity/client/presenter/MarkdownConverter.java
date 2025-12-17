/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.entity.client.presenter;

import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.ThemeType;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class MarkdownConverter {

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-light-theme
    // selectivity added into to stop it conflicting with the dark theme.
    private static final String PRISM_LIGHT_THEME_CLASS_NAME = "stroom-light-theme";

    private final UserPreferencesManager userPreferencesManager;
    private final JavaScriptObject showdownConverter;

    @Inject
    public MarkdownConverter(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
        this.showdownConverter = initConverter();
    }

    /**
     * Converts the supplied markdown into html with appropriate syntax highlighting.
     * The markdown is surrounded by a container div.
     * <p><b>NOTE:</b> if the markdown has come from user content then it cannot be trusted, so
     * you should contain the resulting html in a sandboxed iframe or add other layers of protection
     * from scripting in the markdown.</p>
     */
    public SafeHtml convertMarkdownToHtml(final String rawMarkdown) {
        if (NullSafe.isBlankString(rawMarkdown)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
            appendMarkdownInDiv(htmlBuilder, rawMarkdown);
            return htmlBuilder.toSafeHtml();
        }
    }

    /**
     * Converts the supplied markdown into html with appropriate syntax highlighting
     * The markdown is surrounded by a container div and placed in a sandboxed iFrame for
     * safety from scripts.
     */
    public SafeHtml convertMarkdownToHtmlInFrame(final String rawMarkdown) {
        if (NullSafe.isBlankString(rawMarkdown)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {

            final SafeHtml markdownSafeHtml = convertMarkdownToHtml(rawMarkdown);
            final String currentPreferenceClasses = userPreferencesManager.getCurrentPreferenceClasses();
            final SafeHtml iFrameHtmlContent = new SafeHtmlBuilder()
                    .appendHtmlConstant("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    .appendHtmlConstant("<!DOCTYPE html>")
                    .appendHtmlConstant("<html class=\"")
                    .append(SafeHtmlUtil.from(currentPreferenceClasses))
                    .appendHtmlConstant("\">")
                    .appendHtmlConstant("<head>")
                    .appendHtmlConstant("<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">")
                    .appendHtmlConstant("<link rel=\"stylesheet\" href=\"ui/css/app.css\" type=\"text/css\" />")
                    .appendHtmlConstant("</head>")
                    .appendHtmlConstant("<body>")
                    .appendHtmlConstant("<div class=\"max info-page markdown-container\">")
                    .append(markdownSafeHtml)
                    .appendHtmlConstant("</div>")
                    .appendHtmlConstant("</body>")
                    .appendHtmlConstant("</html>")
                    .toSafeHtml();

            return iFrameHtmlContent;
        }
    }

    /**
     * Converts the supplied markdown into html with appropriate syntax highlighting and
     * appends it to the htmlBuilder as a div with the appropriate classes for markdown rendering.
     * <p><b>NOTE:</b> if the markdown has come from user content then it cannot be trusted, so
     * you should contain the resulting html in a sandboxed iframe or add other layers of protection
     * from scripting in the markdown.</p>
     */
    public void appendMarkdownInDiv(final HtmlBuilder htmlBuilder, final String rawMarkdown) {
        if (htmlBuilder != null && !NullSafe.isBlankString(rawMarkdown)) {
            final String markdownHtml = nativeConvertMarkdownToHtml(rawMarkdown);
            final SafeHtml markdownSafeHtml = SafeHtmlUtils.fromTrustedString(markdownHtml);
            final String cssClasses = getMarkdownContainerClasses();

            htmlBuilder.div(builder -> builder.append(markdownSafeHtml),
                    Attribute.className(cssClasses));
        }
    }

    public ThemeType geCurrentThemeType() {
        return userPreferencesManager.geCurrentThemeType();
    }

    private native JavaScriptObject initConverter() /*-{
        // See https://showdownjs.com/docs/available-options/
        var converter = new $wnd.showdown.Converter({
            openLinksInNewWindow: true,
            strikethrough: true,
            tables: true,
            tasklists: true,
        });

        return converter;
    }-*/;

    public native String nativeConvertMarkdownToHtml(final String rawMarkdown) /*-{
        var converter = this.@stroom.entity.client.presenter.MarkdownConverter::showdownConverter;
        var markdownAsHtml = converter.makeHtml(rawMarkdown);

        // Only need to involve prism if showdown has set a language-XXX class
        if (/language-/.test(markdownAsHtml)) {
            // Transient div just so we can tokenise the existing html
            var markdownDiv = $doc.createElement("div");
            markdownDiv.innerHTML = markdownAsHtml;

            // This will run the prismjs code to tokenise the content of fenced blocks which
            // are within pre/code. It will surround the different bits of syntax with appropriately classed
            // span tags. ShowdownJS will have set the appropriate language class on the
            // code tag so then the prism css can style all the created spans.
            $wnd.Prism.highlightAllUnder(markdownDiv);
            markdownAsHtml = markdownDiv.innerHTML;
        }
        return markdownAsHtml;
    }-*/;

    /**
     * @return The space separated list of css classes to set on the container div that holds
     * the markdown html.
     */
    private String getMarkdownContainerClasses() {
        final String prismThemeClassName = getPrismThemeClassName();
        return "markdown " + prismThemeClassName;
    }

    private String getPrismThemeClassName() {
        final ThemeType themeType = userPreferencesManager.geCurrentThemeType();
        switch (themeType) {
            case DARK:
                return "";
            case LIGHT:
                return PRISM_LIGHT_THEME_CLASS_NAME;
            default:
                throw new RuntimeException("Unexpected theme type " + themeType);
        }
    }
}
