/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.shared;

import stroom.util.shared.NullSafe;

/// The built-in icons a layer can be drawn with, as an alternative to a plain
/// [TypeStyle.Shape] or an uploaded image.
///
/// ## Why these are geometry rather than images
///
/// An icon is a path, not a file, for the same reason `FloorMapShapes`
/// is: one definition serves every surface that has to draw it — the canvas
/// glyph, the Layers panel swatch, the appearance dialog's preview and its
/// picker — so a legend cannot drift from the map.
///
/// It also keeps the layer's **colour** meaningful. An icon is
/// filled with the layer colour exactly as a shape is, so one drawing serves
/// every layer that wants it and the colour control keeps working; an uploaded
/// image cannot be recoloured, which is why choosing one currently retires the
/// colour to labels and areas. And unlike an asset, an icon needs no upload, is
/// not per-document, and renders before the document has ever been saved.
///
/// ## What belongs in the set
///
/// **Things that generate logs** — and only those (client
/// direction, 2026-08-13). A floor map exists to show where logged activity
/// happens, so an entity worth its own icon is one with events behind it: a
/// badge reader, a printer, a camera, a person. Building furniture that emits
/// nothing — a fire extinguisher, a desk, a staircase — is not in the set and
/// should not be added to it. Those layers can still be drawn, as a coloured
/// [TypeStyle.Shape] or an uploaded image.
///
/// ## Drawing rules
///
/// Every path is authored on a **24×24 grid with Y pointing
/// down**, filled with the default `nonzero` rule: overlapping
/// subpaths of the same winding simply union, and a hole is a subpath wound the
/// other way (the door's handle, the lift's arrows). [#transform] maps that
/// grid onto a glyph of any size.
///
/// Paths are compile-time constants interpolated into trusted SVG. Nothing
/// from a document reaches them: a stored icon name is resolved through
/// [#fromName], which yields `null` for anything it does not
/// recognise.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM.
public enum FloorMapIcon {

    PERSON("Person",
            "M8.6 6.8a3.4 3.4 0 1 1 6.8 0a3.4 3.4 0 1 1 -6.8 0Z M4.8 20.6V17.4a7.2 5.6 0 0 "
            + "1 14.4 0V20.6Z"),

    DOOR("Door",
            "M6 2.4L18 2.4L18 21.6L6 21.6Z M13.9 12.4a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 "
            + "-2.2 0Z"),

    GATE("Gate",
            "M2 6.2L8.6 6.2L8.6 8L2 8Z M2.6 8L8 8L8 20.4L2.6 20.4Z M15.4 6.2L22 6.2L22 "
            + "8L15.4 8Z M16 8L21.4 8L21.4 20.4L16 20.4Z M12 4.4L14.8 9.2L12.9 9.2L12.9 "
            + "20.4L11.1 20.4L11.1 9.2L9.2 9.2Z"),

    BARRIER("Barrier",
            "M2.4 5.6L6.2 5.6L6.2 19.6L2.4 19.6Z M1 19.6L7.6 19.6L7.6 21.8L1 21.8Z M6.2 "
            + "8.2L21.6 8.2L21.6 11L6.2 11Z M9.6 10.3L11.4 10.3L11.4 8.9L9.6 8.9Z M14.4 "
            + "10.3L16.2 10.3L16.2 8.9L14.4 8.9Z M19.2 10.3L21 10.3L21 8.9L19.2 8.9Z"),

    BADGE_READER("Badge reader",
            "M2.2 6.6h11.2a1.4 1.4 0 0 1 1.4 1.4v8a1.4 1.4 0 0 1-1.4 1.4H2.2a1.4 1.4 0 0 "
            + "1-1.4-1.4V8a1.4 1.4 0 0 1 1.4-1.4Z M3.2 10.9L7.8 10.9L7.8 9.4L3.2 9.4Z M3.2 "
            + "13.6L11.6 13.6L11.6 12.4L3.2 12.4Z M18.11 8.53A4.4 4.4 0 0 1 18.11 15.47L17.19 "
            + "14.29A2.9 2.9 0 0 0 17.19 9.71Z M20.33 5.7A8 8 0 0 1 20.33 18.3L19.4 17.12A6.5 "
            + "6.5 0 0 0 19.4 6.88Z"),

    SMART_LOCK("Smart lock",
            "M12 1.8a5.2 5.2 0 0 1 5.2 5.2v2.8h-2.8V7a2.4 2.4 0 0 0-4.8 0v2.8H6.8V7A5.2 5.2 "
            + "0 0 1 12 1.8Z M5.6 9.8h12.8a1.6 1.6 0 0 1 1.6 1.6v9a1.6 1.6 0 0 1-1.6 "
            + "1.6H5.6a1.6 1.6 0 0 1-1.6-1.6v-9a1.6 1.6 0 0 1 1.6-1.6Z M10.3 14.4a1.7 1.7 0 1 "
            + "0 3.4 0a1.7 1.7 0 1 0 -3.4 0Z M11.1 18.4L12.9 18.4L12.9 14.4L11.1 14.4Z"),

