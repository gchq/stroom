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

import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.ChangeThemeEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter.MarkdownEditView;
import stroom.preferences.client.UserPreferencesManager;
import stroom.svg.client.SvgImages;
import stroom.ui.config.shared.Themes.ThemeType;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgToggleButton;

import com.google.gwt.dom.client.Element;
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

    private static final String PRISM_LIGHT_THEME_CLASS_NAME = "stroom-light-theme";

    private final EditorPresenter codePresenter;
    private final HtmlPresenter htmlPresenter;
    private final InlineSvgToggleButton editModeButton;
    private boolean reading;
    private boolean readOnly = true;
    private boolean editMode;
    private final ButtonPanel toolbar;
    private final Element htmlPresenterElement;
    private final UserPreferencesManager userPreferencesManager;


    @Inject
    public MarkdownEditPresenter(final EventBus eventBus,
                                 final MarkdownEditView view,
                                 final EditorPresenter editorPresenter,
                                 final HtmlPresenter htmlPresenter,
                                 final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view);
        this.codePresenter = editorPresenter;
        this.htmlPresenter = htmlPresenter;
        this.userPreferencesManager = userPreferencesManager;
        htmlPresenter.getWidget().addStyleName("markdown");
        htmlPresenterElement = htmlPresenter.getWidget().getElement();
        codePresenter.setMode(AceEditorMode.MARKDOWN);
        view.setView(htmlPresenter.getView());

        editModeButton = new InlineSvgToggleButton();
        editModeButton.setSvg(SvgImages.EDIT);
        editModeButton.setTitle("Edit");
        editModeButton.setEnabled(true);

        toolbar = new ButtonPanel();
        toolbar.addButton(editModeButton);

        registerHandler(eventBus.addHandler(ChangeThemeEvent.getType(), event -> {
            updatePrismTheme();
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
                    setMarkdownOnHtmlPresenter(codePresenter.getText());
                    getView().setView(htmlPresenter.getView());
                }
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
        setMarkdownOnHtmlPresenter(rawMarkdown);

    }

    private void setMarkdownOnHtmlPresenter(final String rawMarkdown) {
        updatePrismTheme();
        htmlPresenter.setHtml(convertMarkdownToHtml(rawMarkdown));
        applyPrismSyntaxHighlighting();
    }

    private void updatePrismTheme() {
        // We have two prism css files, each with a baked in theme. The standard
        // un-edited one is a dark theme. The non-standard one that adds in additional
        // class selectivity is for light themes.
        final ThemeType themeType = userPreferencesManager.geCurrentThemeType();
        switch (themeType) {
            case DARK:
                htmlPresenterElement.removeClassName(PRISM_LIGHT_THEME_CLASS_NAME);
                break;
            case LIGHT:
                htmlPresenterElement.addClassName(PRISM_LIGHT_THEME_CLASS_NAME);
                break;
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
    public native String convertMarkdownToHtml(final String markdown) /*-{
        var converter = new $wnd.showdown.Converter();
        converter.setOption('openLinksInNewWindow', true);
        converter.setOption('strikethrough', true);
        converter.setOption('tables', true);
        converter.setOption('tasklists', true);
        return converter.makeHtml(markdown);
    }-*/;

    /**
     * This will run the prismjs code to tokenise the content of fenced blocks which
     * are in within pre/code. ShowdownJS will have set the appropriate language class on the
     * code tag so then the prism css can style all the tokens.
     */
    public native void applyPrismSyntaxHighlighting() /*-{
        var element = this.@stroom.entity.client.presenter.MarkdownEditPresenter::htmlPresenterElement;
        $wnd.Prism.highlightAllUnder(element);
    }-*/;


    // --------------------------------------------------------------------------------


    public interface MarkdownEditView extends View {

        void setView(View view);
    }
}
