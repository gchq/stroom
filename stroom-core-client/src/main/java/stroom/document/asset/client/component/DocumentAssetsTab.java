/*
 * Copyright 2016 Crown Copyright
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

package stroom.document.asset.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/// A document's Assets tab (its images, CSS, scripts and so on): the tree of files beside the
/// selected one's editor, or a message in its place while no file is selected. Its buttons go on
/// the document's toolbar ([#getToolbar()]), outside the tab.
public class DocumentAssetsTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DocumentAssetTree tree;
    @UiField
    Label editorPlaceholder;
    @UiField
    CodeEditor editor;

    // The tab's buttons, for its document's toolbar
    private final Toolbar toolbar = new Toolbar("Asset actions");
    private final Map<AssetAction, ToolbarButton> buttons = new EnumMap<>(AssetAction.class);
    private Consumer<AssetAction> onAction = action -> {
    };

    /// Makes the tab, with no files and its buttons.
    public DocumentAssetsTab() {
        initWidget(BINDER.createAndBindUi(this));
        addButton(AssetAction.REVERT, SvgImage.REFRESH, "Revert Asset Changes");
        addButton(AssetAction.ADD, SvgImage.ADD, "Add File or Folder").setOpensMenu(true);
        addButton(AssetAction.DELETE, SvgImage.DELETE, "Delete Selected Asset");
        addButton(AssetAction.RENAME, SvgImage.EDIT, "Rename Selected Asset");
        addButton(AssetAction.VIEW, SvgImage.EYE, "View Selected Asset in Browser (opens in new tab)");
    }

    private ToolbarButton addButton(final AssetAction action, final SvgImage icon, final String name) {
        final ToolbarButton button = toolbar.button(icon, name, () -> onAction.accept(action));
        buttons.put(action, button);
        return button;
    }

    /// @return The tree of files, which its controller fills.
    public DocumentAssetTree getTree() {
        return tree;
    }

    /// @return The editor of the selected file, which its controller attaches.
    public CodeEditor getEditor() {
        return editor;
    }

    /// @return The tab's buttons, for its document's toolbar.
    public Toolbar getToolbar() {
        return toolbar;
    }

    /// Shows the editor, or in its place a message asking for a file to be selected.
    ///
    /// @param visible Whether the editor is shown.
    public void setEditorVisible(final boolean visible) {
        editor.setVisible(visible);
        editorPlaceholder.setVisible(!visible);
    }

    /// @param action  One of the buttons.
    /// @param enabled Whether it can be pressed.
    public void setActionEnabled(final AssetAction action, final boolean enabled) {
        buttons.get(action).setEnabled(enabled);
    }

    /// @param action One of the buttons.
    /// @return Its element, e.g. to show a menu beside.
    public Element getActionElement(final AssetAction action) {
        return buttons.get(action).getElement();
    }

    /// @param onAction Given the action of the button pressed.
    public void setOnAction(final Consumer<AssetAction> onAction) {
        this.onAction = onAction;
    }


    // --------------------------------------------------------------------------------


    /// The tab's buttons.
    public enum AssetAction {
        /// Throws away the draft changes, back to the saved assets.
        REVERT,
        /// Adds a file or folder, or uploads a file, from a menu.
        ADD,
        /// Deletes the selected file or folder.
        DELETE,
        /// Renames the selected file or folder.
        RENAME,
        /// Opens the selected file in a new browser tab.
        VIEW
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentAssetsTab> {

    }
}
