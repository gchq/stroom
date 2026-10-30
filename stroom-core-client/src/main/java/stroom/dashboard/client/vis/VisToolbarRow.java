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

package stroom.dashboard.client.vis;

import stroom.data.pager.client.RefreshButton;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/// The toolbar row above a visualisation, laid out as a table's (`PagerPanel`): the buttons the
/// visualisation asks for (`stroom.setToolbarButtons` in `vis.js`) at the start, and its pause and
/// resume button at the end. Being above the visualisation's frame rather than over it, neither
/// covers what the visualisation draws. Its markup is `VisToolbarRow.ui.xml`.
public class VisToolbarRow extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Toolbar visToolbar;
    @UiField
    RefreshButton refreshButton;
    // The buttons the visualisation asked for, as visToolbar shows them
    private List<VisButtonSpec> visButtons = Collections.emptyList();
    private BiConsumer<String, Boolean> onPress;

    public VisToolbarRow() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param onPress Given a button's id when it is pressed, and a toggle's new state (null for a
    ///                button that isn't a toggle), or null for nothing.
    public void setOnPress(final BiConsumer<String, Boolean> onPress) {
        this.onPress = onPress;
    }

    /// @return The pause and resume button, which the controller gives what it does.
    public RefreshButton getRefreshButton() {
        return refreshButton;
    }

    /// Shows the buttons the visualisation asks for, replacing any it showed. Stroom's own toolbar
    /// buttons and toggles are made for them, so they look and work as every other toolbar's do.
    ///
    /// @param buttons The buttons, none for no visualisation buttons; pressing one is told to
    ///                [#setOnPress].
    public void setVisButtons(final List<VisButtonSpec> buttons) {
        if (isSameButtons(buttons)) {
            // Only a toggle's state, or whether a button is enabled, may have changed, so keep the
            // buttons (and the focus on one)
            for (int i = 0; i < buttons.size(); i++) {
                final ToolbarButton shown = (ToolbarButton) visToolbar.getWidget(i);
                if (buttons.get(i).isToggle()) {
                    ((ToolbarToggleButton) shown).setPressed(buttons.get(i).isPressed());
                }
                shown.setEnabled(buttons.get(i).isEnabled());
            }
        } else {
            visToolbar.clear();
            for (final VisButtonSpec button : buttons) {
                if (button.isToggle()) {
                    final ToolbarToggleButton toggle = visToolbar.toggle(button.getIcon(), button.getTitle(),
                            pressed -> press(button.getId(), pressed));
                    toggle.setPressed(button.isPressed());
                    toggle.setEnabled(button.isEnabled());
                } else {
                    visToolbar.button(button.getIcon(), button.getTitle(),
                                    () -> press(button.getId(), null))
                            .setEnabled(button.isEnabled());
                }
            }
        }
        visButtons = buttons;
        visToolbar.setVisible(!buttons.isEmpty());
    }

    private void press(final String id, final Boolean pressed) {
        if (onPress != null) {
            onPress.accept(id, pressed);
        }
    }

    // Whether the buttons are those shown, other than the toggles' states
    private boolean isSameButtons(final List<VisButtonSpec> buttons) {
        if (buttons.size() != visButtons.size()) {
            return false;
        }
        for (int i = 0; i < buttons.size(); i++) {
            final VisButtonSpec button = buttons.get(i);
            final VisButtonSpec shown = visButtons.get(i);
            if (!button.getId().equals(shown.getId())
                || !button.getTitle().equals(shown.getTitle())
                || button.getIcon() != shown.getIcon()
                || button.isToggle() != shown.isToggle()) {
                return false;
            }
        }
        return true;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, VisToolbarRow> {

    }
}