    CAMERA("Camera",
            "M2.6 2.2L21.4 2.2L21.4 4L2.6 4Z M11 4L13 4L13 6.8L11 6.8Z M5.4 6.8L18.6 "
            + "6.8L16.4 12.8L7.6 12.8Z M10.3 9.6a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0Z"),

    SENSOR("Sensor",
            "M5.6 2.4h12.8a1.8 1.8 0 0 1 1.8 1.8v3.4a8.2 8.2 0 0 1-16.4 0V4.2a1.8 1.8 0 0 1 "
            + "1.8-1.8Z M9.7 6.4a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0 -4.6 0Z M17.04 14.34A6.4 "
            + "6.4 0 0 0 6.96 14.34L8.14 13.42A4.9 4.9 0 0 1 15.86 13.42Z M20.04 16.68A10.2 "
            + "10.2 0 0 0 3.96 16.68L5.14 15.76A8.7 8.7 0 0 1 18.86 15.76Z"),

    ALARM("Alarm",
            "M12 2.2a1.6 1.6 0 0 1 1.6 1.6v.8a6.2 6.2 0 0 1 4.6 6v4.2l1.8 "
            + "2.4v1.4H4v-1.4l1.8-2.4v-4.2a6.2 6.2 0 0 1 4.6-6v-.8A1.6 1.6 0 0 1 12 2.2Z M9.5 "
            + "19.6h5a2.5 2.5 0 0 1-5 0Z"),

    LIFT("Lift",
            "M3.6 2.4L20.4 2.4L20.4 21.6L3.6 21.6Z M6.6 10.4L11.4 10.4L9 6.2Z M17.4 "
            + "13.6L12.6 13.6L15 17.8Z"),

    PRINTER("Printer",
            "M6.6 2.4L17.4 2.4L17.4 7L6.6 7Z M2.6 7L21.4 7L21.4 15.6L2.6 15.6Z M4.8 11L8.4 "
            + "11L8.4 9.4L4.8 9.4Z M6.6 15.6L17.4 15.6L17.4 21.4L6.6 21.4Z M8.6 19L15.4 "
            + "19L15.4 17.6L8.6 17.6Z"),

    COMPUTER("Computer",
            "M2.4 3.4L21.6 3.4L21.6 16.2L2.4 16.2Z M4.4 14.2L19.6 14.2L19.6 5.4L4.4 5.4Z "
            + "M9.6 17.8h4.8v1.8h3.4v1.8H6.2v-1.8h3.4Z"),

    LAPTOP("Laptop",
            "M4.2 4L19.8 4L19.8 14.4L4.2 14.4Z M6.2 12.4L17.8 12.4L17.8 6L6.2 6Z M1.6 "
            + "15.6h20.8l1.2 2.8a1 1 0 0 1-.9 1.4H1.3a1 1 0 0 1-.9-1.4Z"),

    MOBILE("Mobile device",
            "M7 1.6h10a2.2 2.2 0 0 1 2.2 2.2v16.4a2.2 2.2 0 0 1-2.2 2.2H7a2.2 2.2 0 0 "
            + "1-2.2-2.2V3.8A2.2 2.2 0 0 1 7 1.6Z M6.6 17.6L17.4 17.6L17.4 5L6.6 5Z M10.2 "
            + "4.1L13.8 4.1L13.8 3.2L10.2 3.2Z M10.9 19.9a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 "
            + "-2.2 0Z"),

    KIOSK("Kiosk",
            "M4.4 1.8L19.6 1.8L19.6 22.2L4.4 22.2Z M6.4 10.8L17.6 10.8L17.6 3.8L6.4 3.8Z "
            + "M7.4 13.9L16.6 13.9L16.6 12.4L7.4 12.4Z M7.45 17.4a1.15 1.15 0 1 0 2.3 0a1.15 "
            + "1.15 0 1 0 -2.3 0Z M10.85 17.4a1.15 1.15 0 1 0 2.3 0a1.15 1.15 0 1 0 -2.3 0Z "
            + "M14.25 17.4a1.15 1.15 0 1 0 2.3 0a1.15 1.15 0 1 0 -2.3 0Z"),

    PHONE("Phone",
            "M3.4 3.4h17.2a2 2 0 0 1 2 2v2.6a2 2 0 0 1-2 2H3.4a2 2 0 0 1-2-2V5.4a2 2 0 0 1 "
            + "2-2Z M2.8 11.8h18.4l1.6 8.8H1.2Z M6.95 14.6a1.05 1.05 0 1 0 2.1 0a1.05 1.05 0 "
            + "1 0 -2.1 0Z M10.95 14.6a1.05 1.05 0 1 0 2.1 0a1.05 1.05 0 1 0 -2.1 0Z M14.95 "
            + "14.6a1.05 1.05 0 1 0 2.1 0a1.05 1.05 0 1 0 -2.1 0Z M6.55 17.9a1.05 1.05 0 1 0 "
            + "2.1 0a1.05 1.05 0 1 0 -2.1 0Z M10.95 17.9a1.05 1.05 0 1 0 2.1 0a1.05 1.05 0 1 "
            + "0 -2.1 0Z M15.35 17.9a1.05 1.05 0 1 0 2.1 0a1.05 1.05 0 1 0 -2.1 0Z"),

