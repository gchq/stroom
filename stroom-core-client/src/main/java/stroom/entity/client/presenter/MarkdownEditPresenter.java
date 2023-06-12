/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.ChangeThemeEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter.MarkdownEditView;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.presenter.IFramePresenter.SandboxOption;
import stroom.preferences.client.UserPreferencesManager;
import stroom.svg.client.SvgImages;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.Themes;
import stroom.ui.config.shared.Themes.ThemeType;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Collections;
import java.util.List;

public class MarkdownEditPresenter
        extends MyPresenterWidget<MarkdownEditView>
        implements HasDirtyHandlers, HasToolbar {

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-light-theme
    // selectivity added into to stop it conflicting with the dark theme.
    private static final String PRISM_LIGHT_THEME_CLASS_NAME = "stroom-light-theme";
    private static final String MARKDOWN_FRAME_ID = "markdown-frame";

    private final EditorPresenter codePresenter;
    private final IFramePresenter iFramePresenter;
    private final UiConfigCache uiConfigCache;
    private final InlineSvgToggleButton editModeButton;
    private final ButtonView helpButton;
    private final ButtonPanel toolbar;
    private final UserPreferencesManager userPreferencesManager;
    private boolean reading;
    private boolean readOnly = true;
    private boolean editMode;
    // We need to hold onto this in case the user changes the theme
//    private String rawMarkdown = "";

    @Inject
    public MarkdownEditPresenter(final EventBus eventBus,
                                 final MarkdownEditView view,
                                 final EditorPresenter editorPresenter,
                                 final IFramePresenter iFramePresenter,
                                 final UiConfigCache uiConfigCache,
                                 final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view);
        this.codePresenter = editorPresenter;
        this.iFramePresenter = iFramePresenter;
        this.uiConfigCache = uiConfigCache;
        this.userPreferencesManager = userPreferencesManager;
//        htmlPresenter.getWidget().addStyleName("markdown");
        codePresenter.setMode(AceEditorMode.MARKDOWN);

//        iFramePresenter.setUrl("markdown-preview.html");
        iFramePresenter.setId(MARKDOWN_FRAME_ID);

//        view.setView(htmlPresenter.getView());
        view.setView(iFramePresenter.getView());

        iFramePresenter.setSandboxEnabled(true, SandboxOption.ALLOW_POPUPS);

        editModeButton = new InlineSvgToggleButton();
        editModeButton.setSvg(SvgImages.EDIT);
        editModeButton.setTitle("Edit");
        editModeButton.setEnabled(true);

        toolbar = new ButtonPanel();
        toolbar.addButton(editModeButton);
        helpButton = toolbar.addButton(SvgPresets.HELP.title("Documentation help"));

        registerHandler(eventBus.addHandler(ChangeThemeEvent.getType(), event -> {
//            updatePrismTheme();
//            final String html = getMarkdownHtmlElement().getInnerHTML();
//            GWT.log("--------------------------------------------------------");
//            GWT.log("html: " + html);
//            setMarkdownOnIFramePresenter(html);
            updateMarkdownOnIFramePresenter();
        }));
    }

    @Override
    public List<Widget> getToolbars() {
        if (readOnly) {
            return Collections.emptyList();
        }
        return Collections.singletonList(toolbar);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(editModeButton.addClickHandler(e -> {
            if (!readOnly) {
                this.editMode = !this.editMode;
                if (editMode) {
                    getView().setView(codePresenter.getView());
                } else {
                    // Raw markdown likely been changed to update the iframe cotent
                    updateMarkdownOnIFramePresenter();
                    getView().setView(iFramePresenter.getView());
                }
            }
        }));
        registerHandler(helpButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                showHelp();
            }
        }));
    }

    private void setDirty(final boolean dirty) {
        if (!reading && !readOnly) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public String getText() {
        return codePresenter.getText();
    }

    public void setText(String rawMarkdown) {
        if (rawMarkdown == null) {
            rawMarkdown = "";
        }

        reading = true;
        codePresenter.setText(rawMarkdown);
        reading = false;
        updateMarkdownOnIFramePresenter();
    }

    private void showHelp() {
        uiConfigCache.get()
                .onSuccess(result -> {
                    final String helpUrl = result.getHelpUrlDocumentation();
                    if (helpUrl != null && helpUrl.trim().length() > 0) {
                        Window.open(helpUrl, "_blank", "");
                    } else {
                        AlertEvent.fireError(
                                MarkdownEditPresenter.this,
                                "Help is not configured!",
                                null);
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(
                        MarkdownEditPresenter.this,
                        caught.getMessage(),
                        null));
    }

//    private String setMarkdownOnHtmlPresenter(final String rawMarkdown) {
//        this.rawMarkdown = rawMarkdown == null
//                ? ""
//                : rawMarkdown;
////        updatePrismTheme();
//        // Convert raw mark
////        DOM.createDiv()
//        final String html = convertMarkdownToHtml(rawMarkdown);
////        markdownElement.setInnerHTML(htmlBeforePrism);
////        htmlPresenter.setHtml();
////        applyPrismSyntaxHighlighting();
//
//        return html;
////        return getMarkdownHtmlElement().getInnerHTML();
////        return htmlPresenter.getView().asWidget().getElement().getInnerHTML();
//    }

//    private void refreshMarkdownOnIFramePresenter() {
//        setMarkdownOnIFramePresenter(rawMarkdown);
//    }

    private void updateMarkdownOnIFramePresenter() {
        final String rawMarkdown = codePresenter.getText();
        final String html = convertMarkdownToHtml(rawMarkdown == null
                ? ""
                : rawMarkdown);
//        final String html = setMarkdownOnHtmlPresenter(rawMarkdown);
        // <iframe src="data:text/html;base64,BASE64_GOES_HERE" sandbox=""></iframe>

        final String theme = userPreferencesManager.getCurrentPreferences().getTheme();
        final String themeClass = Themes.getClassName(theme);
        final String prismThemeClassName = getPrismThemeClassName();

//        GWT.log("theme: " + theme + " themeClass: " + themeClass + " prismThemeClassName: " + prismThemeClassName);
//        GWT.log("html: " + html);
//        GWT.log("--------------------------------------------------------------------------------");

        final SafeHtml safeHtml = new SafeHtmlBuilder()
                .appendHtmlConstant("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .appendHtmlConstant("<!DOCTYPE html>")
                .appendHtmlConstant("<html onload=\"console.log('xxxxxxxxxx')\" class=\"")
                .appendEscaped(themeClass)
                .appendHtmlConstant("\">")
                .appendHtmlConstant("<head>")
                .appendHtmlConstant("<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">")
                .appendHtmlConstant("<link rel=\"stylesheet\" href=\"css/app.css\" type=\"text/css\" />")
                .appendHtmlConstant("</head>")
                .appendHtmlConstant("<body>")
                .appendHtmlConstant("<div class=\"max info-page markdown-container markdown ")
                .appendEscaped(prismThemeClassName)
                .appendHtmlConstant("\">")
                .appendHtmlConstant(html)
                .appendHtmlConstant("</div>")
                .appendHtmlConstant("</body>")
                .appendHtmlConstant("</html>")
                        .toSafeHtml();

//        final String base64Html = base64encode(safeHtml.asString());

//        final String base64Html = BaseEncoding.base64().encode(safeHtml.asString().getBytes(StandardCharsets.UTF_8));
//        final String base64Html = Base64Utils.toBase64(safeHtml.asString().getBytes(StandardCharsets.UTF_8));

//        iFramePresenter.setUrl("data:text/html;base64," + base64Html);
//        GWT.log("html: " + safeHtml.asString());

//        setHtmlInIFrame(html, MARKDOWN_FRAME_ID);
//        GWT.log("Setting srcdoc");
        iFramePresenter.setSrcDoc(safeHtml.asString());
//        GWT.log("Done setting srcdoc");

//        final Document document = Window.doc
//        final HeadElement headElement = document.createHeadElement();
//        document.crea
//        final ScriptElement scriptElement = document.createScriptElement("script/prismjs/prism.js");
//        final LinkElement linkElement = document.createLinkElement();
//        linkElement.setRel("stylesheet");
//        linkElement.setHref("css/app.css");
//        headElement.appendChild(linkElement);
//        headElement.appendChild(scriptElement);
//
//        document.appendChild(headElement);
//
//        updatePrismTheme();
//        htmlPresenter.setHtml(convertMarkdownToHtml(rawMarkdown));
//        applyPrismSyntaxHighlighting();
    }

//    private Element getMarkdownHtmlElement() {
////        return htmlPresenter.getWidget().getElement();
//        return markdownElement;
//    }

//    private boolean updatePrismTheme() {
//        // We have two prism css files, each with a baked in theme. The standard
//        // un-edited one is a dark theme. The non-standard one that adds in additional
//        // class selectivity is for light themes.
//        final ThemeType themeType = userPreferencesManager.geCurrentThemeType();
//        GWT.log("changing theme type to " + themeType);
//        if (!Objects.equals(currentThemeType, themeType)) {
//            switch (themeType) {
//                case DARK:
//                    getMarkdownHtmlElement().removeClassName(PRISM_LIGHT_THEME_CLASS_NAME);
//                    break;
//                case LIGHT:
//                    getMarkdownHtmlElement().addClassName(PRISM_LIGHT_THEME_CLASS_NAME);
//                    break;
//                default:
//                    throw new RuntimeException("Unexpected theme type " + themeType);
//            }
//            currentThemeType = themeType;
//            return true;
//        } else {
//            return false;
//        }
//    }

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

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * Use ShowdownJS to convert markdown text into HTML. Enable support for
     * markdown tables.
     */
    public native String convertMarkdownToHtml(final String rawMarkdown) /*-{
        // See https://showdownjs.com/docs/available-options/
        var converter = new $wnd.showdown.Converter({
            openLinksInNewWindow: true,
            strikethrough: true,
            tables: true,
            tasklists: true,
        });
        var markdownAsHtml = converter.makeHtml(rawMarkdown);

        // Only need to involve prism if showdown has set a language-XXX class
        if (/language-/.test(markdownAsHtml)) {
            // Transient div just so we can tokenise any code blocks into lots of spans with
            // appropriate classes for the syntax highlight type.
            var markdownDiv = $doc.createElement("div");
            markdownDiv.innerHTML = markdownAsHtml;
            $wnd.Prism.highlightAllUnder(markdownDiv);
            markdownAsHtml = markdownDiv.innerHTML;
        }
        return markdownAsHtml;
    }-*/;

    /**
     * This will run the prismjs code to tokenise the content of fenced blocks which
     * are in within pre/code. ShowdownJS will have set the appropriate language class on the
     * code tag so then the prism css can style all the tokens.
     */
//    public native void applyPrismSyntaxHighlighting() /*-{
//        var element = this.@stroom.entity.client.presenter.MarkdownEditPresenter::getMarkdownHtmlElement()();
//        $wnd.Prism.highlightAllUnder(element);
//    }-*/;

//    public native void setHtmlInIFrame(final String frameId, final String html) /*-{
//        var markdownFrame = $doc.getElementById(frameId);
//        if (markdownFrame) {
//            markdownFrame.contentWindow.postMessage(html, '*');
//        }
//    }-*/;

//    private static native String base64encode(String plainText) /*-{
//      return window.btoa(plainText);
//    }-*/;

    // --------------------------------------------------------------------------------


    public interface MarkdownEditView extends View {

        void setView(View view);
    }
}
