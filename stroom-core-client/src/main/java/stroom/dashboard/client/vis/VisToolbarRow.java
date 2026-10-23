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

import com.google.gwt.user.client.ui.FlowPanel;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/// The toolbar row above a visualisation, laid out as a table's (`PagerViewImpl`): the buttons the
/// visualisation asks for (`stroom.setToolbarButtons` in `vis.js`) at the start, and its pause and
/// resume button at the end. Being above the visualisation's frame rather than over it, neither
/// covers what the visualisation draws.
public class VisToolbarRow extends FlowPanel {

    private final Toolbar visToolbar;
    private final RefreshButton refreshButton;
    // The buttons the visualisation asked for, as visToolbar shows them
    private List<VisToolbarButton> visButtons = Collections.emptyList();

    public VisToolbarRow() {
        setStyleName("pager dock-container-horizontal dock-min align-center visToolbarRow");

        visToolbar = new Toolbar("Visualisation actions");
        // No empty toolbar for screen readers to find until the visualisation asks for buttons
        visToolbar.setVisible(false);
        final FlowPanel visToolbarContainer = new FlowPanel();
        visToolbarContainer.setStyleName("pager-toolbar");
        visToolbarContainer.add(visToolbar);
        add(visToolbarContainer);

        final Toolbar refreshToolbar = new Toolbar("Visualisation updates");
        refreshButton = refreshToolbar.refresh(null);
        refreshButton.setAllowPause(true);
        add(refreshToolbar);
    }

    /// @return The pause and resume button, which the presenter gives what it does.
    public RefreshButton getRefreshButton() {
        return refreshButton;
    }

    /// Shows the buttons the visualisation asks for, replacing any it showed. Stroom's own toolbar
    /// buttons and toggles are made for them, so they look and work as every other toolbar's do.
    ///
    /// @param buttons The buttons, none for no visualisation buttons.
    /// @param onPress Given a button's id when it is pressed, and a toggle's new state (null for a
    ///                button that isn't a toggle).
    public void setVisButtons(final List<VisToolbarButton> buttons, final BiConsumer<String, Boolean> onPress) {
        if (isSameButtons(buttons)) {
            // Only a toggle's state, or whether a button is enabled, may have changed, so keep the
            // buttons (and the focus on one)
            for (int i = 0; i < buttons.size(); i++) {
                final ToolbarButton shown = (ToolbarButton) visToolbar.getWidget(i);
                if (buttons.get(i).isToggle()) {
                    ((ToolbarToggleButton) shown).setState(buttons.get(i).isPressed());
                }
                shown.setEnabled(buttons.get(i).isEnabled());
            }
        } else {
            visToolbar.clear();
            for (final VisToolbarButton button : buttons) {
                if (button.isToggle()) {
                    final ToolbarToggleButton toggle = visToolbar.toggle(button.getIcon(), button.getTitle(),
                            pressed -> onPress.accept(button.getId(), pressed));
                    toggle.setState(button.isPressed());
                    toggle.setEnabled(button.isEnabled());
                } else {
                    visToolbar.button(button.getIcon(), button.getTitle(),
                                    () -> onPress.accept(button.getId(), null))
                            .setEnabled(button.isEnabled());
                }
            }
        }
        visButtons = buttons;
        visToolbar.setVisible(!buttons.isEmpty());
    }

    // Whether the buttons are those shown, other than the toggles' states
    private boolean isSameButtons(final List<VisToolbarButton> buttons) {
        if (buttons.size() != visButtons.size()) {
            return false;
        }
        for (int i = 0; i < buttons.size(); i++) {
            final VisToolbarButton button = buttons.get(i);
            final VisToolbarButton shown = visButtons.get(i);
            if (!button.getId().equals(shown.getId())
                || !button.getTitle().equals(shown.getTitle())
                || button.getIcon() != shown.getIcon()
                || button.isToggle() != shown.isToggle()) {
                return false;
            }
        }
        return true;
    }
}
