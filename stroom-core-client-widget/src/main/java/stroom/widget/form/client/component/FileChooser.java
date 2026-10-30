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

package stroom.widget.form.client.component;

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FileUploadUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.HTMLPanel;

/// Chooses a file to upload: a button that opens the browser's file dialog, and the name of the
/// chosen file beside it. The BEM block `file-chooser` (`file-chooser.css`), its markup
/// `FileChooser.ui.xml`. A [FileChooserController] attached to it uploads the file.
///
/// In a form group the button is named by the group's label and its own text (e.g. "File, Choose
/// File"), as a picker is, and described by the chosen file's name, which is announced when it
/// changes.
public class FileChooser extends Component {

    private static final String NO_FILE = "No file chosen";
    private static final String NO_FILE_MODIFIER = "file-chooser__name--empty";
    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Button chooseFile;
    @UiField
    SpanElement fileName;
    @UiField
    InputElement fileInput;

    private Runnable onChange;

    /// Makes a chooser with no file chosen.
    public FileChooser() {
        initWidget(BINDER.createAndBindUi(this));
        // Described by the chosen file, which needs an id of its own
        final String fileNameId = DOM.createUniqueId();
        fileName.setId(fileNameId);
        FieldValidity.addDescribedBy(chooseFile.getElement(), fileNameId, false);
        chooseFile.setOnClick(fileInput::click);
        // The file input's change bubbles to the chooser
        addDomHandler(event -> {
            showFileName();
            if (onChange != null) {
                onChange.run();
            }
        }, ChangeEvent.getType());
    }

    /// Gets the name of the chosen file, without the (fake) path that browsers give a file input's
    /// value, e.g. `stream.txt` for `C:\fakepath\stream.txt`.
    ///
    /// @return The name of the chosen file, or an empty string if no file has been chosen.
    public String getFilename() {
        return FileUploadUtil.getFileName(fileInput.getValue());
    }

    /// @param onChange Run when the user chooses a file.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// Puts the focus on the button.
    public void focus() {
        chooseFile.setFocus(true);
    }

    /// @return The file input holding the chosen file, which its controller uploads.
    Element getFileInput() {
        return fileInput;
    }

    private void showFileName() {
        final String name = getFilename();
        final boolean none = name.isEmpty();
        fileName.setInnerText(none
                ? NO_FILE
                : name);
        if (none) {
            fileName.addClassName(NO_FILE_MODIFIER);
        } else {
            fileName.removeClassName(NO_FILE_MODIFIER);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<HTMLPanel, FileChooser> {

    }
}
