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

package stroom.query.client.result.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datagrid.client.model.ColumnSizing;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.IntConsumer;

/// The Column Width dialog ([ColumnWidth]) of a table's column, made for one use: the keyboard's
/// way to resize a column, which the mouse does by dragging its header's edge. Its column menu's
/// Resize Column opens it.
public class ColumnWidthController extends Controller {

    /// The narrowest a column may be made, as a drag allows.
    static final int MIN_WIDTH = ColumnSizing.DEFAULT_MIN_WIDTH;
    /// The widest a column may be made.
    static final int MAX_WIDTH = 10_000;

    private final ColumnWidth view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public ColumnWidthController(final EventBus eventBus, final ColumnWidth view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setRange(MIN_WIDTH, MAX_WIDTH);
    }

    /// Asks for a column's width in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener  What shows it.
    /// @param width   The column's width now, in pixels.
    /// @param onWidth Given the new width when OK is pressed with a different one.
    public void show(final HasControllerNode opener,
                     final int width,
                     final IntConsumer onWidth) {
        final int start = clamp(width);
        view.setWidth(start);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption("Column Width")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final int newWidth = clamp(view.getWidth());
                        if (newWidth != start) {
                            onWidth.accept(newWidth);
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    /// @param width A width, in pixels.
    /// @return The width, within the widths a column may be.
    static int clamp(final int width) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }
}
