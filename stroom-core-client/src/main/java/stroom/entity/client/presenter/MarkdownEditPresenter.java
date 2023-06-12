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
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter.MarkdownEditView;
import stroom.svg.client.SvgImages;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgToggleButton;

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

    private final EditorPresenter codePresenter;
    private final HtmlPresenter htmlPresenter;
    private final InlineSvgToggleButton editModeButton;
    private boolean reading;
    private boolean readOnly = true;
    private boolean editMode;
    private final ButtonPanel toolbar;

    @Inject
    public MarkdownEditPresenter(final EventBus eventBus,
                                 final MarkdownEditView view,
                                 final EditorPresenter editorPresenter,
                                 final HtmlPresenter htmlPresenter) {
        super(eventBus, view);
        this.codePresenter = editorPresenter;
        this.htmlPresenter = htmlPresenter;
        codePresenter.setMode(AceEditorMode.MARKDOWN);
        view.setView(htmlPresenter.getView());

        editModeButton = new InlineSvgToggleButton();
        editModeButton.setSvg(SvgImages.MONO_EDIT);
        editModeButton.setTitle("Edit");
        editModeButton.setEnabled(true);

        toolbar = new ButtonPanel();
        toolbar.addButton(editModeButton);
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
                    htmlPresenter.setHtml(getHtml(codePresenter.getText()));
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

    public void setText(String text) {
        if (text == null) {
            text = "";
        }

        reading = true;
        codePresenter.setText(text);
        reading = false;

        htmlPresenter.setHtml(getHtml(text));
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    public native String getHtml(final String markdown) /*-{
        var converter = new $wnd.showdown.Converter();
        return converter.makeHtml(markdown);
    }-*/;

    public interface MarkdownEditView extends View {

        void setView(View view);
    }
}
