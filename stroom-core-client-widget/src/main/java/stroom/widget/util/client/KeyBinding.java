package stroom.widget.util.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class KeyBinding {

    private static final Map<Action, List<Binding>> BINDINGS = new HashMap<>();
    private static final Map<Action, Command> COMMANDS = new HashMap<>();

    static {
        add(Action.MOVE_UP, KeyCodes.KEY_W, KeyCodes.KEY_K, KeyCodes.KEY_UP);
        add(Action.MOVE_DOWN, KeyCodes.KEY_S, KeyCodes.KEY_J, KeyCodes.KEY_DOWN);
        add(Action.MOVE_LEFT, KeyCodes.KEY_A, KeyCodes.KEY_H, KeyCodes.KEY_LEFT);
        add(Action.MOVE_RIGHT, KeyCodes.KEY_D, KeyCodes.KEY_L, KeyCodes.KEY_RIGHT);
        add(Action.MOVE_PAGE_DOWN, KeyCodes.KEY_PAGEDOWN);
        add(Action.MOVE_PAGE_UP, KeyCodes.KEY_PAGEUP);
        add(Action.MOVE_START, KeyCodes.KEY_HOME);
        add(Action.MOVE_END, KeyCodes.KEY_END);
        add(Action.CLOSE, KeyCodes.KEY_ESCAPE);
        add(Action.EXECUTE, KeyCodes.KEY_ENTER, KeyCodes.KEY_MAC_ENTER, '\n', '\r');
        add(Action.SELECT, KeyCodes.KEY_SPACE);
        add(Action.SELECT_ALL, new Builder().keyCode(KeyCodes.KEY_A).ctrl(true).build());
        add(Action.MENU, KeyCodes.KEY_ALT, KeyCodes.KEY_CONTEXT_MENU);
        add(Action.DIALOG_OK, true, KeyCodes.KEY_ENTER, KeyCodes.KEY_MAC_ENTER, '\n', '\r');
        add(Action.DIALOG_CLOSE, KeyCodes.KEY_ESCAPE);

        add(Action.ITEM_SAVE, new Builder().keyCode(KeyCodes.KEY_S).ctrl(true).build());
        add(Action.ITEM_SAVE_ALL, new Builder().keyCode(KeyCodes.KEY_S).shift(true).ctrl(true).build());
        add(Action.ITEM_CLOSE, new Builder().keyCode(KeyCodes.KEY_W).alt(true).build());
        add(Action.ITEM_CLOSE_ALL, new Builder().keyCode(KeyCodes.KEY_W).shift(true).alt(true).build());
    }

    public static void addCommand(final Action action, final Command command) {
        COMMANDS.put(action, command);
    }

    public static String getShortcut(final Action action) {
        final List<Binding> list = BINDINGS.get(action);
        if (list != null && list.size() > 0) {
            final Binding binding = list.get(0);
            return binding.toString();
        }
        return null;
    }

    public static boolean is(final NativeEvent e,
                             final Action... actions) {
        final Binding eventAsBinding = new Builder()
                .keyCode(e.getKeyCode())
                .shift(e.getShiftKey())
                .ctrl(e.getCtrlKey())
                .alt(e.getAltKey())
                .meta(e.getMetaKey())
                .build();

        for (final Action action : actions) {
            final boolean matchesAction = matchesAction(eventAsBinding, action);
            if (matchesAction) {
                e.preventDefault();
                e.stopPropagation();
                return true;
            }
        }

        return false;
    }

    public static boolean isCommand(final NativeEvent e) {
        final Binding eventAsBinding = new Builder()
                .keyCode(e.getKeyCode())
                .shift(e.getShiftKey())
                .ctrl(e.getCtrlKey())
                .alt(e.getAltKey())
                .meta(e.getMetaKey())
                .build();

        // Check to see if the keypress is supposed to execute a command.
        if (eventAsBinding.hasModifiers()) {
            for (final Entry<Action, Command> entry : COMMANDS.entrySet()) {
                final boolean matchesAction = matchesAction(eventAsBinding, entry.getKey());
                if (matchesAction) {
                    GWT.log("Execute command: " + getShortcut(entry.getKey()));
                    entry.getValue().execute();
                    e.preventDefault();
                    e.stopPropagation();
                    return true;
                }
            }
        }
        return false;
    }


    private static boolean matchesAction(final Binding eventAsBinding, final Action action) {
        final List<Binding> bindings = BINDINGS.get(action);
        if (bindings != null) {
            for (final Binding binding : bindings) {
                // If the binding requires the use of modifiers then test the modifiers that are present on the
                // event.
                if (binding.hasModifiers()) {
                    if (eventAsBinding.equals(binding)) {
                        GWT.log(action.toString());
                        GWT.log("eventAsBinding: " + eventAsBinding);
                        GWT.log("binding: " + binding);
                        return true;
                    }
                } else if (binding.keyCode == eventAsBinding.keyCode) {
                    GWT.log(action.toString());
                    GWT.log("eventAsBinding: " + eventAsBinding);
                    GWT.log("binding: " + binding);
                    return true;
                }
            }
        }
        return false;
    }

    public enum Action {
        MOVE_UP,
        MOVE_DOWN,
        MOVE_LEFT,
        MOVE_RIGHT,
        MOVE_PAGE_DOWN,
        MOVE_PAGE_UP,
        MOVE_START,
        MOVE_END,
        CLOSE,
        EXECUTE,
        SELECT,
        MENU,
        SELECT_ALL,
        DIALOG_OK,
        DIALOG_CLOSE,
        ITEM_SAVE,
        ITEM_SAVE_ALL,
        ITEM_CLOSE,
        ITEM_CLOSE_ALL
    }

    static void add(final Action action,
                    final boolean ctrl,
                    final int... keyCode) {
        for (int code : keyCode) {
            add(action, new Builder().ctrl(ctrl).keyCode(code).build());
        }
    }

    static void add(final Action action, final int... keyCode) {
        for (int code : keyCode) {
            add(action, new Builder().keyCode(code).build());
        }
    }

    static void add(final Action action, final Binding... bindings) {
        final List<Binding> set = BINDINGS.computeIfAbsent(action, k -> new ArrayList<>());
        set.addAll(Arrays.asList(bindings));
    }

    private static class Binding {

        private final int keyCode;
        private final boolean shift;
        private final boolean ctrl;
        private final boolean alt;
        private final boolean meta;

        public Binding(final int keyCode,
                       final boolean shift,
                       final boolean ctrl,
                       final boolean alt,
                       final boolean meta) {
            this.keyCode = keyCode;
            this.shift = shift;
            this.ctrl = ctrl;
            this.alt = alt;
            this.meta = meta;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Binding binding = (Binding) o;
            return keyCode == binding.keyCode &&
                    shift == binding.shift &&
                    ctrl == binding.ctrl &&
                    alt == binding.alt &&
                    meta == binding.meta;
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyCode, shift, ctrl, alt, meta);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            if (ctrl) {
                if (sb.length() > 0) {
                    sb.append("+");
                }
                sb.append("Ctrl");
            }
            if (alt) {
                if (sb.length() > 0) {
                    sb.append("+");
                }
                sb.append("Alt");
            }
            if (shift) {
                if (sb.length() > 0) {
                    sb.append("+");
                }
                sb.append("Shift");
            }
            if (meta) {
                if (sb.length() > 0) {
                    sb.append("+");
                }
                sb.append("Meta");
            }
            if (sb.length() > 0) {
                sb.append("+");
            }
            sb.append((char) keyCode);
            return sb.toString();
        }

        public boolean hasModifiers() {
            return shift || ctrl || alt || meta;
        }
    }

    public static class Builder {

        private int keyCode;
        private boolean shift;
        private boolean ctrl;
        private boolean alt;
        private boolean meta;

        public Builder keyCode(final int keyCode) {
            this.keyCode = keyCode;
            return this;
        }

        public Builder shift(final boolean shift) {
            this.shift = shift;
            return this;
        }

        public Builder ctrl(final boolean ctrl) {
            this.ctrl = ctrl;
            return this;
        }

        public Builder alt(final boolean alt) {
            this.alt = alt;
            return this;
        }

        public Builder meta(final boolean meta) {
            this.meta = meta;
            return this;
        }

        public Binding build() {
            return new Binding(keyCode, shift, ctrl, alt, meta);
        }
    }
}
