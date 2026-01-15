/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.widget.util.client;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.shared.QueryDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;
import stroom.view.shared.ViewDoc;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class KeyBinding {

    private static final Map<Shortcut, Action> SHORTCUT_TO_ACTION_MAP = new HashMap<>();
    private static final Map<Action, List<Shortcut>> ACTION_TO_SHORTCUTS_MAP = new HashMap<>();

    private static final Map<Action, Command> COMMANDS = new HashMap<>();

    private static final Map<String, Action> DOC_TYPE_TO_ACTION_MAP = new HashMap<>();

    private static final int GOTO_FIRST_KEY = KeyCodes.KEY_G;
    private static final int CREATE_DOC_FIRST_KEY = KeyCodes.KEY_C;
//    private static final int SUB_TAB_FIRST_KEY = KeyCodes.KEY_T;

    private static final KeySequences KEY_SEQUENCES = new KeySequences();

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

        KEY_SEQUENCES.addKeySequence(Action.FIND, KeyCodes.KEY_SHIFT, KeyCodes.KEY_SHIFT);

        // Binds for Going To a single instance screen. Sort these by 2nd key
        addGotoKeySequence(Action.GOTO_APP_PERMS, KeyCodes.KEY_A);
        addGotoKeySequence(Action.GOTO_ANNOTATIONS, KeyCodes.KEY_B);
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
        addGotoKeySequence(Action.GOTO_USER_GROUPS, KeyCodes.KEY_G);
        addGotoKeySequence(Action.GOTO_USER_PREFERENCES, KeyCodes.KEY_U);
        addGotoKeySequence(Action.GOTO_FS_VOLUMES, KeyCodes.KEY_V);
        addGotoKeySequence(Action.GOTO_USER_ACCOUNTS, KeyCodes.KEY_X);

        // Binds for creating a document. Sort these by 2nd key
        addCreateDocKeySequence(Action.CREATE_ANNOTATION, KeyCodes.KEY_A);
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

        // Map doc types to the create actions, so we can get the action for a doc
        DOC_TYPE_TO_ACTION_MAP.put(ElasticIndexDoc.TYPE, Action.CREATE_ELASTIC_INDEX);
        DOC_TYPE_TO_ACTION_MAP.put(DashboardDoc.TYPE, Action.CREATE_DASHBOARD);
        DOC_TYPE_TO_ACTION_MAP.put(FeedDoc.TYPE, Action.CREATE_FEED);
        DOC_TYPE_TO_ACTION_MAP.put(ExplorerConstants.FOLDER_TYPE, Action.CREATE_FOLDER);
        DOC_TYPE_TO_ACTION_MAP.put(DictionaryDoc.TYPE, Action.CREATE_DICTIONARY);
        DOC_TYPE_TO_ACTION_MAP.put(LuceneIndexDoc.TYPE, Action.CREATE_LUCENE_INDEX);
        DOC_TYPE_TO_ACTION_MAP.put(DocumentationDoc.TYPE, Action.CREATE_DOCUMENTATION);
        DOC_TYPE_TO_ACTION_MAP.put(PipelineDoc.TYPE, Action.CREATE_PIPELINE);
        DOC_TYPE_TO_ACTION_MAP.put(QueryDoc.TYPE, Action.CREATE_QUERY);
        DOC_TYPE_TO_ACTION_MAP.put(AnalyticRuleDoc.TYPE, Action.CREATE_ANALYTIC_RULE);
        DOC_TYPE_TO_ACTION_MAP.put(TextConverterDoc.TYPE, Action.CREATE_TEXT_CONVERTER);
        DOC_TYPE_TO_ACTION_MAP.put(ViewDoc.TYPE, Action.CREATE_VIEW);
        DOC_TYPE_TO_ACTION_MAP.put(XsltDoc.TYPE, Action.CREATE_XSLT);

//        addKeySequence(Action.DOCUMENTATION, SUB_TAB_FIRST_KEY, KeyCodes.KEY_D);
//        addKeySequence(Action.SETTINGS, SUB_TAB_FIRST_KEY, KeyCodes.KEY_S);
    }

    public static void addCommand(final Action action, final Command command) {
        COMMANDS.put(action, command);
    }

    public static Optional<Action> getCreateActionByType(final String documentType) {
        if (NullSafe.isBlankString(documentType)) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(DOC_TYPE_TO_ACTION_MAP.get(documentType));
        }
    }

    public static String getShortcut(final Action action) {
        final List<Shortcut> shortcuts = NullSafe.list(ACTION_TO_SHORTCUTS_MAP.get(action));

        if (!shortcuts.isEmpty()) {
            // Get the primary shortcut
            return shortcuts.get(0).toString();
        } else {
            // Try key sequences instead
            return KEY_SEQUENCES.getShortcut(action);
        }
    }

    public static Action test(final NativeEvent e) {
        Action action = null;
        logKey(e);

        // We don't want to test key binds in text input else we pick up things like the user typing '/'
        if (BrowserEvents.KEYDOWN.equals(e.getType())) {
//            GWT.log("KeyBinding KEYDOWN " + keyCodeToString(e.getKeyCode()));

            // Only test a basic action if we have a modifier.
            if (e.getAltKey() || e.getCtrlKey() || e.getMetaKey()) {
                // If there is a modifier then this isn't going to be a key sequence.
                KEY_SEQUENCES.clear();
                action = getKeyDownAction(e);
                action = testAction(e, action);

            } else if (isInput(e)) {
                // We will support some single key bindings in text boxes.
                if (e.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                    KEY_SEQUENCES.clear();
                    action = getKeyDownAction(e);
                    action = testAction(e, action);
                } else if (e.getKeyCode() == KeyCodes.KEY_SHIFT) {
                    // Allow double shift keysequence in text area.
                    KEY_SEQUENCES.onKeyDown(e);
                } else {
                    KEY_SEQUENCES.clear();
                }

            } else {
                // See if this is potentially part of a sequence.
                if (!KEY_SEQUENCES.onKeyDown(e)) {
                    // It isn't part of a sequence so test if it is a simple binding.
                    action = getKeyDownAction(e);
                    if (action != null) {
                        action = testAction(e, action);
                    }
                }
            }

        } else if (BrowserEvents.KEYUP.equals(e.getType())) {
//            GWT.log("KeyBinding KEYUP " + keyCodeToString(e.getKeyCode()));

            action = KEY_SEQUENCES.onKeyUp(e);
            action = testAction(e, action);
        }
        return action;
    }

    private static Action testAction(final NativeEvent e,
                                     final Action action) {
        // Find a basic modifier binding.
        if (action != null) {
            final Command command = COMMANDS.get(action);
            if (command != null) {
                // Swallow the event and run the command
                e.preventDefault();
                e.stopPropagation();
                command.execute();
                // Clear the action as we have executed the command
                return null;
            }
        }
        return action;
    }


    private static boolean isInput(final NativeEvent e) {
        final EventTarget eventTarget = e.getEventTarget();
        final boolean isInput;
        if (eventTarget != null) {
            if (Element.is(eventTarget)) {
                final Element element = Element.as(eventTarget);
                final String tagName = element.getTagName();
                // TODO this is really clunky. Need to create our own textbox and text area
                //  components so that we can control the key events
                isInput = isTextBox(element, tagName)
                          || "TEXTAREA".equalsIgnoreCase(tagName);

//                final String className = NullSafe.string(element.getClassName());
//                final String type = element.getAttribute("type");
//                GWT.log("className: " + className + " tagName: " + element.getTagName()
//                        + " type: " + type + " shouldCheck: " + shouldCheck);
            } else {
                isInput = false;
            }
        } else {
            isInput = false;
        }
        return isInput;
    }

    private static boolean isTextBox(final Element element, final String tagName) {
        return "INPUT".equalsIgnoreCase(tagName)
               && isTextualInputType(element);
    }

    private static boolean isTextualInputType(final Element element) {
        // These are the type of input element that we want to stop
        final String type = element.getAttribute("type");
        return NullSafe.isBlankString(type)
               || "text".equalsIgnoreCase(type)
               || "password".equalsIgnoreCase(type)
               || "search".equalsIgnoreCase(type)
               || "number".equalsIgnoreCase(type)
               || "url".equalsIgnoreCase(type);
    }

    private static void logKey(final NativeEvent e) {
//        final List<String> keys = new ArrayList<>();
//        if (e.getShiftKey()) {
//            keys.add("Shift");
//        }
//        if (e.getCtrlKey()) {
//            keys.add("Ctrl");
//        }
//        if (e.getAltKey()) {
//            keys.add("Alt");
//        }
//        if (e.getMetaKey()) {
//            keys.add("Meta");
//        }
//        keys.add(KeyBinding.keyCodeToString(e.getKeyCode()));
//        GWT.log(e.getType()
//                + " (" + e.getKeyCode() + ") - "
//                + String.join("+", keys)
//                + ", firstInSequence: " + firstInSequence
//                + ", eventTargetKey: " + deriveEventTargetKey(e));
    }

    private static Action getKeyDownAction(final NativeEvent e) {

        final Shortcut shortcut = getShortcut(e);

//        GWT.log("KEYDOWN = " + shortcut);

        final Binding binding = getBinding(shortcut);
        Action action = null;
//        Command command = null;
        if (binding != null) {

//            GWT.log("BINDING = " + binding);

            action = binding.action;
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
        binding = NullSafe.get(SHORTCUT_TO_ACTION_MAP.get(shortcut),
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
        for (final int code : keyCode) {
            add(action, Shortcut.builder()
                    .ctrl(ctrl)
                    .keyCode(code)
                    .build());
        }
    }

    static void add(final Action action, final int... keyCode) {
        for (final int code : keyCode) {
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
                                           + shortcuts2.stream()
                                                   .map(Objects::toString)
                                                   .collect(Collectors.joining(", ")));
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
        KEY_SEQUENCES.addKeySequence(
                action,
                GOTO_FIRST_KEY,
                keyCode2);
    }

    static void addCreateDocKeySequence(final Action action,
                                        final int keyCode2) {
        KEY_SEQUENCES.addKeySequence(
                action,
                CREATE_DOC_FIRST_KEY,
                keyCode2);
    }

    public static String keyCodeToString(final int keyCode) {
        //noinspection EnhancedSwitchMigration // cos GWT
        switch (keyCode) {
            case KeyCodes.KEY_LEFT:
                return "Left-Arrow";
            case KeyCodes.KEY_RIGHT:
                return "Right-Arrow";
            case KeyCodes.KEY_UP:
                return "Up-Arrow";
            case KeyCodes.KEY_DOWN:
                return "Down-Arrow";
            case KeyCodes.KEY_SPACE:
                return "Space";
            case KeyCodes.KEY_CTRL:
                return "Ctrl";
            case KeyCodes.KEY_ALT:
                return "Alt";
            case KeyCodes.KEY_SHIFT:
                return "Shift";
            case KeyCodes.KEY_TAB:
                return "Tab";
            case KeyCodes.KEY_ESCAPE:
                return "Esc";
            case KeyCodes.KEY_ENTER:
                return "Enter";
            case KeyCodes.KEY_MAC_ENTER:
                return "Mac-Enter";
            case KeyCodes.KEY_BACKSPACE:
                return "Backspace";
            case KeyCodes.KEY_DELETE:
                return "Delete";
            case KeyCodes.KEY_INSERT:
                return "Insert";
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
                return "End";
            case KeyCodes.KEY_HOME:
                return "Home";
            case KeyCodes.KEY_PAGEUP:
                return "Pageup";
            case KeyCodes.KEY_PAGEDOWN:
                return "Pagedown";
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
        GOTO_ANNOTATIONS,
        GOTO_PROPERTIES,
        GOTO_API_KEYS,
        GOTO_CACHES,
        GOTO_DATA_RETENTION,
        GOTO_CONTENT_TEMPLATES,
        GOTO_DEPENDENCIES,
        GOTO_JOBS,
        GOTO_NODES,
        GOTO_TASKS,
        GOTO_EXPLORER_TREE,
        GOTO_SEARCH_RESULTS,
        GOTO_FS_VOLUMES,
        GOTO_APP_PERMS,
        GOTO_DOC_PERMS,
        GOTO_INDEX_VOLUMES,
        GOTO_USER_ACCOUNTS,
        GOTO_USER_PROFILE,
        GOTO_USERS,
        GOTO_USER_GROUPS,
        GOTO_USER_PREFERENCES,
        GOTO_USER_PERMISSION_REPORT,

        // Create Doc key sequences
        CREATE_ANNOTATION, // A
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
                keysPressed.add("Ctrl");
            }
            if (alt) {
                keysPressed.add("Alt");
            }
            if (shift) {
                keysPressed.add("Shift");
            }
            if (meta) {
                keysPressed.add("Meta");
            }
            keysPressed.add(keyCodeToString(keyCode));
            return java.lang.String.join("+", keysPressed);
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
}
