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

package stroom.pipeline.stepping.client.component;

import stroom.pipeline.shared.XPathFilter;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Add and Edit XPath Filter dialog ([XPathFilterEditor]) of stepping, made for one use.
public class XPathFilterEditorController extends Controller {

    private final XPathFilterEditor view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public XPathFilterEditorController(final EventBus eventBus, final XPathFilterEditor view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Edits a filter in a dialog, owned by `opener` until it is closed, writing it on OK.
    ///
    /// @param opener      What shows it.
    /// @param caption     The dialog's caption, e.g. "Add XPath Filter".
    /// @param xPathFilter The filter, changed on OK.
    /// @param onOk        Run once the filter is written.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final XPathFilter xPathFilter,
                     final Runnable onOk) {
        read(xPathFilter);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(700))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write(xPathFilter);
                        onOk.run();
                    }
                    e.hide();
                })
                .fire();
    }

    private void read(final XPathFilter xPathFilter) {
        view.setXPath(xPathFilter.getPath());
        view.setSearchType(xPathFilter.getSearchType());
        view.setMatchType(xPathFilter.getMatchType());
        view.setValue(xPathFilter.getValue() == null
                ? ""
                : xPathFilter.getValue());
        view.setIgnoreCase(xPathFilter.isIgnoreCase() == null
                ? Boolean.TRUE
                : xPathFilter.isIgnoreCase());
    }

    private void write(final XPathFilter xPathFilter) {
        xPathFilter.setPath(view.getXPath());
        xPathFilter.setSearchType(view.getSearchType());
        xPathFilter.setMatchType(view.getMatchType());
        if (view.getMatchType().isNeedsValue()) {
            xPathFilter.setValue(view.getValue());
            xPathFilter.setIgnoreCase(view.isIgnoreCase());
        } else {
            xPathFilter.setValue(null);
            xPathFilter.setIgnoreCase(null);
        }
    }
}
