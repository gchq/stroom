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

import com.google.gwt.dom.client.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Fires single DOM events, the equivalent of Testing Library's `fireEvent`, e.g.
/// `fireEvent.contextMenu(el)` is `play.fireEvent().contextMenu(el)`. Unlike the user events on
/// [Play] (e.g. [Play#click(Query)]), only the one event is fired, with nothing else a real user
/// would cause, e.g. no focus change. Each method adds a step to the play function.
///
/// Mouse events are fired at (0, 0) unless the [EventInit] gives coordinates, as in Testing
/// Library; keyboard events get a `keyCode`, `which` and `code` to match their `key`.
public final class FireEvent {

    private static final List<String> NON_BUBBLING = Arrays.asList(
            "focus", "blur", "mouseenter", "mouseleave", "pointerenter", "pointerleave", "scroll", "load");
    private static final List<String> NON_CANCELABLE = Arrays.asList(
            "focus", "blur", "focusin", "focusout", "mouseenter", "mouseleave", "pointerenter",
            "pointerleave", "scroll", "load", "input", "change");

    private final Play play;

    /// @param play The play function to add the steps to.
    FireEvent(final Play play) {
        this.play = play;
    }

    /// Fires `click`.
    ///
    /// @param target The element.
    public void click(final Query target) {
        fire("click", target, null);
    }

