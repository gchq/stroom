package stroom.widget.util.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KeyBinding {

    private static final List<Binding> BINDINGS = new ArrayList<>();
    private static final Map<Action, Command> COMMANDS = new HashMap<>();

    private static int shiftCount = 0;
    private static final Timer doubleShiftTimer = new Timer() {
        @Override
        public void run() {
            shiftCount = 0;
        }
    };

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
        add(Action.MENU, new Builder().keyCode(KeyCodes.KEY_CONTEXT_MENU).alt(true).build());
        add(Action.OK, true, KeyCodes.KEY_ENTER, KeyCodes.KEY_MAC_ENTER, '\n', '\r');

        add(Action.ITEM_SAVE, new Builder().keyCode(KeyCodes.KEY_S).ctrl(true).build());
        add(Action.ITEM_SAVE_ALL, new Builder().keyCode(KeyCodes.KEY_S).shift(true).ctrl(true).build());
        add(Action.ITEM_CLOSE, new Builder().keyCode(KeyCodes.KEY_W).alt(true).build());
        add(Action.ITEM_CLOSE_ALL, new Builder().keyCode(KeyCodes.KEY_W).shift(true).alt(true).build());

        add(Action.FIND, new Builder().keyCode(KeyCodes.KEY_F).alt(true).shift(true).build());
        add(Action.FIND_IN_CONTENT, new Builder().keyCode(KeyCodes.KEY_F).ctrl(true).shift(true).build());
        add(Action.RECENT_ITEMS, new Builder().keyCode(KeyCodes.KEY_E).ctrl(true).build());
        add(Action.LOCATE, new Builder().keyCode(KeyCodes.KEY_L).alt(true).build());
    }

    public static void addCommand(final Action action, final Command command) {
        COMMANDS.put(action, command);
    }

    public static String getShortcut(final Action action) {
        for (final Binding binding : BINDINGS) {
            if (binding.action == action) {
                return binding.shortcut.toString();
            }
        }
        return null;
    }

    public static Action test(final NativeEvent e) {
//        log(e);

        Command command = null;
        if (BrowserEvents.KEYDOWN.equals(e.getType())) {
            if (e.getKeyCode() == KeyCodes.KEY_SHIFT &&
                    e.getShiftKey() &&
                    !e.getCtrlKey() &&
                    !e.getAltKey() &&
                    !e.getMetaKey()) {
                if (shiftCount == 0) {
                    shiftCount = 1;
                    doubleShiftTimer.cancel();
                    doubleShiftTimer.schedule(DoubleClickTester.DOUBLE_CLICK_PERIOD);
                } else if (shiftCount == 2) {
                    shiftCount = 0;
                    command = COMMANDS.get(Action.FIND);
                    if (command != null) {
                        e.preventDefault();
                        e.stopPropagation();
                        command.execute();
                    }
                }
            } else {
                shiftCount = 0;
                doubleShiftTimer.cancel();
            }
        } else if (BrowserEvents.KEYUP.equals(e.getType())) {
//            GWT.log("key up " + e.getKeyCode());
            if (e.getKeyCode() == KeyCodes.KEY_SHIFT &&
                    !e.getShiftKey() &&
                    !e.getCtrlKey() &&
                    !e.getAltKey() &&
                    !e.getMetaKey()) {
//                GWT.log("key up shift");
                if (shiftCount == 1) {
                    shiftCount = 2;
                }
            } else {
                shiftCount = 0;
                doubleShiftTimer.cancel();
            }
        }

        if (command == null && BrowserEvents.KEYDOWN.equals(e.getType())) {
            return onKeyDown(e);
        }

        return null;
    }

    private static void log(final NativeEvent e) {
        GWT.log(e.getType() +
                "\nkeyCode=" +
                e.getKeyCode() +
                "\nshift=" +
                e.getShiftKey() +
                "\nctrlKey=" +
                e.getCtrlKey() +
                "\naltKey=" +
                e.getAltKey() +
                "\nmetaKey=" +
                e.getMetaKey() +
                "\n\n");
    }

    private static Action onKeyDown(final NativeEvent e) {
        final Shortcut shortcut = getShortcut(e);

//        GWT.log("KEYDOWN = " + shortcut);

        final Binding binding = getBinding(shortcut);
        if (binding != null) {

//            GWT.log("BINDING = " + binding);

            final Action action = binding.action;
            final Command command = COMMANDS.get(action);
            if (command != null) {
//                GWT.log("EXECUTE = " + action);

                e.preventDefault();
                e.stopPropagation();
                command.execute();
            } else {
                return action;
            }
        }
        return null;
    }

    private static Shortcut getShortcut(final NativeEvent e) {
        return new Builder()
                .keyCode(e.getKeyCode())
                .shift(e.getShiftKey())
                .ctrl(e.getCtrlKey())
                .alt(e.getAltKey())
                .meta(e.getMetaKey())
                .build();
    }

    private static Binding getBinding(final Shortcut shortcut) {
        // Favour exact matches
        for (final Binding binding : BINDINGS) {
            if (binding.shortcut.equals(shortcut)) {
                return binding;
            }
        }

        // If we have a binding that isn't exact but has the same keycode without specifying modifiers then
        // return the bound action.
        for (final Binding binding : BINDINGS) {
            if (!binding.shortcut.hasModifiers() && binding.shortcut.keyCode == shortcut.keyCode) {
                return binding;
            }
        }

        return null;
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
        OK,
        ITEM_SAVE,
        ITEM_SAVE_ALL,
        ITEM_CLOSE,
        ITEM_CLOSE_ALL,
        FIND,
        FIND_IN_CONTENT,
        RECENT_ITEMS,
        LOCATE
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

    static void add(final Action action, final Shortcut... shortcuts) {
        for (final Shortcut shortcut : shortcuts) {
            BINDINGS.add(new Binding.Builder().shortcut(shortcut).action(action).build());
        }
    }


    // --------------------------------------------------------------------------------


    private static class Binding {

        private final Shortcut shortcut;
        private final Action action;

        private Binding(final Shortcut shortcut,
                        final Action action) {
            this.shortcut = shortcut;
            this.action = action;
        }

        @Override
        public String toString() {
            return "Binding{" +
                    "shortcut=" + shortcut +
                    ", action=" + action +
                    '}';
        }

        public static class Builder {

            private Shortcut shortcut;
            private Action action;

            public Builder shortcut(final Shortcut shortcut) {
                this.shortcut = shortcut;
                return this;
            }

            public Builder action(final Action action) {
                this.action = action;
                return this;
            }

            public Binding build() {
                return new Binding(shortcut, action);
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static class Shortcut {

        private final int keyCode;
        private final boolean shift;
        private final boolean ctrl;
        private final boolean alt;
        private final boolean meta;

        public Shortcut(final int keyCode,
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
            final Shortcut binding = (Shortcut) o;
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
            if (keyCode == KeyCodes.KEY_SPACE) {
                sb.append("space");
            } else if (keyCode == KeyCodes.KEY_TAB) {
                sb.append("tab");
            } else {
                sb.append((char) keyCode);
            }
            return sb.toString();
        }

        public boolean hasModifiers() {
            return shift || ctrl || alt || meta;
        }
    }


    // --------------------------------------------------------------------------------


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

        public Shortcut build() {
            return new Shortcut(keyCode, shift, ctrl, alt, meta);
        }
    }
}
