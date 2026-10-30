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

import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.form.client.FileUploadCallback;
import stroom.widget.form.client.FileUploadSubmitter;
import stroom.widget.form.client.FileUploadTransport;
import stroom.widget.task.client.SimpleTask;
import stroom.widget.task.client.Task;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.inject.Inject;

import java.util.Objects;
import java.util.function.Consumer;

/// Uploads the file chosen in the [FileChooser] it is attached to, showing the upload on a task
/// monitor, and gives its owner the key of the uploaded file or the reason it failed. A reply that
/// comes once this is disposed is dropped.
///
/// ```java
/// fileChooser = own(fileChooserController);
/// fileChooser.attach(view.getFileChooser());
/// fileChooser.setTaskMonitorFactory(this, "Uploading Data");
/// ...
/// fileChooser.upload(ImportUtil.getImportFileURL(), this::onUploaded, this::error);
/// ```
public class FileChooserController extends Controller {

    private static final FileUploadTransport DEFAULT_TRANSPORT = FileUploadSubmitter::submit;
    private static FileUploadTransport uploadTransport = DEFAULT_TRANSPORT;

    private FileChooser chooser;
    private TaskMonitorFactory taskMonitorFactory;
    private String taskMessage = "Uploading";

    /// Made by GIN; [#attach(FileChooser)] gives it its chooser.
    @Inject
    public FileChooserController() {
    }

    /// Attaches this to the chooser whose file it uploads.
    ///
    /// @param chooser The chooser, e.g. one a template declares.
    public void attach(final FileChooser chooser) {
        this.chooser = Objects.requireNonNull(chooser, "chooser");
    }

    /// @param taskMonitorFactory What shows the upload, or null for the task monitor of what owns
    ///                           this ([Contexts#TASK_MONITOR]).
    /// @param taskMessage        What the task monitor says while the file uploads.
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory, final String taskMessage) {
        this.taskMonitorFactory = taskMonitorFactory;
        this.taskMessage = taskMessage;
    }

    /// @return The name of the chosen file, or an empty string if no file has been chosen.
    public String getFilename() {
        return chooser.getFilename();
    }

    /// Uploads the chosen file. Stroom's transport sends it with an `XMLHttpRequest` rather than a
    /// form, so that it can carry the `X-CSRF` header the server requires.
    ///
    /// @param url       The upload endpoint, e.g. `ImportUtil.getImportFileURL()`.
    /// @param onSuccess Given the key of the uploaded file. What it throws is its own: it isn't taken
    ///                  as the upload failing (it once was, and reported as an upload error).
    /// @param onFailure Given the reason it failed.
    public void upload(final String url,
                       final Consumer<ResourceKey> onSuccess,
                       final Consumer<String> onFailure) {
        Objects.requireNonNull(chooser, "Not attached to a chooser");
        final Task task = new SimpleTask(taskMessage);
        final TaskMonitor taskMonitor = (taskMonitorFactory != null
                ? taskMonitorFactory
                : context(Contexts.TASK_MONITOR)).createTaskMonitor();
        taskMonitor.onStart(task);
        uploadTransport.upload(url, chooser.getFileInput(), new FileUploadCallback() {
            @Override
            public void onUploadStart() {
                // Started above
            }

            @Override
            public void onUploadSuccess(final String responseText) {
                taskMonitor.onEnd(task);
                if (!isDisposed()) {
                    readReply(responseText, onSuccess, onFailure);
                }
            }

            @Override
            public void onUploadFailure(final String message) {
                taskMonitor.onEnd(task);
                if (!isDisposed()) {
                    onFailure.accept(message);
                }
            }
        });
    }

    /// Sets how every [FileChooserController] sends its file. Only for tests, which fake uploads.
    ///
    /// @param transport The transport, or null for Stroom's default, which uploads the file with
    ///                  an `XMLHttpRequest`.
    public static void setUploadTransport(final FileUploadTransport transport) {
        uploadTransport = transport != null
                ? transport
                : DEFAULT_TRANSPORT;
    }

    /// Reads the upload servlet's reply: a property map that says whether it succeeded, with the
    /// uploaded file's key or the exception's message.
    ///
    /// @param reply     The reply's body, or null if there was none.
    /// @param onSuccess Given the key of the uploaded file.
    /// @param onFailure Given the reason it failed.
    static void readReply(final String reply,
                          final Consumer<ResourceKey> onSuccess,
                          final Consumer<String> onFailure) {
        if (reply == null) {
            onFailure.accept("Unable to read file");
            return;
        }
        final ResourceKey resourceKey;
        try {
            final PropertyMap propertyMap = new PropertyMap();
            propertyMap.loadArgLine(reply);
            if (!propertyMap.isSuccess()) {
                onFailure.accept(propertyMap.get("exception"));
                return;
            }
            resourceKey = new ResourceKey(propertyMap);
        } catch (final RuntimeException e) {
            onFailure.accept(e.getMessage());
            return;
        }
        onSuccess.accept(resourceKey);
    }
}
