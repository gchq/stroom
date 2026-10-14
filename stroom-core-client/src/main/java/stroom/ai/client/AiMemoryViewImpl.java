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

package stroom.ai.client;

import stroom.ai.client.AiMemoryPresenter.AiMemoryView;
import stroom.widget.button.client.Button;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class AiMemoryViewImpl extends ViewWithUiHandlers<AiMemoryUiHandlers> implements AiMemoryView {

    private static final String UNDO_ATTRIBUTE = "data-undo-memory";

    private final Widget widget;

    @UiField
    ListBox memories;
    @UiField
    TextArea content;
    @UiField
    Button newMemory;
    @UiField
    Button save;
    @UiField
    Button delete;
    @UiField
    Button clearUser;
    @UiField
    HTML changes;

    @Inject
    public AiMemoryViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        changes.addClickHandler(event -> {
            final Element target = event.getNativeEvent().getEventTarget().cast();
            final String messageId = target.getAttribute(UNDO_ATTRIBUTE);
            if (messageId != null && !messageId.isEmpty() && getUiHandlers() != null) {
                getUiHandlers().onUndoChange(Integer.parseInt(messageId));
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setMemories(final List<String> labels, final int selectedIndex) {
        memories.clear();
        labels.forEach(memories::addItem);
        memories.setSelectedIndex(selectedIndex);
    }

    @Override
    public void setContent(final String text, final boolean editable) {
        content.setText(text);
        content.setEnabled(editable);
        save.setEnabled(editable);
        delete.setEnabled(editable);
    }

    @Override
    public String getContent() {
        return content.getText();
    }

    @Override
    public void setChanges(final SafeHtml html) {
        changes.setHTML(html);
    }

    @UiHandler("memories")
    public void onMemories(final ChangeEvent event) {
        if (getUiHandlers() != null && memories.getSelectedIndex() >= 0) {
            getUiHandlers().onMemorySelected(memories.getSelectedIndex());
        }
    }

    @UiHandler("newMemory")
    public void onNewMemory(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onNewMemory();
        }
    }

    @UiHandler("save")
    public void onSave(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSaveMemory();
        }
    }

    @UiHandler("delete")
    public void onDelete(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDeleteMemory();
        }
    }

    @UiHandler("clearUser")
    public void onClearUser(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onClearUserMemory();
        }
    }

    public interface Binder extends UiBinder<Widget, AiMemoryViewImpl> {

    }
}
