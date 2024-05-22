package stroom.widget.util.client;

import stroom.util.shared.GwtNullSafe;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class KeyBinding {

    private static final Map<Shortcut, Action> SHORTCUT_TO_ACTION_MAP = new HashMap<>();
    private static final Map<Action, List<Shortcut>> ACTION_TO_SHORTCUTS_MAP = new HashMap<>();

    private static final Map<Action, Command> COMMANDS = new HashMap<>();

    private static final Map<Shortcut, KeySequence> KEY_SEQUENCES_BY_FIRST_KEY = new HashMap<>();
    private static final Map<Shortcut, KeySequence> KEY_SEQUENCES_BY_SECOND_KEY = new HashMap<>();
    private static final Map<KeySequence, Action> KEY_SEQUENCE_TO_ACTION_MAP = new HashMap<>();
    private static final Map<Action, KeySequence> ACTION_TO_KEY_SEQUENCE_MAP = new HashMap<>();

    private static final int GOTO_FIRST_KEY = KeyCodes.KEY_G;
    private static final int CREATE_DOC_FIRST_KEY = KeyCodes.KEY_C;
    private static final int SUB_TAB_FIRST_KEY = KeyCodes.KEY_T;

    private static final int KEY_SEQUENCE_TIMER_DELAY = 1_000;
    private static final Timer KEY_SEQUENCE_TIMER = new Timer() {
        @Override
        public void run() {
            clearKeySequenceState();
        }
    };

    private static Shortcut firstInSequence = null;
    private static boolean seenKeyUpBetween = false;
    // This
    private static String eventTargetKey = null;

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
//        add(Action.EDIT, KeyCodes.KEY_E);
        add(Action.EXECUTE, KeyCodes.KEY_ENTER, KeyCodes.KEY_MAC_ENTER, '\n');
        add(Action.SELECT, KeyCodes.KEY_SPACE);
        add(Action.SELECT_ALL, Shortcut.builder().ctrl().keyCode(KeyCodes.KEY_A).build());
        add(Action.MENU, Shortcut.builder().alt().keyCode(KeyCodes.KEY_CONTEXT_MENU).build());
        add(Action.OK, true, KeyCodes.KEY_ENTER, KeyCodes.KEY_MAC_ENTER, '\n');

        add(Action.ITEM_SAVE, Shortcut.builder().ctrl().keyCode(KeyCodes.KEY_S).build());
        add(Action.ITEM_SAVE_ALL, Shortcut.builder().shift().ctrl().keyCode(KeyCodes.KEY_S).build());
        add(Action.ITEM_CLOSE, Shortcut.builder().alt().keyCode(KeyCodes.KEY_W).build());
        add(Action.ITEM_CLOSE_ALL, Shortcut.builder().shift().alt().keyCode(KeyCodes.KEY_W).build());

        add(Action.FIND, Shortcut.builder().alt().shift().keyCode(KeyCodes.KEY_F).build());
        add(Action.FIND_IN_CONTENT, Shortcut.builder().ctrl().shift().keyCode(KeyCodes.KEY_F).build());
        add(Action.RECENT_ITEMS, Shortcut.builder().ctrl().keyCode(KeyCodes.KEY_E).build());
        add(Action.LOCATE, Shortcut.builder().alt().keyCode(KeyCodes.KEY_L).build());
        add(Action.HELP, Shortcut.builder().keyCode(KeyCodes.KEY_F1).build());
        add(Action.SHOW_KEY_BINDS, Shortcut.builder().keyCode(MyKeyCodes.KEY_QUESTION_MARK).build());
        add(Action.INFO, Shortcut.builder().keyCode(KeyCodes.KEY_I).build());
        add(Action.FOCUS_FILTER, Shortcut.builder().keyCode(MyKeyCodes.KEY_FORWARD_SLASH).build());
        add(Action.FOCUS_EXPLORER_FILTER, Shortcut.builder().ctrl().keyCode(MyKeyCodes.KEY_FORWARD_SLASH).build());

        addKeySequence(
                Action.FIND,
                Shortcut.builder().shift().keyCode(KeyCodes.KEY_SHIFT).build(),
                Shortcut.builder().shift().keyCode(KeyCodes.KEY_SHIFT).build());

        // Binds for Going To a single instance screen. Sort these by 2nd key
        addGotoKeySequence(Action.GOTO_APP_PERMS, KeyCodes.KEY_A);
        addGotoKeySequence(Action.GOTO_CACHES, KeyCodes.KEY_C);
        addGotoKeySequence(Action.GOTO_DEPENDENCIES, KeyCodes.KEY_D);
        addGotoKeySequence(Action.GOTO_EXPLORER_TREE, KeyCodes.KEY_E);
        addGotoKeySequence(Action.GOTO_INDEX_VOLUMES, KeyCodes.KEY_I);
        addGotoKeySequence(Action.GOTO_JOBS, KeyCodes.KEY_J);
        addGotoKeySequence(Action.GOTO_API_KEYS, KeyCodes.KEY_K);
        addGotoKeySequence(Action.GOTO_NODES, KeyCodes.KEY_N);
        addGotoKeySequence(Action.GOTO_PROPERTIES, KeyCodes.KEY_P);
        addGotoKeySequence(Action.GOTO_DATA_RETENTION, KeyCodes.KEY_R);
        addGotoKeySequence(Action.GOTO_SEARCH_RESULTS, KeyCodes.KEY_S);
        addGotoKeySequence(Action.GOTO_TASKS, KeyCodes.KEY_T);
        addGotoKeySequence(Action.GOTO_USER_PREFERENCES, KeyCodes.KEY_U);
        addGotoKeySequence(Action.GOTO_FS_VOLUMES, KeyCodes.KEY_V);
        addGotoKeySequence(Action.GOTO_USER_ACCOUNTS, KeyCodes.KEY_X);

        // Binds for creating a document. Sort these by 2nd key
        addCreateDocKeySequence(Action.CREATE_ELASTIC_INDEX, KeyCodes.KEY_C);
        addCreateDocKeySequence(Action.CREATE_DASHBOARD, KeyCodes.KEY_D);
        addCreateDocKeySequence(Action.CREATE_FEED, KeyCodes.KEY_E);
        addCreateDocKeySequence(Action.CREATE_FOLDER, KeyCodes.KEY_F);
        addCreateDocKeySequence(Action.CREATE_DICTIONARY, KeyCodes.KEY_I);
        addCreateDocKeySequence(Action.CREATE_LUCENE_INDEX, KeyCodes.KEY_L);
        addCreateDocKeySequence(Action.CREATE_DOCUMENTATION, KeyCodes.KEY_O);
        addCreateDocKeySequence(Action.CREATE_PIPELINE, KeyCodes.KEY_P);
        addCreateDocKeySequence(Action.CREATE_QUERY, KeyCodes.KEY_Q);
        addCreateDocKeySequence(Action.CREATE_ANALYTIC_RULE, KeyCodes.KEY_R);
        addCreateDocKeySequence(Action.CREATE_TEXT_CONVERTER, KeyCodes.KEY_T);
        addCreateDocKeySequence(Action.CREATE_VIEW, KeyCodes.KEY_V);
        addCreateDocKeySequence(Action.CREATE_XSLT, KeyCodes.KEY_X);

//        addKeySequence(Action.DOCUMENTATION, SUB_TAB_FIRST_KEY, KeyCodes.KEY_D);
//        addKeySequence(Action.SETTINGS, SUB_TAB_FIRST_KEY, KeyCodes.KEY_S);
    }

    public static void addCommand(final Action action, final Command command) {
        COMMANDS.put(action, command);
    }

    public static String getShortcut(final Action action) {
        final List<Shortcut> shortcuts = GwtNullSafe.list(ACTION_TO_SHORTCUTS_MAP.get(action));

        if (!shortcuts.isEmpty()) {
            // Get the primary shortcut
            return shortcuts.get(0).toString();
        } else {
            // Try key sequences instead
            final KeySequence keySequence = ACTION_TO_KEY_SEQUENCE_MAP.get(action);
            if (keySequence != null) {
                return keySequence.toString();
            } else {
                return null;
            }
        }
    }

    public static void consumeAction(final NativeEvent e,
                                     final Action action,
                                     final Runnable onAction) {

        final Action actualAction = test(e);
        if (actualAction == action && onAction != null) {
            onAction.run();
            // Caller has consumed it so stop it propagating further
            e.stopPropagation();
        }
    }

    /**
     * Tests the supplied {@link NativeEvent} to see if the key(s) are associated with an {@link Action}.
     * If a {@link Command} has been assigned to the {@link Action} then the command will be run and a
     * null {@link Action} will be returned.
     *
     * @return Matching {@link Action} or null
     */
    public static Action test(final NativeEvent e) {
        Action action = null;
        if (BrowserEvents.KEYDOWN.equals(e.getType())) {
            final Shortcut shortcut = getShortcut(e);
            action = testKeyDownEvent(e, shortcut);
            if (action == null) {
                action = onKeyDown(e, shortcut);
            }

            final Command command = COMMANDS.get(action);
            if (command == null) {
                // No command assigned so return the action for the caller to do something with
//                action = onKeyDown(e, shortcut);
            } else {
                // Swallow the event and run the command
                e.preventDefault();
                e.stopPropagation();
                command.execute();
                // Clear the action as we have executed the command
                action = null;
            }
        } else if (BrowserEvents.KEYUP.equals(e.getType())) {
            final Shortcut shortcut = getShortcut(e);
            testKeyUpEvent(e, shortcut);
        }
        return action;
    }

    private static boolean shouldCheckKeySequence(final NativeEvent e) {
        final EventTarget eventTarget = e.getEventTarget();
        final boolean shouldCheck;
        if (eventTarget != null) {
            if (Element.is(eventTarget)) {
                final Element element = Element.as(eventTarget);
                final String tagName = element.getTagName();
                // TODO this is really clunky. Need to create our own textbox and text area
                //  components so that we can control the key events
                shouldCheck = !isTextBox(element, tagName)
                        && !"TEXTAREA".equalsIgnoreCase(tagName);

//                final String className = GwtNullSafe.string(element.getClassName());
//                final String type = element.getAttribute("type");
//                GWT.log("className: " + className + " tagName: " + element.getTagName()
//                        + " type: " + type + " shouldCheck: " + shouldCheck);
            } else {
                shouldCheck = true;
            }
        } else {
            shouldCheck = true;
        }
//        GWT.log("shouldCheck: " + shouldCheck);
        return shouldCheck;
    }

    private static boolean isTextBox(final Element element, final String tagName) {
        return "INPUT".equalsIgnoreCase(tagName) && isTextualInputType(element);
    }

    private static boolean isTextualInputType(final Element element) {
        // These are the type of input element that we want to stop
        final String type = element.getAttribute("type");
        return GwtNullSafe.isBlankString(type)
                || "text".equalsIgnoreCase(type)
                || "password".equalsIgnoreCase(type)
                || "search".equalsIgnoreCase(type)
                || "number".equalsIgnoreCase(type)
                || "url".equalsIgnoreCase(type);
    }

    private static Action testKeyDownEvent(final NativeEvent e,
                                           final Shortcut shortcut) {
        Action action = null;
        logKey(e);
        if (shouldCheckKeySequence(e)) {
            if (firstInSequence == null) {
                if (KEY_SEQUENCES_BY_FIRST_KEY.containsKey(shortcut)) {
                    // Might be a key sequence so allow the user a short time to press
                    // the second key bind in the sequence
                    firstInSequence = shortcut;
                    eventTargetKey = deriveEventTargetKey(e);
                    KEY_SEQUENCE_TIMER.cancel();
                    KEY_SEQUENCE_TIMER.schedule(KEY_SEQUENCE_TIMER_DELAY);
                }
            } else {
                // Check for key up in between so we know it is two distinct key binds
                if (seenKeyUpBetween
                        && KEY_SEQUENCES_BY_SECOND_KEY.containsKey(shortcut)
                        && Objects.equals(eventTargetKey, deriveEventTargetKey(e))) {
                    final KeySequence keySequence = new KeySequence(firstInSequence, shortcut);
                    action = KEY_SEQUENCE_TO_ACTION_MAP.get(keySequence);
//                    GWT.log("Got action: " + action + " for sequence " + keySequence);
                    // We have seen a second key so regardless of whether the pair matched on one of our binds
                    // clear out the state ready for another go
                    clearKeySequenceState();
                    KEY_SEQUENCE_TIMER.cancel();
                }
            }
        }
        return action;
    }

    private static void testKeyUpEvent(final NativeEvent e,
                                       final Shortcut shortcut) {
        logKey(e);
        if (shouldCheckKeySequence(e)) {
            if (firstInSequence != null
                    && Objects.equals(eventTargetKey, deriveEventTargetKey(e))) {
                seenKeyUpBetween = true;
            }
        }
    }

    /**
     * Use the abs position of the event target as a key, so we can tell different keyDown events
     * apart, e.g. if the exp tree does not consume an event then it will bubble up to the
     * MainPresenter to check.
     */
    private static String deriveEventTargetKey(final NativeEvent e) {
        return GwtNullSafe.get(
                e.getCurrentEventTarget(),
                KeyBinding::getElement,
                elm ->
                        elm.getAbsoluteLeft() + ":" + elm.getAbsoluteTop());
    }

    private static Element getElement(final EventTarget eventTarget) {
        if (eventTarget != null) {
            try {
                if (Element.is(eventTarget)) {
                    return Element.as(eventTarget);
                }
            } catch (Exception e) {
                // Just swallow
            }
        }
        return null;
    }

    private static void clearKeySequenceState() {
//        GWT.log("Clearing key sequence state");
        firstInSequence = null;
        seenKeyUpBetween = false;
        eventTargetKey = null;
    }

    private static void logKey(final NativeEvent e) {
        final List<String> keys = new ArrayList<>();
        if (e.getShiftKey()) {
            keys.add("shift");
        }
        if (e.getCtrlKey()) {
            keys.add("ctrl");
        }
        if (e.getAltKey()) {
            keys.add("alt");
        }
        if (e.getMetaKey()) {
            keys.add("meta");
        }
        keys.add(KeyBinding.keyCodeToString(e.getKeyCode()));
//        GWT.log(e.getType()
//                + " (" + e.getKeyCode() + ") - "
//                + String.join("+", keys)
//                + ", firstInSequence: " + firstInSequence
//                + ", eventTargetKey: " + deriveEventTargetKey(e));
    }

    private static Action onKeyDown(final NativeEvent e,
                                    final Shortcut shortcut) {

//        GWT.log("KEYDOWN = " + shortcut);

        final Binding binding = getBinding(shortcut);
        Action action = null;
//        Command command = null;
        if (binding != null) {

//            GWT.log("BINDING = " + binding);

            action = binding.action;
//            command = COMMANDS.get(action);
//            if (command != null) {
//                GWT.log("COMMAND = " + command);
//
//                e.preventDefault();
//                e.stopPropagation();
//                command.execute();
//            } else {
//                return action;
//            }
        }
        return action;
    }

    private static Shortcut getShortcut(final NativeEvent e) {
        return Shortcut.builder()
                .keyCode(e.getKeyCode())
                .shift(e.getShiftKey())
                .ctrl(e.getCtrlKey())
                .alt(e.getAltKey())
                .meta(e.getMetaKey())
                .build();
    }

    private static Binding getBinding(final Shortcut shortcut) {
        Binding binding = null;
        // Favour exact matches
        binding = GwtNullSafe.get(SHORTCUT_TO_ACTION_MAP.get(shortcut),
                action -> new Binding.Builder().action(action).shortcut(shortcut).build());

        // TODO Commented this out as it seems a tad risky, better to be explicit with binds
//        if (binding == null) {
//            // If we have a binding that isn't exact but has the same keycode without specifying modifiers then
//            // return the bound action.
//            for (final Binding binding : BINDINGS) {
//                if (!binding.shortcut.hasModifiers() && binding.shortcut.keyCode == shortcut.keyCode) {
//                    return binding;
//                }
//            }
//        }
        return binding;
    }

    static void add(final Action action,
                    final boolean ctrl,
                    final int... keyCode) {
        for (int code : keyCode) {
            add(action, Shortcut.builder().ctrl(ctrl).keyCode(code).build());
        }
    }

    static void add(final Action action, final int... keyCode) {
        for (int code : keyCode) {
            add(action, Shortcut.builder()
                    .keyCode(code)
                    .build());
        }
    }

    static void add(final Action action, final Shortcut... shortcuts) {
        for (final Shortcut shortcut : shortcuts) {
            final List<Shortcut> shortcuts2 = ACTION_TO_SHORTCUTS_MAP.computeIfAbsent(action, k -> new ArrayList<>());
            if (shortcuts2.contains(shortcut)) {
                throw new RuntimeException("Duplicate shortcut " + shortcut + " for action " + action
                        + ", existing shortcuts: "
                        + shortcuts2.stream().map(Objects::toString).collect(Collectors.joining(", ")));
            }
            shortcuts2.add(shortcut);
            SHORTCUT_TO_ACTION_MAP.put(shortcut, action);
//            BINDINGS.add(new Binding.Builder()
//                    .shortcut(shortcut)
//                    .action(action)
//                    .build());
        }
    }

    static void addGotoKeySequence(final Action action,
                                   final int keyCode2) {
        addKeySequence(
                action,
                Shortcut.unmodifiedKey(GOTO_FIRST_KEY),
                Shortcut.unmodifiedKey(keyCode2));
    }

    static void addCreateDocKeySequence(final Action action,
                                        final int keyCode2) {
        addKeySequence(
                action,
                Shortcut.unmodifiedKey(CREATE_DOC_FIRST_KEY),
                Shortcut.unmodifiedKey(keyCode2));
    }

    static void addKeySequence(final Action action,
                               final int keyCode1,
                               final int keyCode2) {
        addKeySequence(
                action,
                Shortcut.unmodifiedKey(keyCode1),
                Shortcut.unmodifiedKey(keyCode2));
    }

    static void addKeySequence(final Action action,
                               final Shortcut shortcut1,
                               final Shortcut shortcut2) {
        final KeySequence keySequence = new KeySequence(shortcut1, shortcut2);
        final KeySequence existingKeySequence = ACTION_TO_KEY_SEQUENCE_MAP.put(action, keySequence);
        if (existingKeySequence != null) {
            throw new RuntimeException("Action " + action
                    + " is already bound to key sequence " + existingKeySequence + ". "
                    + "Tyring to bind it to " + keySequence);
        }
        KEY_SEQUENCE_TO_ACTION_MAP.put(keySequence, action);

        KEY_SEQUENCES_BY_FIRST_KEY.put(keySequence.getFirst(), keySequence);
        KEY_SEQUENCES_BY_SECOND_KEY.put(keySequence.getSecond(), keySequence);
    }

    public static String keyCodeToString(final int keyCode) {
        //noinspection EnhancedSwitchMigration // cos GWT
        switch (keyCode) {
            case KeyCodes.KEY_LEFT:
                return "left-arrow";
            case KeyCodes.KEY_RIGHT:
                return "right-arrow";
            case KeyCodes.KEY_UP:
                return "up-arrow";
            case KeyCodes.KEY_DOWN:
                return "down-arrow";
            case KeyCodes.KEY_SPACE:
                return "space";
            case KeyCodes.KEY_CTRL:
                return "ctrl";
            case KeyCodes.KEY_ALT:
                return "alt";
            case KeyCodes.KEY_SHIFT:
                return "shift";
            case KeyCodes.KEY_TAB:
                return "tab";
            case KeyCodes.KEY_ESCAPE:
                return "esc";
            case KeyCodes.KEY_ENTER:
                return "enter";
            case KeyCodes.KEY_MAC_ENTER:
                return "mac-enter";
            case KeyCodes.KEY_BACKSPACE:
                return "backspace";
            case KeyCodes.KEY_DELETE:
                return "delete";
            case KeyCodes.KEY_INSERT:
                return "insert";
            case KeyCodes.KEY_F1:
                return "F1";
            case KeyCodes.KEY_F2:
                return "F2";
            case KeyCodes.KEY_F3:
                return "F3";
            case KeyCodes.KEY_F4:
                return "F4";
            case KeyCodes.KEY_F5:
                return "F5";
            case KeyCodes.KEY_F6:
                return "F6";
            case KeyCodes.KEY_F7:
                return "F7";
            case KeyCodes.KEY_F8:
                return "F8";
            case KeyCodes.KEY_F9:
                return "F9";
            case KeyCodes.KEY_F10:
                return "F10";
            case KeyCodes.KEY_END:
                return "end";
            case KeyCodes.KEY_HOME:
                return "home";
            case KeyCodes.KEY_PAGEUP:
                return "pageup";
            case KeyCodes.KEY_PAGEDOWN:
                return "pagedown";
            default:
                return String.valueOf(((char) keyCode)).toLowerCase();
        }
    }


    // --------------------------------------------------------------------------------


    private static class MyKeyCodes {

        /**
         * Question mark '?'
         */
        private static final int KEY_QUESTION_MARK = 63;
        /**
         * Question mark '?'
         */
        private static final int KEY_FORWARD_SLASH = 191;
    }


    // --------------------------------------------------------------------------------


    public enum Action {
        MOVE_UP,
        MOVE_DOWN,
        MOVE_LEFT,
        MOVE_RIGHT,
        MOVE_PAGE_DOWN,
        MOVE_PAGE_UP,
        MOVE_START,
        MOVE_END,
        /**
         * 'e' - E.g. to edit the current item
         */
        EDIT,
        CLOSE,
        /**
         * enter
         */
        EXECUTE,
        /**
         * space
         */
        SELECT,
        MENU,
        SELECT_ALL,
        /**
         * ctrl-enter
         */
        OK,
        ITEM_SAVE,
        ITEM_SAVE_ALL,
        ITEM_CLOSE,
        ITEM_CLOSE_ALL,
        FIND,
        FIND_IN_CONTENT,
        RECENT_ITEMS,
        LOCATE,
        HELP,
        DOCUMENTATION,
        SHOW_KEY_BINDS,
        INFO,
        FOCUS_FILTER,
        FOCUS_EXPLORER_FILTER,
        SETTINGS,

        // GOTO key sequences
        GOTO_PROPERTIES,
        GOTO_API_KEYS,
        GOTO_CACHES,
        GOTO_DATA_RETENTION,
        GOTO_DEPENDENCIES,
        GOTO_JOBS,
        GOTO_NODES,
        GOTO_TASKS,
        GOTO_EXPLORER_TREE,
        GOTO_SEARCH_RESULTS,
        GOTO_FS_VOLUMES,
        GOTO_APP_PERMS,
        GOTO_INDEX_VOLUMES,
        GOTO_USER_ACCOUNTS,
        GOTO_USER_PREFERENCES,

        // Create Doc key sequences
        CREATE_ELASTIC_INDEX, // C
        CREATE_DASHBOARD, // D
        CREATE_FEED, // E
        CREATE_FOLDER, // F
        CREATE_DICTIONARY, // I
        CREATE_LUCENE_INDEX, // L
        CREATE_DOCUMENTATION, // O
        CREATE_PIPELINE, // P
        CREATE_QUERY, // Q
        CREATE_ANALYTIC_RULE, // R
        CREATE_TEXT_CONVERTER, // T
        CREATE_VIEW, // V
        CREATE_XSLT, // X
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

        public static Shortcut unmodifiedKey(final int keyCode) {
            return new Shortcut(keyCode, false, false, false, false);
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
            final List<String> keysPressed = new ArrayList<>();
            if (ctrl) {
                keysPressed.add("ctrl");
            }
            if (alt) {
                keysPressed.add("alt");
            }
            if (shift) {
                keysPressed.add("shift");
            }
            if (meta) {
                keysPressed.add("meta");
            }
            keysPressed.add(keyCodeToString(keyCode));
            return java.lang.String.join("+", keysPressed);
        }

        public boolean hasModifiers() {
            return shift || ctrl || alt || meta;
        }

        public static Builder builder() {
            return new Builder();
        }
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private int keyCode;
        private boolean shift;
        private boolean ctrl;
        private boolean alt;
        private boolean meta;

        private Builder() {
        }

        public Builder keyCode(final int keyCode) {
            this.keyCode = keyCode;
            return this;
        }

        public Builder shift(final boolean shift) {
            this.shift = shift;
            return this;
        }

        public Builder shift() {
            this.shift = true;
            return this;
        }

        public Builder ctrl() {
            this.ctrl = true;
            return this;
        }

        public Builder ctrl(final boolean ctrl) {
            this.ctrl = ctrl;
            return this;
        }

        public Builder alt() {
            this.alt = true;
            return this;
        }

        public Builder alt(final boolean alt) {
            this.alt = alt;
            return this;
        }

        public Builder meta() {
            this.meta = true;
            return this;
        }

        public Builder meta(final boolean meta) {
            this.meta = meta;
            return this;
        }

        private Shortcut build() {
            return new Shortcut(keyCode, shift, ctrl, alt, meta);
        }
    }


    // --------------------------------------------------------------------------------


    private static class KeySequence {

        private final List<Shortcut> shortcuts;

        // For the moment only support sequences of two shortcuts, e.g. 'g,p'
        private KeySequence(final Shortcut shortcut1,
                            final Shortcut shortcut2) {
            Objects.requireNonNull(shortcut1);
            Objects.requireNonNull(shortcut2);
            this.shortcuts = Arrays.asList(shortcut1, shortcut2);
        }

        public Shortcut getFirst() {
            return shortcuts.get(0);
        }

        public Shortcut getSecond() {
            return shortcuts.get(1);
        }

        @Override
        public String toString() {
            return shortcuts.stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining(""));
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final KeySequence that = (KeySequence) object;
            return Objects.equals(shortcuts, that.shortcuts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shortcuts);
        }
    }
}
