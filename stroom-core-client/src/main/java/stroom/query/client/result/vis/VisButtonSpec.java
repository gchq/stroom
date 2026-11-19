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

package stroom.query.client.result.vis;

import stroom.svg.shared.SvgImage;

import java.util.Objects;

/// A button that a visualisation asks for in its toolbar, beside the pause button, with
/// `stroom.setToolbarButtons` in `vis.js`. Stroom makes it a real toolbar button (or toggle), so
/// it looks and works as every other toolbar button does; the visualisation only says what it is
/// and hears when it is pressed.
public final class VisButtonSpec {

    /// The icon of a button whose icon isn't one of Stroom's.
    static final SvgImage UNKNOWN_ICON = SvgImage.SETTINGS;

    private final String id;
    private final String title;
    private final SvgImage icon;
    private final Boolean pressed;
    private final boolean enabled;

    /// An enabled button or toggle.
    ///
    /// @param id      What the visualisation calls it, given back when it is pressed.
    /// @param title   Its name, shown as its tooltip and announced by screen readers.
    /// @param icon    The name of one of Stroom's icons, see [#icon(String)].
    /// @param pressed Whether a toggle is pressed, or null for a button that isn't a toggle.
    public VisButtonSpec(final String id, final String title, final String icon, final Boolean pressed) {
        this(id, title, icon, pressed, true);
    }

    /// @param id      What the visualisation calls it, given back when it is pressed.
    /// @param title   Its name, shown as its tooltip and announced by screen readers.
    /// @param icon    The name of one of Stroom's icons, see [#icon(String)].
    /// @param pressed Whether a toggle is pressed, or null for a button that isn't a toggle.
    /// @param enabled False if it can't be pressed for now (e.g. a save with nothing to save); it is
    ///                still in the toolbar, greyed out, so that a person can find it and hear why.
    public VisButtonSpec(final String id,
                            final String title,
                            final String icon,
                            final Boolean pressed,
                            final boolean enabled) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title == null || title.trim().isEmpty()
                ? id
                : title;
        this.icon = icon(icon);
        this.pressed = pressed;
        this.enabled = enabled;
    }

    /// The icon with a name, e.g. `DOWNLOAD`, `FILTER` or `EYE`: one of Stroom's icons
    /// ([SvgImage]), ignoring case and treating `-` as `_` (so `expand-all` is `EXPAND_ALL`).
    ///
    /// @param name The icon's name, may be null.
    /// @return The icon, or [#UNKNOWN_ICON] if there is none with the name.
    static SvgImage icon(final String name) {
        if (name == null || name.trim().isEmpty()) {
            return UNKNOWN_ICON;
        }
        final String constant = name.trim().replace('-', '_').toUpperCase();
        for (final SvgImage image : SvgImage.values()) {
            if (image.name().equals(constant)) {
                return image;
            }
        }
        return UNKNOWN_ICON;
    }

    /// @return What the visualisation calls it.
    public String getId() {
        return id;
    }

    /// @return Its name.
    public String getTitle() {
        return title;
    }

    /// @return Its icon.
    public SvgImage getIcon() {
        return icon;
    }

    /// @return True if it is a toggle.
    public boolean isToggle() {
        return pressed != null;
    }

    /// @return True if it is a toggle that is pressed.
    public boolean isPressed() {
        return Boolean.TRUE.equals(pressed);
    }

    /// @return True if it can be pressed.
    public boolean isEnabled() {
        return enabled;
    }
}
