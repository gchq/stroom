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
 */

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.HasSave;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.docref.DocRef;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.receive.rules.client.component.RuleSetEditor;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.ControllerView;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Data Receipt Rules content tab: shows a [RuleSetEditor], labels the tab ("* " while the
/// rules have changes that aren't saved), saves them with Save All, and asks before closing with
/// changes. A thin shell until content tabs are controllers.
public class RuleSetPresenter extends ContentTabPresenter<ControllerView>
        implements CloseContentEvent.Handler, HasSave {

    private static final String TAB_LABEL = "Data Receipt Rules";

    private final RuleSetEditor editor;
    private String lastLabel;

    /// Made by GIN, with its editor, which it owns.
    ///
    /// @param eventBus        The application's event bus.
    /// @param editor          The rules' tabs.
    /// @param hasSaveRegistry What Save All saves.
    @Inject
    public RuleSetPresenter(final EventBus eventBus,
                            final RuleSetEditor editor,
                            final HasSaveRegistry hasSaveRegistry) {
        super(eventBus, new ControllerView(editor));
        // The rules may always be changed: read only only ever narrows from the owner down, so it
        // is said here, above the editor, too
        provide(Contexts.READ_ONLY, ContextValue.of(false));
        this.editor = own(editor);
        editor.setOnDirtyChange(dirty -> updateLabel());
        hasSaveRegistry.register(this);
        // The registry is shared, so it would keep this closed screen (and Save All would save it)
        onDispose(() -> hasSaveRegistry.unregister(this));
        listen(SaveDocumentEvent.getType(), event -> {
            if (event.getTabData() instanceof final HasSave hasSave && hasSave.isDirty()) {
                save();
            }
        });
    }

    @Override
    public boolean isDirty() {
        return editor.isDirty();
    }

    @Override
    public void save() {
        editor.save();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET;
    }

    @Override
    public String getLabel() {
        return isDirty()
                ? "* " + TAB_LABEL
                : TAB_LABEL;
    }

    @Override
    public String getType() {
        return ReceiveDataRules.TYPE;
    }

    @Override
    public DocRef getDocRef() {
        return editor.getDocRef();
    }

    @Override
    public void onCloseRequest(final CloseContentEvent event) {
        final DirtyMode dirtyMode = event.getDirtyMode();
        if (isDirty() && DirtyMode.FORCE != dirtyMode) {
            if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                ConfirmEvent.fire(this,
                        "There are unsaved changes. Are you sure you want to close this tab?",
                        // Closing the tab disposes this
                        result -> event.getCallback().closeTab(result));
            } else if (DirtyMode.SKIP_DIRTY != dirtyMode) {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            event.getCallback().closeTab(true);
        }
    }

    private void updateLabel() {
        // Only when it changes, as the tab is laid out again
        if (!getLabel().equals(lastLabel)) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }
}
