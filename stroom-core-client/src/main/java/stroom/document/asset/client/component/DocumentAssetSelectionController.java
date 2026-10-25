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

package stroom.document.asset.client.component;

import stroom.docstore.shared.AbstractDoc;
import stroom.widget.component.client.Controller;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;
import java.util.function.Consumer;

/// Picks one of a document's assets (e.g. an image) with a [DropDownBox]: the box shows the asset's
/// path ("None" for none), and asks for the "Choose Asset" dialog ([DocumentAssetChooserController]),
/// which this shows.
///
/// ```java
/// // A template declares the box; its screen's controller attaches one of these to it
/// assetSelection = make(documentAssetSelectionProvider).attach(view.getAssetBox());
/// ```
public class DocumentAssetSelectionController extends Controller {

    private static final String NONE = "None";

    private final Provider<DocumentAssetChooserController> chooserProvider;
    private DropDownBox box;
    private AbstractDoc document;
    private String selectedAssetPath;
    private boolean enabled = true;
    private Consumer<String> onChange;

    /// Made by GIN; [#attach(DropDownBox)] gives it its box.
    ///
    /// @param chooserProvider Makes the dialog, for one use.
    @Inject
    public DocumentAssetSelectionController(final Provider<DocumentAssetChooserController> chooserProvider) {
        this.chooserProvider = chooserProvider;
    }

    /// Attaches this to the box whose requests to open the dialog it answers.
    ///
    /// @param box The box, e.g. one a template declares.
    /// @return This, for its owner to keep.
    public DocumentAssetSelectionController attach(final DropDownBox box) {
        this.box = Objects.requireNonNull(box, "box");
        box.setOnOpen(this::showPopup);
        setSelectedAssetPath(selectedAssetPath);
        return this;
    }

    /// @param document The document whose assets can be picked.
    public void setDocument(final AbstractDoc document) {
        this.document = document;
    }

    /// @return The path of the asset picked, or null (or empty) for none.
    public String getSelectedAssetPath() {
        return selectedAssetPath;
    }

    /// Shows an asset as picked.
    ///
    /// @param selectedAssetPath Its path, or null or empty for none.
    public void setSelectedAssetPath(final String selectedAssetPath) {
        this.selectedAssetPath = selectedAssetPath;
        if (box != null) {
            box.setText(selectedAssetPath == null || selectedAssetPath.isEmpty()
                    ? NONE
                    : selectedAssetPath);
        }
    }

    /// Sets whether an asset can be picked; the box is disabled when it can't.
    ///
    /// @param enabled Whether it can.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (box != null) {
            box.setEnabled(enabled);
        }
    }

    /// Sets what is told when the user picks another asset.
    ///
    /// @param onChange Given its path.
    public void setOnChange(final Consumer<String> onChange) {
        this.onChange = onChange;
    }

    private void showPopup() {
        if (enabled && document != null) {
            chooserProvider.get().choose(this, document, url -> {
                if (!Objects.equals(selectedAssetPath, url)) {
                    setSelectedAssetPath(url);
                    if (onChange != null) {
                        onChange.accept(url);
                    }
                }
            });
        }
    }
}
