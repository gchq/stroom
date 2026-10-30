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

package stroom.ai.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Download Options dialog ([DownloadChat]) of an Ask Stroom AI conversation, made for one use.
public class DownloadChatController extends Controller {

    private final DownloadChat view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public DownloadChatController(final EventBus eventBus, final DownloadChat view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Asks how to download the conversation in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    /// @param onOk   Told on OK whether the data attached to the conversation goes in the download.
    public void show(final HasControllerNode opener, final Consumer<Boolean> onOk) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Download Options")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(view.isIncludeDataContexts());
                    }
                    e.hide();
                })
                .fire();
    }
}
