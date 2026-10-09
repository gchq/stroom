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

package stroom.floormap.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// Per-type presentation settings for a floor map (see the coordinate/rendering
/// redesign, §6 "Type settings").
///
/// Types are held on the [FloorMapDoc] as an *ordered* list of
/// `TypeStyle`s. The list **order is the z-order** — earlier
/// entries paint behind later ones — and each entry also carries the default
/// graphic drawn for a fact of that type when it has no image of its own: either
/// a [#shape] filled with [#colour], or an arbitrary image from the
/// document's asset store ([#graphic]).
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TypeStyle {

    /// Default graphic shape for an imageless fact.
    public enum Shape {
        CIRCLE,
        SQUARE,
        TRIANGLE,
        DIAMOND,
        PIN
    }

    /// Built-in colour for an unconfigured `person` layer — people keep their
    /// traditional blue on maps that have never configured a person style.
    public static final String DEFAULT_PERSON_COLOUR = "#1f77b4";

    /// Built-in colour for any other unconfigured layer.
    public static final String DEFAULT_COLOUR = "#607d8b";

    @JsonProperty
    private final String type;
    @JsonProperty
    private final Shape shape;
    @JsonProperty
    private final String colour;
    @JsonProperty
    private final String graphic;
    @JsonProperty
    private final String icon;

    @JsonCreator
    public TypeStyle(@JsonProperty("type") final String type,
                     @JsonProperty("shape") final Shape shape,
                     @JsonProperty("colour") final String colour,
                     @JsonProperty("graphic") final String graphic,
                     @JsonProperty("icon") final String icon) {
        this.type = type;
        this.shape = shape;
        this.colour = colour;
        this.graphic = graphic;
        this.icon = icon;
    }

    /// Convenience for a style drawing a shape or an uploaded image, with no icon.
    public TypeStyle(final String type,
                     final Shape shape,
                     final String colour,
                     final String graphic) {
        this(type, shape, colour, graphic, null);
    }

    /// Convenience for a shape-and-colour style with no image graphic.
    public TypeStyle(final String type,
                     final Shape shape,
                     final String colour) {
        this(type, shape, colour, null, null);
    }

    /// A style drawing one of the built-in icons, filled with `colour`.
    ///
    /// @param icon the icon; `null` falls back to the shape
    public static TypeStyle ofIcon(final String type,
                                   final FloorMapIcon icon,
                                   final String colour) {
        return new TypeStyle(type, null, colour, null,
                icon == null
                        ? null
                        : icon.name());
    }

    /// The fact type this style applies to.
    public String getType() {
        return type;
    }

    /// The default graphic shape, or `null` to use the render fallback.
    public Shape getShape() {
        return shape;
    }

    /// The default graphic fill colour (e.g. `"#1f77b4"`), or `null`.
    public String getColour() {
        return colour;
    }

    /// The asset-store URL of the image to draw instead of [#shape] (e.g.
    /// `"/assets/<docUuid>/icons/van.svg"`), or `null` to draw the
    /// shape. Any format the browser can render is allowed.
    ///
    /// The image is drawn at the same fixed screen size as a shape glyph, so
    /// layers stay legible at every zoom level. A fact carrying its own image
    /// takes precedence over its layer's graphic.
    public String getGraphic() {
        return graphic;
    }

    /// The name of the built-in [FloorMapIcon] to draw instead of
    /// [#shape], or `null` to draw the shape.
    ///
    /// Stored as the enum's name rather than the enum itself so a document
    /// written by a version that knows an icon this one does not still
    /// deserialises — [#iconOrNull()] resolves it, and an unrecognised
    /// name simply draws the shape.
    ///
    /// Ranks *below* [an uploaded image][#getGraphic()] and above
    /// the shape, matching the order the renderer checks them in. Choosing one in
    /// the appearance dialog clears the other two, so only one can ever be set.
    public String getIcon() {
        return icon;
    }

    /// The built-in icon this layer draws, or `null` if it draws a shape or
    /// an image. Resolves [#getIcon()], so an unknown name reads as
    /// `null`.
    public FloorMapIcon iconOrNull() {
        return FloorMapIcon.fromName(icon);
    }

    /// True if this style draws a built-in icon.
    ///
    /// An unrecognised icon name is **not** "has an icon": the
    /// layer cannot draw one, so every surface must treat it as having none. See
    /// [#hasGraphic()] on why this needs no `@JsonIgnore`.
    public boolean hasIcon() {
        return iconOrNull() != null;
    }

    /// True if this style draws an image rather than a shape.
    ///
    /// Needs no `@JsonIgnore`: Jackson only auto-detects `getXxx`/
    /// `isXxx` as properties, so a `hasXxx` method is invisible to it —
    /// matching `Fact.hasImage()` and the other `has*` helpers.
    /// `TestJsonSerialisation` fails the build on a redundant
    /// `@JsonIgnore`.
    public boolean hasGraphic() {
        return NullSafe.isNonEmptyString(graphic);
    }

    /// The colour a fact of the given type is actually drawn in when it carries no
    /// colour of its own: the colour configured for that type on the Settings tab
    /// if there is one, otherwise the built-in default.
    ///
    /// This is the single authority for "what colour does this type resolve to",
    /// so the canvas, the layer-appearance dialog and the object-edit dialog's
    /// *Default* fill cannot drift apart — a picker that showed anything
    /// else would be advertising a colour the map does not use.
    ///
    /// @param type   the fact type (e.g. `"gate"`); `null` yields the
    ///         built-in default
    /// @param styles the document's per-type styles, or `null`
    /// @return a 7-character hex colour; never `null`
    public static String colourForType(final String type, final List<TypeStyle> styles) {
        if (type != null && styles != null) {
            for (final TypeStyle style : styles) {
                if (style != null && type.equals(style.getType())
                        && NullSafe.isNonEmptyString(style.getColour())) {
                    return style.getColour();
                }
            }
        }
        return FloorMapJsonKeys.PERSON.equalsIgnoreCase(type)
                ? DEFAULT_PERSON_COLOUR
                : DEFAULT_COLOUR;
    }

    /// Merges the set of discovered type names into an existing ordered list of
    /// type styles: existing entries keep their position and settings; any type
    /// present in `discoveredTypes` but not already configured is appended
    /// **alphabetically** with a default (null) shape and colour.
    ///
    /// This backs the Settings tab's "Discover" button. It never removes a
    /// configured type — a type that has disappeared from the data keeps its
    /// saved style.
    ///
    /// @param existing        the current ordered styles (may be `null`)
    /// @param discoveredTypes the distinct type names found in the data (may be
    ///         `null`); blank names are ignored
    /// @return a new ordered list; never `null`
    public static List<TypeStyle> merge(final List<TypeStyle> existing,
                                        final Collection<String> discoveredTypes) {
        final List<TypeStyle> result = new ArrayList<>();
        final Set<String> present = new LinkedHashSet<>();
        if (existing != null) {
            for (final TypeStyle style : existing) {
                // Skip a null element rather than dereferencing it. Nothing in the
                // application produces one, but a hand-edited or badly imported document
                // can carry a literal null in the typeStyles array, and the sibling
                // walkers over this same list (colourForType, withAreaStyle,
                // FloorMapDocSession.hasAreaStyle) all guard for it — this one did not.
                if (style == null) {
                    continue;
                }
                result.add(style);
                if (style.getType() != null) {
                    present.add(style.getType());
                }
            }
        }
        if (discoveredTypes != null) {
            // Sort the genuinely-new type names alphabetically before appending.
            final Set<String> fresh = new TreeSet<>();
            for (final String name : discoveredTypes) {
                if (NullSafe.isNonEmptyString(name) && !present.contains(name)) {
                    fresh.add(name);
                }
            }
            for (final String name : fresh) {
                result.add(new TypeStyle(name, null, null));
            }
        }
        return result;
    }

    /// Returns a copy of `existing` guaranteed to contain a style for the
    /// [FloorMapJsonKeys#AREA] type.
    ///
    /// If no `"area"` style is present, one is inserted immediately
    /// *after* the last `"background"` entry (or at index 0 when
    /// there is no background style), so areas paint above the floor plan but
    /// beneath every other object. Do not rely on [#merge] for this —
    /// discovery appends new types at the *end*, which would paint areas
    /// on top of everything.
    ///
    /// @param existing the current ordered styles (may be `null`); never
    ///         mutated
    /// @return a new ordered list containing an `"area"` style; never
    ///         `null`
    public static List<TypeStyle> withAreaStyle(final List<TypeStyle> existing) {
        final List<TypeStyle> result = new ArrayList<>();
        int insertAt = 0;
        if (existing != null) {
            for (final TypeStyle style : existing) {
                if (style != null && FloorMapJsonKeys.AREA.equals(style.getType())) {
                    return new ArrayList<>(existing);
                }
                result.add(style);
                if (style != null && FloorMapJsonKeys.BACKGROUND.equals(style.getType())) {
                    insertAt = result.size();
                }
            }
        }
        result.add(insertAt, new TypeStyle(FloorMapJsonKeys.AREA, null, "#1e88e5"));
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TypeStyle that = (TypeStyle) o;
        return Objects.equals(type, that.type)
                && shape == that.shape
                && Objects.equals(colour, that.colour)
                && Objects.equals(graphic, that.graphic)
                && Objects.equals(icon, that.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, shape, colour, graphic, icon);
    }

    @Override
    public String toString() {
        return "TypeStyle{type='" + type + "', shape=" + shape + ", colour='" + colour
                + "', graphic='" + graphic + "', icon='" + icon + "'}";
    }
}
