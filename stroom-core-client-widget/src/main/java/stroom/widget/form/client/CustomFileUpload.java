/*
 * Copyright 2025 Crown Copyright
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

package stroom.widget.form.client;

import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResourceKey;
import stroom.widget.button.client.Button;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.annotations.IsSafeUri;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// Chooses a file to upload: a button that opens the browser's file dialog, and the name of the
/// chosen file beside it. The BEM block `file-upload` (`file-upload.css`): its button
/// (`file-upload__button`), the file's name (`file-upload__name`) and the hidden file input
/// (`file-upload__input`).
///
/// In a form group the button is named by the group's label and its own text (e.g. "File, Choose
/// File"), as a picker is, and described by the chosen file's name, which is announced when it
/// changes.
public class CustomFileUpload extends Composite {

    private static final String NO_FILE = "No file chosen";
    private static final String NO_FILE_MODIFIER = "file-upload__name--empty";
    private static final Binder BINDER = GWT.create(Binder.class);
    private static final FileUploadTransport DEFAULT_TRANSPORT = FileUploadSubmitter::submit;
    private static FileUploadTransport uploadTransport = DEFAULT_TRANSPORT;
    private final Widget widget;

    @UiField
    Button chooseFile;
    @UiField
    Label fileName;
    @UiField
    FileUpload fileUpload;

    private String actionUrl;
    private final FileUploadResultHandler resultHandler = new FileUploadResultHandler();

    private CustomFileUpload() {
        widget = BINDER.createAndBindUi(this);
        fileUpload.setVisible(false);
        // A picker, to a form group, which names it by its label and its own text
        chooseFile.getElement().setAttribute("role", "button");
        chooseFile.getElement().setAttribute("aria-haspopup", "dialog");
        // Described by the chosen file, which is announced when it changes
        final String fileNameId = DOM.createUniqueId();
        fileName.getElement().setId(fileNameId);
        fileName.getElement().setAttribute("aria-live", "polite");
        FieldValidity.addDescribedBy(chooseFile.getElement(), fileNameId, false);
        showFileName();
        initWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    /// Gets the name of the chosen file, without the (fake) path that browsers give a file input's
    /// value, e.g. `stream.txt` for `C:\fakepath\stream.txt`.
    ///
    /// @return The name of the chosen file, or an empty string if no file has been chosen.
    public String getFilename() {
        return FileUploadUtil.getFileName(fileUpload.getFilename());
    }

    /**
     * Set the consumer called with the {@link ResourceKey} of the uploaded file when an upload
     * triggered by {@link #submit()} succeeds.
     */
    public CustomFileUpload onSuccess(final Consumer<ResourceKey> successConsumer) {
        resultHandler.onSuccess(successConsumer);
        return this;
    }

    /**
     * Set the consumer called with an error message when an upload triggered by {@link #submit()}
     * fails.
     */
    public CustomFileUpload onFailure(final Consumer<String> failureConsumer) {
        resultHandler.onFailure(failureConsumer);
        return this;
    }

    /**
     * Set the task monitor factory used to indicate progress for the duration of the upload.
     */
    public CustomFileUpload taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory,
                                               final String taskMessage) {
        resultHandler.taskMonitorFactory(taskMonitorFactory, taskMessage);
        return this;
    }

    public void setAction(@IsSafeUri final String url) {
        this.actionUrl = url;
    }

    public void setAction(final SafeUri url) {
        this.actionUrl = url.asString();
    }

    public void submit() {
        // By default this uploads via XMLHttpRequest rather than a native form submission so we
        // can attach the X-CSRF header that the server requires for session-authenticated requests.
        resultHandler.onUploadStart();
        uploadTransport.upload(actionUrl, fileUpload.getElement(), resultHandler);
    }

    /// Sets how every [CustomFileUpload] sends its file, e.g. to fake uploads in tests.
    ///
    /// @param transport The transport, or null for Stroom's default, which uploads the file with
    ///                  an `XMLHttpRequest`.
    public static void setUploadTransport(final FileUploadTransport transport) {
        uploadTransport = transport != null
                ? transport
                : DEFAULT_TRANSPORT;
    }

    public void focus() {
        chooseFile.setFocus(true);
    }

    @UiHandler("chooseFile")
    void onChooseFile(final ClickEvent e) {
        fileUpload.click();
    }

    @UiHandler("fileUpload")
    void onFileUpload(final ChangeEvent e) {
        showFileName();
    }

    private void showFileName() {
        final String name = getFilename();
        final boolean none = name.isEmpty();
        fileName.setText(none
                ? NO_FILE
                : name);
        fileName.setStyleName(NO_FILE_MODIFIER, none);
    }

    public interface Binder extends UiBinder<Widget, CustomFileUpload> {

    }
}
