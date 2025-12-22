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

package stroom.editor.client.view;

import stroom.editor.client.presenter.Option;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.view.GlobalResizeObserver;

import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.SimplePanel;
import com.gwtplatform.mvp.client.ViewImpl;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public abstract class AbstractEditorViewImpl extends ViewImpl {

    private static final boolean SHOW_INDICATORS_DEFAULT = false;

    protected final Editor editor;
    protected AceEditorMode mode = AceEditorMode.XML;
    private boolean isVimPreferredKeyBinding = false;
    private boolean liveAutoCompletePreference = false;
    protected Option stylesOption;
    protected Option lineNumbersOption;
    protected Option indicatorsOption;
    protected Option lineWrapOption;
    protected Option showIndentGuides;
    protected Option showInvisiblesOption;
    protected Option basicAutoCompletionOption;
    protected Option snippetsOption;
    protected Option highlightActiveLineOption;
    protected Option viewAsHexOption;
    protected Option useVimBindingsOption;
    protected Option liveAutoCompletionOption;

    public AbstractEditorViewImpl() {
        editor = new Editor();
        editor.getElement().setClassName("editor " + NullSafe.string(getAdditionalClassNames()));
    }

    protected void initOptions() {
        // Don't forget to add any new options into EditorPresenter
        stylesOption = new Option(
                "Styles", true, true, (on) -> setMode(mode, on));
        lineNumbersOption = new Option(
                "Line Numbers", true, true, (on) -> editor.setShowGutter(on));
        indicatorsOption = new Option(
                "Indicators", SHOW_INDICATORS_DEFAULT, false, this::doLayout);
        lineWrapOption = new Option(
                "Wrap Lines", false, true, (on) -> editor.setUseWrapMode(on));
        showIndentGuides = new Option(
                "Show Indent Guides", true, true, (on) -> editor.setShowIndentGuides(on));
        showInvisiblesOption = new Option(
                "Show Hidden Characters", false, true, (on) -> editor.setShowInvisibles(on));
        useVimBindingsOption = buildVimBindingsOption(false);
        basicAutoCompletionOption = new Option(
                "Auto Completion", true, true, (on) -> editor.setUseBasicAutoCompletion(on));
        liveAutoCompletionOption = buildLiveAutoCompleteOption(false);
        snippetsOption = new Option(
                "Snippets", true, true, (on) -> editor.setUseSnippets(on));
        highlightActiveLineOption = new Option(
                "Highlight Active Line", true, true, (on) -> editor.setHighlightActiveLine(on));
        viewAsHexOption = new Option("View as Hex", false, false, null);
    }

    protected SimplePanel createResizablePanel() {
        return new SimplePanel() {
            @Override
            protected void onAttach() {
                super.onAttach();
                GlobalResizeObserver.addListener(getElement(), element -> onResize());
            }

            @Override
            public void onBrowserEvent(final Event event) {
                super.onBrowserEvent(event);
            }

            @Override
            protected void onDetach() {
                GlobalResizeObserver.removeListener(getElement());
                super.onDetach();
            }
        };
    }

    public String getEditorId() {
        return editor.getId();
    }

    public void focus() {
        editor.focus();
    }

    public String getText() {
        return editor.getText();
    }

    public void setText(final String text) {
        editor.setText(NullSafe.string(text));
    }

    public boolean isClean() {
        return editor.isClean();
    }

    public void markClean() {
        editor.markClean();
    }

    public void insertTextAtCursor(final String text) {
        editor.insertTextAtCursor(text);
    }

    public void replaceSelectedText(final String text) {
        editor.replaceSelectedText(text);
    }

    public void insertSnippet(final String snippet) {
        editor.insertSnippet(snippet);
    }

    public void setMode(final AceEditorMode mode) {
        this.mode = mode;
        if (stylesOption.isOn()) {
            editor.setMode(mode);
        } else {
            editor.setMode(AceEditorMode.TEXT);
        }
    }

    public void setMode(final AceEditorMode mode, final boolean areStylesEnabled) {
        this.mode = mode;
        if (areStylesEnabled) {
            editor.setMode(mode);
        } else {
            editor.setMode(AceEditorMode.TEXT);
        }
    }

    public void setTheme(final AceEditorTheme theme) {
        editor.setTheme(theme);
    }

    public void setUserKeyBindingsPreference(final boolean useVimBindings) {
        this.isVimPreferredKeyBinding = useVimBindings;
        this.useVimBindingsOption = buildVimBindingsOption(useVimBindings);
        // Option overrides user preference so even if the user prefers vim (why wouldn't he/she?)
        // they can temporarily disable it
        editor.setUseVimBindings(getUseVimBindingsOption().isOn());
    }

    public void setUserLiveAutoCompletePreference(final boolean isOn) {
        this.liveAutoCompletePreference = isOn;
        this.liveAutoCompletionOption = buildLiveAutoCompleteOption(isOn);
        // Option overrides user preference so even if the user prefers vim (why wouldn't he/she?)
        // they can temporarily disable it
        editor.setUseLiveAutoCompletion(getLiveAutoCompletionOption().isOn());
    }

    protected Option buildVimBindingsOption(final boolean useVimBindings) {
        return new Option(
                "Vim Key Bindings",
                useVimBindings,
                true,
                (on) -> editor.setUseVimBindings(on));
    }

    protected Option buildLiveAutoCompleteOption(final boolean isOn) {
        return new Option(
                "Live Auto Completion",
                isOn,
                true,
                (on) -> editor.setUseLiveAutoCompletion(on));
    }

    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return editor.addKeyDownHandler(handler);
    }

    public HandlerRegistration addKeyUpHandler(final KeyUpHandler handler) {
        return editor.addKeyUpHandler(handler);
    }

    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return editor.addValueChangeHandler(handler);
    }

    public HandlerRegistration addMouseDownHandler(final MouseDownHandler handler) {
        return getContentPanel().addHandler(handler, MouseDownEvent.getType());
    }

    public void fireEvent(final GwtEvent<?> event) {
        getContentPanel().fireEvent(event);
    }

    public void onResize() {
        doLayout();
    }

    protected void doLayout() {
        doLayout(indicatorsOption.isOn());
    }

    protected void doLayout(final boolean showIndicators) {
        editor.onResize();
    }

    protected String getAdditionalClassNames() {
        return "";
    }

    public Option getUseVimBindingsOption() {
        return useVimBindingsOption;
    }

    public Option getLiveAutoCompletionOption() {
        return liveAutoCompletionOption;
    }

    public void setReadOnly(final boolean readOnly) {
        editor.setReadOnly(readOnly);
    }

    public Editor getEditor() {
        return editor;
    }

    abstract SimplePanel getContentPanel();


    // --------------------------------------------------------------------------------


}
