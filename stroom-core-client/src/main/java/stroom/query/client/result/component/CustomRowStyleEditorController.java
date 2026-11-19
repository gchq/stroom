/*
 * Copyright 2024 Crown Copyright
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

import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.CustomRowStyle;
import stroom.query.api.TextAttributes;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Edit Custom Row Style dialog ([CustomRowStyleEditor]) of a conditional formatting rule, made
/// for one use.
public class CustomRowStyleEditorController extends Controller {

    private final CustomRowStyleEditor view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public CustomRowStyleEditorController(final EventBus eventBus, final CustomRowStyleEditor view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Edits a rule's custom style in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener         What shows it.
    /// @param customStyle    The rule's custom style, or null for none.
    /// @param textAttributes The rule's bold and italic, shown in the examples.
    /// @param onOk           Given the style on OK, or null if no colour is given.
    public void edit(final HasControllerNode opener,
                     final CustomConditionalFormattingStyle customStyle,
                     final TextAttributes textAttributes,
                     final Consumer<CustomConditionalFormattingStyle> onOk) {
        final CustomRowStyle light = customStyle == null
                ? null
                : customStyle.getLight();
        final CustomRowStyle dark = customStyle == null
                ? null
                : customStyle.getDark();
        view.setColours(light == null ? null : light.getBackgroundColour(),
                light == null ? null : light.getTextColour(),
                dark == null ? null : dark.getBackgroundColour(),
                dark == null ? null : dark.getTextColour(),
                textAttributes);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption("Edit Custom Row Style")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(write());
                    }
                    e.hide();
                })
                .fire();
    }

    private CustomConditionalFormattingStyle write() {
        final CustomRowStyle light = CustomRowStyle.create(view.getLightBackgroundColour(),
                view.getLightTextColour());
        final CustomRowStyle dark = CustomRowStyle.create(view.getDarkBackgroundColour(),
                view.getDarkTextColour());
        if (light != null || dark != null) {
            return new CustomConditionalFormattingStyle(light, dark);
        }
        return null;
    }
}
