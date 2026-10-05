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


package stroom.gwt.workbench.framework.client.play;

/// Options for an event fired with [FireEvent], the equivalent of the event init object passed to
/// Testing Library's `fireEvent`, e.g. `fireEvent.mouseDown(el, { ctrlKey: true, clientX: 300 })`
/// is `play.fireEvent().mouseDown(el, EventInit.create().ctrlKey().at(300, 150))`.
///
/// The coordinates can be relative to an element found when the event is fired, e.g.
/// `EventInit.create().atCentreOf(play.getByRole("tab", "Bravo"))`.
public final class EventInit {

    private Double clientX;
    private Double clientY;
    private Query reference;
    // Relative to the reference's top left corner, or to its centre if null
    private Double offsetX;
    private Double offsetY;
    private Integer button;
    private Integer buttons;
    private Integer detail;
    private int modifiers;
    private String key;
    private String code;
    private Integer keyCode;
    private Boolean bubbles;

    private EventInit() {
    }

    /// @return Options with the defaults for the event type.
    public static EventInit create() {
        return new EventInit();
    }

    /// @param x The `clientX` coordinate.
    /// @param y The `clientY` coordinate.
    /// @return These options.
    public EventInit at(final double x, final double y) {
        this.clientX = x;
        this.clientY = y;
        this.reference = null;
        return this;
    }

    /// @param element The element, found when the event is fired.
    /// @return These options, with the coordinates of the element's centre.
    public EventInit atCentreOf(final Query element) {
        this.reference = element;
        this.offsetX = null;
        this.offsetY = null;
        return this;
    }

    /// @param element The element, found when the event is fired.
    /// @param dx      The distance right of the element's left edge.
    /// @param dy      The distance below the element's top edge.
    /// @return These options, with coordinates relative to the element's top left corner, e.g.
    /// `relativeTo(panel, 3, 10)` for `{ clientX: rect.left + 3, clientY: rect.top + 10 }`.
    public EventInit relativeTo(final Query element, final double dx, final double dy) {
        this.reference = element;
        this.offsetX = dx;
        this.offsetY = dy;
        return this;
    }

    /// @param button The mouse button, 0 for main, 1 for middle and 2 for secondary.
    /// @return These options.
    public EventInit button(final int button) {
        this.button = button;
        return this;
    }

    /// @param buttons The mouse buttons held, as a mask (1 main, 2 secondary, 4 middle).
    /// @return These options.
    public EventInit buttons(final int buttons) {
        this.buttons = buttons;
        return this;
    }

    /// @param detail The click count.
    /// @return These options.
    public EventInit detail(final int detail) {
        this.detail = detail;
        return this;
    }

    /// @return These options, with Shift held.
    public EventInit shiftKey() {
        modifiers |= Keys.SHIFT;
        return this;
    }

    /// @return These options, with Control held.
    public EventInit ctrlKey() {
        modifiers |= Keys.CONTROL;
        return this;
    }

    /// @return These options, with Alt held.
    public EventInit altKey() {
        modifiers |= Keys.ALT;
        return this;
    }

    /// @return These options, with Meta held.
    public EventInit metaKey() {
        modifiers |= Keys.META;
        return this;
    }

    /// @param key The key value, e.g. `u` or `Enter`. The `keyCode` and `code` are set to match
    ///            unless given.
    /// @return These options.
    public EventInit key(final String key) {
        this.key = key;
        return this;
    }

    /// @param code The physical key, e.g. `KeyU`.
    /// @return These options.
    public EventInit code(final String code) {
        this.code = code;
        return this;
    }

    /// @param keyCode The legacy key code, e.g. 13 for Enter.
    /// @return These options.
    public EventInit keyCode(final int keyCode) {
        this.keyCode = keyCode;
        return this;
    }

    /// @param bubbles True if the event bubbles, to override the default for its type.
    /// @return These options.
    public EventInit bubbles(final boolean bubbles) {
        this.bubbles = bubbles;
        return this;
    }

    /// @return The `clientX` coordinate, or null if not set.
    Double getClientX() {
        return clientX;
    }

    /// @return The `clientY` coordinate, or null if not set.
    Double getClientY() {
        return clientY;
    }

    /// @return The element the coordinates are relative to, or null.
    Query getReference() {
        return reference;
    }

    /// @return The x offset from the reference's left edge, or null for its centre.
    Double getOffsetX() {
        return offsetX;
    }

    /// @return The y offset from the reference's top edge, or null for its centre.
    Double getOffsetY() {
        return offsetY;
    }

    /// @return The mouse button, or null for the default.
    Integer getButton() {
        return button;
    }

    /// @return The mouse buttons held, or null for the default.
    Integer getButtons() {
        return buttons;
    }

    /// @return The click count, or null for the default.
    Integer getDetail() {
        return detail;
    }

    /// @return The modifier keys held, as a mask of [Keys#SHIFT] etc.
    int getModifiers() {
        return modifiers;
    }

    /// @return The key value, or null.
    String getKey() {
        return key;
    }

    /// @return The physical key, or null.
    String getCode() {
        return code;
    }

    /// @return The legacy key code, or null to derive it from the key.
    Integer getKeyCode() {
        return keyCode;
    }

    /// @return Whether the event bubbles, or null for the default.
    Boolean getBubbles() {
        return bubbles;
    }

    /// @return The options as JavaScript, e.g. `{ ctrlKey: true, clientX: 300, clientY: 150 }`.
    String describe() {
        final StringBuilder sb = new StringBuilder();
        if (reference != null) {
            if (offsetX == null) {
                append(sb, "at", "centreOf(" + reference.describe() + ")");
            } else {
                append(sb, "clientX", "left + " + Values.formatNumber(offsetX));
                append(sb, "clientY", "top + " + Values.formatNumber(offsetY) + " of "
                                      + reference.describe());
            }
        } else if (clientX != null) {
            append(sb, "clientX", Values.formatNumber(clientX));
            append(sb, "clientY", Values.formatNumber(clientY));
        }
        appendIfSet(sb, "button", button);
        appendIfSet(sb, "buttons", buttons);
        appendIfSet(sb, "detail", detail);
        if ((modifiers & Keys.SHIFT) != 0) {
            append(sb, "shiftKey", "true");
        }
        if ((modifiers & Keys.CONTROL) != 0) {
            append(sb, "ctrlKey", "true");
        }
        if ((modifiers & Keys.ALT) != 0) {
            append(sb, "altKey", "true");
        }
        if ((modifiers & Keys.META) != 0) {
            append(sb, "metaKey", "true");
        }
        if (key != null) {
            append(sb, "key", Expectation.quote(key));
        }
        if (code != null) {
            append(sb, "code", Expectation.quote(code));
        }
        appendIfSet(sb, "keyCode", keyCode);
        if (bubbles != null) {
            append(sb, "bubbles", bubbles.toString());
        }
        return sb.length() == 0
                ? "{}"
                : "{ " + sb + " }";
    }

    private static void appendIfSet(final StringBuilder sb, final String name, final Integer value) {
        if (value != null) {
            append(sb, name, value.toString());
        }
    }

    private static void append(final StringBuilder sb, final String name, final String value) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(name).append(": ").append(value);
    }
}
