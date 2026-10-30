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

package stroom.cell.client;

import stroom.svg.shared.SvgImage;

import java.util.Objects;

/// An icon shown in a table cell (e.g. by [IconCell] or [ActionCell]): the icon, its name (its tooltip,
/// and what a screen reader reads) and whether it is enabled (a disabled icon is drawn faded and, in
/// a cell whose icons can be pressed, does nothing when pressed).
///
/// ```
/// CellIcon.of(SvgImage.LOCKED, "Locked Stream")
/// CellIcon.of(SvgImage.DELETE, "Delete rule", false)
/// ```
public final class CellIcon {

    private final SvgImage icon;
    private final String name;
    private final boolean enabled;

    private CellIcon(final SvgImage icon, final String name, final boolean enabled) {
        this.icon = Objects.requireNonNull(icon);
        this.name = name;
        this.enabled = enabled;
    }

    /// @param icon The icon.
    /// @param name Its name, or null or empty for none.
    /// @return An enabled cell icon.
    public static CellIcon of(final SvgImage icon, final String name) {
        return new CellIcon(icon, name, true);
    }

    /// @param icon    The icon.
    /// @param name    Its name, or null or empty for none.
    /// @param enabled Whether it is enabled.
    /// @return A cell icon.
    public static CellIcon of(final SvgImage icon, final String name, final boolean enabled) {
        return new CellIcon(icon, name, enabled);
    }

    /// @return The icon.
    public SvgImage getIcon() {
        return icon;
    }

    /// @return Its name, or null or empty for none.
    public String getName() {
        return name;
    }

    /// @return Whether it has a name.
    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    /// @return Whether it is enabled.
    public boolean isEnabled() {
        return enabled;
    }

    /// @param name The name to give it.
    /// @return This icon with that name.
    public CellIcon withName(final String name) {
        return new CellIcon(icon, name, enabled);
    }

    /// @param enabled Whether it is enabled.
    /// @return This icon, enabled or not.
    public CellIcon withEnabled(final boolean enabled) {
        return new CellIcon(icon, name, enabled);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final CellIcon that)) {
            return false;
        }
        return enabled == that.enabled && icon == that.icon && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(icon, name, enabled);
    }

    @Override
    public String toString() {
        return "CellIcon{" + icon + ", " + name + ", " + (enabled
                ? "enabled"
                : "disabled") + "}";
    }
}