    SERVER("Server",
            "M3.6 2.6L20.4 2.6L20.4 8L3.6 8Z M5.5 5.3a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 "
            + "-2.2 0Z M3.6 9.3L20.4 9.3L20.4 14.7L3.6 14.7Z M5.5 12a1.1 1.1 0 1 0 2.2 0a1.1 "
            + "1.1 0 1 0 -2.2 0Z M3.6 16L20.4 16L20.4 21.4L3.6 21.4Z M5.5 18.7a1.1 1.1 0 1 0 "
            + "2.2 0a1.1 1.1 0 1 0 -2.2 0Z"),

    NETWORK("Network",
            "M6.2 4.4L8 4.4L8 12.8L6.2 12.8Z M16 4.4L17.8 4.4L17.8 12.8L16 12.8Z M2.6 "
            + "12.8h18.8a1.6 1.6 0 0 1 1.6 1.6v3.6a1.6 1.6 0 0 1-1.6 1.6H2.6a1.6 1.6 0 0 "
            + "1-1.6-1.6v-3.6a1.6 1.6 0 0 1 1.6-1.6Z M4.9 16.2a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 "
            + "1 0 -2.2 0Z M8.5 16.2a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0Z M12.1 16.2a1.1 "
            + "1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0Z"),

    WIFI("Wi-Fi",
            "M1.35 11.94A13 13 0 0 1 22.65 11.94L21.01 13.09A11 11 0 0 0 2.99 13.09Z M4.63 "
            + "14.24A9 9 0 0 1 19.37 14.24L17.73 15.38A7 7 0 0 0 6.27 15.38Z M7.9 16.53A5 5 0 "
            + "0 1 16.1 16.53L14.46 17.68A3 3 0 0 0 9.54 17.68Z M10.2 18.6a1.8 1.8 0 1 1 3.6 "
            + "0a1.8 1.8 0 1 1 -3.6 0Z"),

    POWER("Power",
            "M13.8 2.2L11.6 9.4L17.2 9.4L9.8 21.8L11.6 13.6L6.4 13.6Z"),

    VEHICLE("Vehicle",
            "M1.8 6.6L13.2 6.6L13.2 15L1.8 15Z M13.2 9h3.8l3.4 3.4V15h-7.2Z M1.8 15L20.8 "
            + "15L20.8 16.6L1.8 16.6Z M4 17.8a2.4 2.4 0 1 1 4.8 0a2.4 2.4 0 1 1 -4.8 0Z M14.2 "
            + "17.8a2.4 2.4 0 1 1 4.8 0a2.4 2.4 0 1 1 -4.8 0Z");

    /// The side of the square grid every path is authored on.
    public static final double GRID = 24;

    private final String label;
    private final String path;

    FloorMapIcon(final String label, final String path) {
        this.label = label;
        this.path = path;
    }

    /// The icon's name for people — shown under it in the picker and as the
    /// layer's appearance in a tooltip. Not derived from [#name()]: "Wi-Fi"
    /// and "Badge reader" are not what a de-underscored enum constant gives.
    public String getLabel() {
        return label;
    }

    /// The SVG `d` attribute, on the {@value #GRID}-unit grid described in
    /// the class javadoc. Combine with [#transform] to place it.
    public String getPath() {
        return path;
    }

    /// The icon with the given name, or `null` if there is none.
    ///
    /// Null-safe and lenient by design: the name comes from a stored document,
    /// which may have been written by a later version that had an icon this one
    /// has never heard of, or hand-edited. An unknown name means "no icon", so the
    /// layer falls back to its shape rather than failing to draw.
    ///
    /// @param name an icon name as stored on a [TypeStyle]; may be
    ///         `null`
    public static FloorMapIcon fromName(final String name) {
        if (NullSafe.isEmptyString(name)) {
            return null;
        }
        for (final FloorMapIcon icon : values()) {
            if (icon.name().equals(name)) {
                return icon;
            }
        }
        return null;
    }

    /// The SVG `transform` that maps the icon's grid onto a glyph centred on
    /// the origin and spanning `±halfSize` — the frame
    /// `FloorMapShapes` works in, so an icon drops into the same place a
    /// shape would.
    ///
    /// @param halfSize half the glyph's extent
    /// @return the `transform` attribute value
    public static String transform(final double halfSize) {
        return "translate(" + (-halfSize) + "," + (-halfSize) + ")"
                + " scale(" + ((2 * halfSize) / GRID) + ")";
    }
}