    /// Fires `click`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void click(final Query target, final EventInit init) {
        fire("click", target, init);
    }

    /// Fires `dblclick`.
    ///
    /// @param target The element.
    public void dblClick(final Query target) {
        fire("dblClick", target, null);
    }

    /// Fires `contextmenu` (with the secondary button).
    ///
    /// @param target The element.
    public void contextMenu(final Query target) {
        fire("contextMenu", target, null);
    }

    /// Fires `contextmenu`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void contextMenu(final Query target, final EventInit init) {
        fire("contextMenu", target, init);
    }

    /// Fires `mousedown`.
    ///
    /// @param target The element.
    public void mouseDown(final Query target) {
        fire("mouseDown", target, null);
    }

    /// Fires `mousedown`.
    ///
    /// @param target The element.
    /// @param init   The event's options, e.g. `EventInit.create().ctrlKey()`.
    public void mouseDown(final Query target, final EventInit init) {
        fire("mouseDown", target, init);
    }

    /// Fires `mouseup`.
    ///
    /// @param target The element, e.g. [Play#body()] for `fireEvent.mouseUp(document)`.
    public void mouseUp(final Query target) {
        fire("mouseUp", target, null);
    }

    /// Fires `mouseup`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void mouseUp(final Query target, final EventInit init) {
        fire("mouseUp", target, init);
    }

    /// Fires `mousemove`.
    ///
    /// @param target The element.
    /// @param init   The event's options, e.g. `EventInit.create().at(360, 150)`.
    public void mouseMove(final Query target, final EventInit init) {
        fire("mouseMove", target, init);
    }

    /// Fires `mouseover`.
    ///
    /// @param target The element.
    public void mouseOver(final Query target) {
        fire("mouseOver", target, null);
    }

    /// Fires `mouseout`.
    ///
    /// @param target The element.
    public void mouseOut(final Query target) {
        fire("mouseOut", target, null);
    }

    /// Fires `pointerdown`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void pointerDown(final Query target, final EventInit init) {
        fire("pointerDown", target, init);
    }

    /// Fires `pointermove`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void pointerMove(final Query target, final EventInit init) {
        fire("pointerMove", target, init);
    }

    /// Fires `pointerup`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void pointerUp(final Query target, final EventInit init) {
        fire("pointerUp", target, init);
    }

    /// Fires `keydown`.
    ///
    /// @param target The element, e.g. [Play#body()].
    /// @param init   The event's options, e.g. `EventInit.create().key("u")`.
    public void keyDown(final Query target, final EventInit init) {
        fire("keyDown", target, init);
    }

    /// Fires `keypress`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void keyPress(final Query target, final EventInit init) {
        fire("keyPress", target, init);
    }

    /// Fires `keyup`.
    ///
    /// @param target The element.
    /// @param init   The event's options.
    public void keyUp(final Query target, final EventInit init) {
        fire("keyUp", target, init);
    }

    /// Fires `focus` (only the event; the element doesn't get the focus).
    ///
    /// @param target The element.
    public void focus(final Query target) {
        fire("focus", target, null);
    }

    /// Fires `blur` (only the event; the element keeps the focus).
    ///
    /// @param target The element.
    public void blur(final Query target) {
        fire("blur", target, null);
    }

    /// Fires `focusin`.
    ///
    /// @param target The element.
    public void focusIn(final Query target) {
        fire("focusIn", target, null);
    }

    /// Fires `focusout`.
    ///
    /// @param target The element.
    public void focusOut(final Query target) {
        fire("focusOut", target, null);
    }

    /// Sets a field's value then fires `input`, the equivalent of
    /// `fireEvent.input(el, { target: { value } })`.
    ///
    /// @param target The field.
    /// @param value  The value.
    public void input(final Query target, final String value) {
        fireWithValue("input", target, value);
    }

    /// Sets a field's value then fires `change`, the equivalent of
    /// `fireEvent.change(el, { target: { value } })`.
    ///
    /// @param target The field.
    /// @param value  The value.
    public void change(final Query target, final String value) {
        fireWithValue("change", target, value);
    }

    /// Fires any event, e.g. `scroll` or `wheel`.
    ///
    /// @param type   The event type, e.g. `scroll`.
    /// @param target The element.
    /// @param init   The event's options, or null for the defaults.
    public void event(final String type, final Query target, final EventInit init) {
        Objects.requireNonNull(target, "target");
        // Copied so that changing the options afterwards doesn't change the step
        final EventInit options = copy(init);
        add("fireEvent(" + target.describe() + ", new Event(" + Expectation.quote(type) + (options != null
                ? ", " + options.describe()
                : "") + "))", root -> dispatch(target.resolve(root), type, options, root));
    }

    private void fire(final String method, final Query target, final EventInit init) {
        Objects.requireNonNull(target, "target");
        // Copied so that changing the options afterwards doesn't change the step
        final EventInit options = copy(init);
        add("fireEvent." + method + "(" + target.describe() + (options != null
                ? ", " + options.describe()
                : "") + ")", root -> dispatch(target.resolve(root), method.toLowerCase(), options, root));
    }

    private static EventInit copy(final EventInit init) {
        return init != null
                ? init.copy()
                : null;
    }

    private void fireWithValue(final String method, final Query target, final String value) {
        add("fireEvent." + method + "(" + target.describe() + ", { target: { value: "
            + Expectation.quote(value) + " } })", root -> {
                final Element element = target.resolve(root);
                Dom.setValue(element, value);
                dispatch(element, method, null, root);
            });
    }

    private void add(final String description, final Consumer<Element> action) {
        play.addStep(PlayStep.action(root -> description, action));
    }

    /// Fires an event as Testing Library's `fireEvent` does.
    ///
    /// @param target The element.
    /// @param type   The event type, e.g. `contextmenu`.
    /// @param init   The event's options, or null for the defaults.
    /// @param root   The story's root element, to find an element the options refer to.
    static void dispatch(final Element target, final String type, final EventInit init, final Element root) {
        final EventInit options = init != null
                ? init
                : EventInit.create();
        double clientX = 0;
        double clientY = 0;
        if (options.getReference() != null) {
            final Element reference = options.getReference().resolve(root);
            if (options.getOffsetX() == null) {
                clientX = Dom.getRect(reference, "left") + Dom.getRect(reference, "width") / 2;
                clientY = Dom.getRect(reference, "top") + Dom.getRect(reference, "height") / 2;
            } else {
                clientX = Dom.getRect(reference, "left") + options.getOffsetX();
                clientY = Dom.getRect(reference, "top") + options.getOffsetY();
            }
        } else if (options.getClientX() != null) {
            clientX = options.getClientX();
            clientY = options.getClientY();
        }
        // As Testing Library's fireEvent, which leaves button and buttons at the MouseEvent
        // defaults (0), even for contextMenu, mouseDown and pointerDown
        final int button = options.getButton() != null
                ? options.getButton()
                : 0;
        final int buttons = options.getButtons() != null
                ? options.getButtons()
                : 0;
        final String key = options.getKey();
        final int keyCode = options.getKeyCode() != null
                ? options.getKeyCode()
                : key != null
                        ? defaultKeyCode(type, key)
                        : 0;
        final String code = options.getCode() != null
                ? options.getCode()
                : key != null
                        ? Keys.code(key)
                        : null;
        final int charCode = key != null && "keypress".equals(type)
                ? Keys.charCode(key)
                : 0;
        final boolean bubbles = options.getBubbles() != null
                ? options.getBubbles()
                : defaultBubbles(type);
        Dom.fireEvent(target, type, eventClass(type), bubbles, defaultCancelable(type), clientX, clientY,
                button, buttons, options.getDetail() != null
                        ? options.getDetail()
                        : 0,
                options.getModifiers(), key, code, keyCode, charCode);
    }

    /// @param type An event type, e.g. `mousedown`.
    /// @return The event's constructor, e.g. `MouseEvent`.
    static String eventClass(final String type) {
        if (type.startsWith("pointer")) {
            return "PointerEvent";
        }
        if (type.startsWith("mouse") || "click".equals(type) || "dblclick".equals(type)
            || "contextmenu".equals(type)) {
            return "MouseEvent";
        }
        if (type.startsWith("key")) {
            return "KeyboardEvent";
        }
        if (type.startsWith("focus") || "blur".equals(type)) {
            return "FocusEvent";
        }
        if ("wheel".equals(type)) {
            return "WheelEvent";
        }
        if ("input".equals(type)) {
            return "InputEvent";
        }
        return "Event";
    }

    /// @param type An event type.
    /// @return True if events of the type bubble.
    static boolean defaultBubbles(final String type) {
        return !NON_BUBBLING.contains(type);
    }

    /// @param type An event type.
    /// @return True if events of the type can be cancelled.
    static boolean defaultCancelable(final String type) {
        return !NON_CANCELABLE.contains(type);
    }

    /// @param type A keyboard event type.
    /// @param key  The key value.
    /// @return The legacy `keyCode`: the character code for `keypress`, otherwise the key's code.
    static int defaultKeyCode(final String type, final String key) {
        return "keypress".equals(type)
                ? Keys.charCode(key)
                : Keys.keyCode(key);
    }
}
