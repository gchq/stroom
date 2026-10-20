/*
 * Copyright 2021 Crown Copyright
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/// The keyboard shortcuts of the user interface, and the handling of key events to find the
/// [Action] a key press is bound to.
///
/// There are two kinds of binding:
///
/// * Fixed bindings, which the user can't change, e.g. the arrow keys to move around a list, or
///   Escape to close a dialog. These follow the conventions that assistive technology expects.
/// * Configurable bindings, which have defaults that the user can change in their preferences
///   (see [#applyUserBindings(Map, boolean)]), e.g. `Ctrl+S` to save. Only actions with an
///   [Action#getCategory()] are configurable.
///
/// The user can also turn off all character key shortcuts, e.g. `W` to move up a list or `G U`
/// to go to the user preferences, so that speech input users don't trigger them by accident
/// (WCAG 2.1.4).
public class KeyBinding {

    // Built-in bindings that can't be changed, by action
    private static final Map<Action, List<KeyStroke>> FIXED_BINDINGS = new LinkedHashMap<>();
    // The default bindings of the configurable actions, which the user can change
    private static final Map<Action, List<KeyShortcut>> DEFAULT_BINDINGS = new LinkedHashMap<>();

    private static final Map<Action, Command> COMMANDS = new HashMap<>();

    private static final Map<String, Action> DOC_TYPE_TO_ACTION_MAP = new HashMap<>();

    // The bindings in use, made from the fixed and default bindings and the user's changes
    private static final Map<KeyStroke, Action> KEY_STROKE_TO_ACTION_MAP = new HashMap<>();
    private static final Map<Action, List<KeyShortcut>> ACTION_TO_SHORTCUTS_MAP = new HashMap<>();
    private static KeySequences keySequences = new KeySequences();

    // Whether a key recorder has the focus, see #setRecording
    private static boolean recording;

    static {
        addFixed(Action.MOVE_UP, KeyCodes.KEY_UP);
        addFixed(Action.MOVE_DOWN, KeyCodes.KEY_DOWN);
        addFixed(Action.MOVE_LEFT, KeyCodes.KEY_LEFT);
        addFixed(Action.MOVE_RIGHT, KeyCodes.KEY_RIGHT);
        addFixed(Action.MOVE_PAGE_DOWN, KeyCodes.KEY_PAGEDOWN);
        addFixed(Action.MOVE_PAGE_UP, KeyCodes.KEY_PAGEUP);
        addFixed(Action.MOVE_START, KeyCodes.KEY_HOME);
        addFixed(Action.MOVE_END, KeyCodes.KEY_END);
        addFixed(Action.CLOSE, KeyCodes.KEY_ESCAPE);
        addFixed(Action.EXECUTE, KeyCodes.KEY_ENTER, KeyCodes.KEY_MAC_ENTER, '\n');
        addFixed(Action.SELECT, KeyCodes.KEY_SPACE);
        addFixed(Action.SELECT_ALL, KeyStroke.builder().ctrl().keyCode(KeyCodes.KEY_A).build());
        addFixed(Action.MENU, KeyStroke.builder().alt().keyCode(KeyCodes.KEY_CONTEXT_MENU).build());
        addFixed(Action.OK,
                KeyStroke.builder().ctrl().keyCode(KeyCodes.KEY_ENTER).build(),
                KeyStroke.builder().ctrl().keyCode(KeyCodes.KEY_MAC_ENTER).build(),
                KeyStroke.builder().ctrl().keyCode('\n').build());

        // Character keys to move around lists, which the user can change or turn off
        addDefault(Action.MOVE_UP, "W", "K");
        addDefault(Action.MOVE_DOWN, "S", "J");
        addDefault(Action.MOVE_LEFT, "A", "H");
        addDefault(Action.MOVE_RIGHT, "D", "L");

        addDefault(Action.ITEM_SAVE, "Ctrl+S");
        addDefault(Action.ITEM_SAVE_ALL, "Ctrl+Shift+S");
        addDefault(Action.ITEM_CLOSE, "Alt+W");
        addDefault(Action.ITEM_CLOSE_ALL, "Alt+Shift+W");

        addDefault(Action.FIND, "Alt+Shift+F", "Shift Shift");
        addDefault(Action.FIND_IN_CONTENT, "Ctrl+Shift+F");
        addDefault(Action.RECENT_ITEMS, "Ctrl+E");
        addDefault(Action.LOCATE, "Alt+L");
        addDefault(Action.MAXIMISE_PANE, "Alt+M");
        addDefault(Action.HELP, "F1");
        addDefault(Action.FOCUS_FILTER, "/");
        addDefault(Action.FOCUS_EXPLORER_FILTER, "Ctrl+/");

        // Binds for Going To a single instance screen. Sort these by 2nd key
        addDefault(Action.GOTO_APP_PERMS, "G A");
        addDefault(Action.GOTO_ANNOTATIONS, "G B");
        addDefault(Action.GOTO_CACHES, "G C");
        addDefault(Action.GOTO_DEPENDENCIES, "G D");
        addDefault(Action.GOTO_EXPLORER_TREE, "G E");
        addDefault(Action.GOTO_USER_GROUPS, "G G");
        addDefault(Action.GOTO_INDEX_VOLUMES, "G I");
        addDefault(Action.GOTO_JOBS, "G J");
        addDefault(Action.GOTO_API_KEYS, "G K");
        addDefault(Action.GOTO_NODES, "G N");
        addDefault(Action.GOTO_PROPERTIES, "G P");
        addDefault(Action.GOTO_DATA_RETENTION, "G R");
        addDefault(Action.GOTO_SEARCH_RESULTS, "G S");
        addDefault(Action.GOTO_TASKS, "G T");
        addDefault(Action.GOTO_USER_PREFERENCES, "G U");
        addDefault(Action.GOTO_FS_VOLUMES, "G V");
        addDefault(Action.GOTO_USER_ACCESS, "G W");
        addDefault(Action.GOTO_USER_ACCOUNTS, "G X");
        addDefault(Action.GOTO_SIGNING_KEYS, "G Y");

        // Binds for creating a document. Sort these by 2nd key
        addDefault(Action.CREATE_ANNOTATION, "C A");
        addDefault(Action.CREATE_ELASTIC_INDEX, "C C");
        addDefault(Action.CREATE_DASHBOARD, "C D");
        addDefault(Action.CREATE_FEED, "C E");
        addDefault(Action.CREATE_FOLDER, "C F");
        addDefault(Action.CREATE_DICTIONARY, "C I");
        addDefault(Action.CREATE_LUCENE_INDEX, "C L");
        addDefault(Action.CREATE_DOCUMENTATION, "C O");
        addDefault(Action.CREATE_PIPELINE, "C P");
        addDefault(Action.CREATE_QUERY, "C Q");
        addDefault(Action.CREATE_ANALYTIC_RULE, "C R");
        addDefault(Action.CREATE_TEXT_CONVERTER, "C T");
        addDefault(Action.CREATE_VIEW, "C V");
        addDefault(Action.CREATE_XSLT, "C X");

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

        applyUserBindings(Collections.emptyMap(), true);
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

    /// The text of the main shortcut bound to an action, e.g. to show beside a menu item. This
    /// reflects the user's changes to the bindings.
    ///
    /// @param action The action.
    /// @return The text form of the action's first shortcut, e.g. `Ctrl+S` or `G U`, or null if
    /// the action has no shortcut.
    public static String getShortcut(final Action action) {
        final List<KeyShortcut> shortcuts = NullSafe.list(ACTION_TO_SHORTCUTS_MAP.get(action));
        return shortcuts.isEmpty()
                ? null
                : shortcuts.get(0).toString();
    }

    /// @return The actions whose bindings the user can change, in the order to show them.
    public static List<Action> getConfigurableActions() {
        final List<Action> actions = new ArrayList<>();
        for (final Action action : Action.values()) {
            if (action.isConfigurable()) {
                actions.add(action);
            }
        }
        return actions;
    }

    /// @param action An action.
    /// @return The action's built-in shortcuts, which the user can't change, e.g. the Up arrow to
    /// move up a list. Empty if it has none.
    public static List<KeyShortcut> getFixedShortcuts(final Action action) {
        return NullSafe.list(FIXED_BINDINGS.get(action))
                .stream()
                .map(KeyShortcut::of)
                .collect(Collectors.toList());
    }

    /// @param action A configurable action.
    /// @return The action's default shortcuts, before any changes by the user. Empty if it has
    /// none.
    public static List<KeyShortcut> getDefaultShortcuts(final Action action) {
        return NullSafe.list(DEFAULT_BINDINGS.get(action));
    }

    /// @return The default shortcuts of all the configurable actions, including those with none.
    public static Map<Action, List<KeyShortcut>> getDefaultBindings() {
        final Map<Action, List<KeyShortcut>> bindings = new LinkedHashMap<>();
        for (final Action action : getConfigurableActions()) {
            bindings.put(action, new ArrayList<>(getDefaultShortcuts(action)));
        }
        return bindings;
    }

    /// Makes the shortcuts of all the configurable actions from the defaults and the user's
    /// changes, as saved in their preferences.
    ///
    /// @param userBindings The user's shortcuts for the actions they have changed, by action
    ///                     name, in the text form of [KeyShortcut]. An empty list means the action
    ///                     has no shortcut. Unknown actions and invalid shortcuts are ignored. May
    ///                     be null.
    /// @return The shortcuts of every configurable action, including those with none.
    public static Map<Action, List<KeyShortcut>> fromUserBindings(final Map<String, List<String>> userBindings) {
        final Map<Action, List<KeyShortcut>> bindings = getDefaultBindings();
        NullSafe.map(userBindings).forEach((actionName, shortcutTexts) -> {
            final Action action = parseAction(actionName);
            if (action != null && action.isConfigurable()) {
                final List<KeyShortcut> shortcuts = new ArrayList<>();
                for (final String shortcutText : NullSafe.list(shortcutTexts)) {
                    final KeyShortcut shortcut = KeyShortcut.parse(shortcutText);
                    if (shortcut != null && !shortcuts.contains(shortcut)) {
                        shortcuts.add(shortcut);
                    }
                }
                bindings.put(action, shortcuts);
            }
        });
        return bindings;
    }

    /// The reverse of [#fromUserBindings(Map)], for saving in the user's preferences: only the
    /// actions whose shortcuts differ from the defaults are included, so that the user gets any
    /// new default shortcuts in later versions of Stroom.
    ///
    /// @param bindings The shortcuts of the configurable actions. Actions that are missing keep
    ///                 their defaults.
    /// @return The shortcuts of the actions that differ from the defaults, by action name, in the
    /// text form of [KeyShortcut], or null if none differ.
    public static Map<String, List<String>> toUserBindings(final Map<Action, List<KeyShortcut>> bindings) {
        final Map<String, List<String>> userBindings = new LinkedHashMap<>();
        for (final Action action : getConfigurableActions()) {
            final List<KeyShortcut> shortcuts = NullSafe.map(bindings).get(action);
            if (shortcuts != null && !shortcuts.equals(getDefaultShortcuts(action))) {
                userBindings.put(action.name(), shortcuts.stream()
                        .map(KeyShortcut::toString)
                        .collect(Collectors.toList()));
            }
        }
        return userBindings.isEmpty()
                ? null
                : userBindings;
    }

    /// Makes the shortcuts that will be in use: the user's shortcuts (see
    /// [#fromUserBindings(Map)]) less any that can't be used, i.e. those that clash with a fixed
    /// binding, those that clash with a shortcut of an action before them, and character key
    /// shortcuts if the user has turned them off. Actions the user has changed come before those
    /// they haven't, so a new default shortcut never takes the place of one the user chose.
    ///
    /// @param userBindings                 The user's changes, as for [#fromUserBindings(Map)].
    /// @param characterKeyShortcutsEnabled False if the user has turned off character key
    ///                                     shortcuts.
    /// @return The shortcuts in use for every configurable action, including those with none.
    public static Map<Action, List<KeyShortcut>> resolve(final Map<String, List<String>> userBindings,
                                                         final boolean characterKeyShortcutsEnabled) {
        final Map<Action, List<KeyShortcut>> bindings = fromUserBindings(userBindings);
        final List<Action> actions = new ArrayList<>();
        for (final Entry<Action, List<KeyShortcut>> entry : bindings.entrySet()) {
            if (!entry.getValue().equals(getDefaultShortcuts(entry.getKey()))) {
                actions.add(entry.getKey());
            }
        }
        for (final Action action : bindings.keySet()) {
            if (!actions.contains(action)) {
                actions.add(action);
            }
        }

        final List<KeyShortcut> used = new ArrayList<>();
        final Map<Action, List<KeyShortcut>> resolved = new LinkedHashMap<>();
        for (final Action action : actions) {
            final List<KeyShortcut> shortcuts = new ArrayList<>();
            for (final KeyShortcut shortcut : bindings.get(action)) {
                final boolean turnedOff = !characterKeyShortcutsEnabled && shortcut.isCharacterKeyShortcut();
                final boolean clashes = findFixedClash(shortcut) != null
                                        || used.stream().anyMatch(shortcut::clashesWith);
                if (!turnedOff && !clashes) {
                    shortcuts.add(shortcut);
                    used.add(shortcut);
                }
            }
            resolved.put(action, shortcuts);
        }

        // Back in the usual order
        final Map<Action, List<KeyShortcut>> ordered = new LinkedHashMap<>();
        for (final Action action : bindings.keySet()) {
            ordered.put(action, resolved.get(action));
        }
        return ordered;
    }

    /// Changes the shortcuts in use to the user's, e.g. once their preferences have been loaded
    /// or when they change them (see [#resolve(Map, boolean)]). Menu items show the new
    /// shortcuts the next time they are shown.
    ///
    /// @param userBindings                 The user's changes, as for [#fromUserBindings(Map)].
    /// @param characterKeyShortcutsEnabled False if the user has turned off character key
    ///                                     shortcuts.
    public static void applyUserBindings(final Map<String, List<String>> userBindings,
                                         final boolean characterKeyShortcutsEnabled) {
        KEY_STROKE_TO_ACTION_MAP.clear();
        ACTION_TO_SHORTCUTS_MAP.clear();
        keySequences = new KeySequences();

        resolve(userBindings, characterKeyShortcutsEnabled).forEach((action, shortcuts) ->
                shortcuts.forEach(shortcut -> bind(action, shortcut)));
        // Fixed bindings last, so a menu shows the user's shortcut rather than e.g. an arrow key
        FIXED_BINDINGS.forEach((action, keyStrokes) ->
                keyStrokes.forEach(keyStroke -> bind(action, KeyShortcut.of(keyStroke))));
    }

    /// Finds the shortcuts of other actions that clash with a shortcut (see
    /// [KeyShortcut#clashesWith(KeyShortcut)]), e.g. to warn the user before they bind it.
    ///
    /// @param bindings The shortcuts of the configurable actions.
    /// @param action   The action that the shortcut is for.
    /// @param shortcut The shortcut.
    /// @return The clashing shortcuts of the other actions, by action, in the order of
    /// `bindings`. Empty if there are none.
    public static Map<Action, List<KeyShortcut>> findClashes(final Map<Action, List<KeyShortcut>> bindings,
                                                             final Action action,
                                                             final KeyShortcut shortcut) {
        final Map<Action, List<KeyShortcut>> clashes = new LinkedHashMap<>();
        NullSafe.map(bindings).forEach((otherAction, shortcuts) -> {
            if (otherAction != action) {
                final List<KeyShortcut> clashing = NullSafe.list(shortcuts)
                        .stream()
                        .filter(shortcut::clashesWith)
                        .collect(Collectors.toList());
                if (!clashing.isEmpty()) {
                    clashes.put(otherAction, clashing);
                }
            }
        });
        return clashes;
    }

    /// @param shortcut A shortcut.
    /// @return The action whose fixed binding the shortcut clashes with, e.g. [Action#SELECT]
    /// for `Space`, or null if there is none. A shortcut like this can't be used.
    public static Action findFixedClash(final KeyShortcut shortcut) {
        for (final Entry<Action, List<KeyStroke>> entry : FIXED_BINDINGS.entrySet()) {
            for (final KeyStroke keyStroke : entry.getValue()) {
                if (shortcut.clashesWith(KeyShortcut.of(keyStroke))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /// Tells key handling that a key recorder, which lets the user press the keys of a new
    /// shortcut, has the focus (or has lost it). While it has the focus, key presses don't run
    /// commands or take part in key sequences, so that the user can press any keys. Only Escape
    /// and Enter still give an action ([Action#CLOSE] and [Action#OK]), to close the recorder's
    /// dialog.
    ///
    /// @param recording Whether a key recorder has the focus.
    public static void setRecording(final boolean recording) {
        KeyBinding.recording = recording;
        keySequences.clear();
    }

    /// Makes a key stroke from a key event.
    ///
    /// @param e A key event.
    /// @return The key, with the modifier keys held down.
    public static KeyStroke getKeyStroke(final NativeEvent e) {
        return KeyStroke.builder()
                .keyCode(e.getKeyCode())
                .shift(e.getShiftKey())
                .ctrl(e.getCtrlKey())
                .alt(e.getAltKey())
                .meta(e.getMetaKey())
                .build();
    }

    /// Whether a key press is a shortcut for an action, without the side effects of [#test] (it
    /// neither runs a command nor takes part in a key sequence), for a widget that handles an
    /// action itself before the global key handler sees the key. A key without Ctrl, Alt or Meta
    /// isn't a shortcut while the user is typing in a text field.
    ///
    /// @param e      A `keydown` event.
    /// @param action The action.
    /// @return Whether the key, with its modifiers, is bound to the action.
    public static boolean isShortcut(final NativeEvent e, final Action action) {
        return !recording
               && BrowserEvents.KEYDOWN.equals(e.getType())
               && (e.getAltKey() || e.getCtrlKey() || e.getMetaKey() || !isInput(e))
               && getKeyDownAction(e) == action;
    }

    public static Action test(final NativeEvent e) {
        if (recording) {
            return testWhileRecording(e);
        }

        Action action = null;

        // We don't want to test key binds in text input else we pick up things like the user typing '/'
        if (BrowserEvents.KEYDOWN.equals(e.getType())) {
            // Only test a basic action if we have a modifier.
            if (e.getAltKey() || e.getCtrlKey() || e.getMetaKey()) {
                // If there is a modifier then this isn't going to be a key sequence.
                keySequences.clear();
                action = getKeyDownAction(e);
                action = testAction(e, action);

            } else if (isInput(e)) {
                // We will support some single key bindings in text boxes.
                if (e.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                    keySequences.clear();
                    action = getKeyDownAction(e);
                    action = testAction(e, action);
                } else if (e.getKeyCode() == KeyCodes.KEY_SHIFT) {
                    // Allow double shift keysequence in text area.
                    keySequences.onKeyDown(e);
                } else {
                    keySequences.clear();
                }

            } else {
                // See if this is potentially part of a sequence.
                if (!keySequences.onKeyDown(e)) {
                    // It isn't part of a sequence so test if it is a simple binding.
                    action = getKeyDownAction(e);
                    if (action != null) {
                        action = testAction(e, action);
                    }
                }
            }

        } else if (BrowserEvents.KEYUP.equals(e.getType())) {
            action = keySequences.onKeyUp(e);
            action = testAction(e, action);
        }
        return action;
    }

    private static Action testWhileRecording(final NativeEvent e) {
        if (BrowserEvents.KEYDOWN.equals(e.getType())
            && !e.getAltKey()
            && !e.getCtrlKey()
            && !e.getMetaKey()
            && !e.getShiftKey()) {
            if (e.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                return Action.CLOSE;
            } else if (e.getKeyCode() == KeyCodes.KEY_ENTER || e.getKeyCode() == KeyCodes.KEY_MAC_ENTER) {
                return Action.OK;
            }
        }
        return null;
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

    private static Action getKeyDownAction(final NativeEvent e) {
        return KEY_STROKE_TO_ACTION_MAP.get(getKeyStroke(e));
    }

    private static Action parseAction(final String actionName) {
        for (final Action action : Action.values()) {
            if (action.name().equals(actionName)) {
                return action;
            }
        }
        return null;
    }

    private static void bind(final Action action, final KeyShortcut shortcut) {
        if (shortcut.isSequence()) {
            final List<KeyStroke> keyStrokes = shortcut.getKeyStrokes();
            keySequences.addKeySequence(action, keyStrokes.get(0).getKeyCode(), keyStrokes.get(1).getKeyCode());
        } else {
            // First binding of a key stroke wins
            if (!KEY_STROKE_TO_ACTION_MAP.containsKey(shortcut.getFirst())) {
                KEY_STROKE_TO_ACTION_MAP.put(shortcut.getFirst(), action);
            }
        }
        ACTION_TO_SHORTCUTS_MAP.computeIfAbsent(action, k -> new ArrayList<>()).add(shortcut);
    }

    private static void addFixed(final Action action, final int... keyCodes) {
        for (final int keyCode : keyCodes) {
            addFixed(action, KeyStroke.of(keyCode));
        }
    }

    private static void addFixed(final Action action, final KeyStroke... keyStrokes) {
        final List<KeyStroke> list = FIXED_BINDINGS.computeIfAbsent(action, k -> new ArrayList<>());
        for (final KeyStroke keyStroke : keyStrokes) {
            if (list.contains(keyStroke)) {
                throw new RuntimeException("Duplicate shortcut " + keyStroke + " for action " + action);
            }
            list.add(keyStroke);
        }
    }

    private static void addDefault(final Action action, final String... shortcutTexts) {
        if (!action.isConfigurable()) {
            throw new RuntimeException("Action " + action + " has no category so can't have a default binding");
        }
        final List<KeyShortcut> list = DEFAULT_BINDINGS.computeIfAbsent(action, k -> new ArrayList<>());
        for (final String shortcutText : shortcutTexts) {
            final KeyShortcut shortcut = KeyShortcut.parse(shortcutText);
            if (shortcut == null) {
                throw new RuntimeException("Invalid shortcut '" + shortcutText + "' for action " + action);
            } else if (list.contains(shortcut)) {
                throw new RuntimeException("Duplicate shortcut " + shortcut + " for action " + action
                                           + ", existing shortcuts: "
                                           + list.stream()
                                                   .map(Objects::toString)
                                                   .collect(Collectors.joining(", ")));
            }
            list.add(shortcut);
        }
    }


    // --------------------------------------------------------------------------------


    /// The groups that the configurable actions are shown in.
    public enum Category {
        GENERAL("General"),
        LISTS("Lists"),
        GO_TO("Go to"),
        CREATE("Create");

        private final String displayName;

        Category(final String displayName) {
            this.displayName = displayName;
        }

        /// @return The name of the category to show the user.
        public String getDisplayName() {
            return displayName;
        }
    }


    // --------------------------------------------------------------------------------


    public enum Action {
        MOVE_UP("Move up", Category.LISTS),
        MOVE_DOWN("Move down", Category.LISTS),
        MOVE_LEFT("Move left or collapse", Category.LISTS),
        MOVE_RIGHT("Move right or expand", Category.LISTS),
        MOVE_PAGE_DOWN("Move down a page", Category.LISTS),
        MOVE_PAGE_UP("Move up a page", Category.LISTS),
        MOVE_START("Move to the start", Category.LISTS),
        MOVE_END("Move to the end", Category.LISTS),
        /**
         * 'e' - E.g. to edit the current item
         */
        EDIT("Edit", null),
        CLOSE("Close", null),
        /**
         * enter
         */
        EXECUTE("Open", null),
        /**
         * space
         */
        SELECT("Select", null),
        MENU("Show menu", null),
        SELECT_ALL("Select all", null),
        /**
         * ctrl-enter
         */
        OK("OK", null),
        ITEM_SAVE("Save", Category.GENERAL),
        ITEM_SAVE_ALL("Save all", Category.GENERAL),
        ITEM_CLOSE("Close tab", Category.GENERAL),
        ITEM_CLOSE_ALL("Close all tabs", Category.GENERAL),
        FIND("Find", Category.GENERAL),
        FIND_IN_CONTENT("Find in content", Category.GENERAL),
        RECENT_ITEMS("Recent items", Category.GENERAL),
        LOCATE("Locate in explorer", Category.GENERAL),
        // Maximise the pane holding the focus within its split pane, or restore it
        MAXIMISE_PANE("Maximise or restore pane", Category.GENERAL),
        HELP("Show help", Category.GENERAL),
        DOCUMENTATION("Documentation", null),
        SHOW_KEY_BINDS("Show keyboard shortcuts", null),
        INFO("Info", null),
        FOCUS_FILTER("Go to quick filter", Category.GENERAL),
        FOCUS_EXPLORER_FILTER("Go to explorer filter", Category.GENERAL),
        SETTINGS("Settings", null),

        // GOTO key sequences
        GOTO_ANNOTATIONS("Go to Annotations", Category.GO_TO),
        GOTO_PROPERTIES("Go to Properties", Category.GO_TO),
        GOTO_API_KEYS("Go to API Keys", Category.GO_TO),
        GOTO_SIGNING_KEYS("Go to Signing Keys", Category.GO_TO),
        GOTO_CACHES("Go to Caches", Category.GO_TO),
        GOTO_DATA_RETENTION("Go to Data Retention", Category.GO_TO),
        GOTO_CONTENT_TEMPLATES("Go to Content Templates", Category.GO_TO),
        GOTO_DEPENDENCIES("Go to Dependencies", Category.GO_TO),
        GOTO_JOBS("Go to Jobs", Category.GO_TO),
        GOTO_NODES("Go to Nodes", Category.GO_TO),
        GOTO_TASKS("Go to Server Tasks", Category.GO_TO),
        GOTO_EXPLORER_TREE("Go to Explorer", Category.GO_TO),
        GOTO_SEARCH_RESULTS("Go to Search Results", Category.GO_TO),
        GOTO_FS_VOLUMES("Go to Data Volumes", Category.GO_TO),
        GOTO_APP_PERMS("Go to Application Permissions", Category.GO_TO),
        GOTO_DOC_PERMS("Go to Document Permissions", Category.GO_TO),
        GOTO_INDEX_VOLUMES("Go to Index Volumes", Category.GO_TO),
        GOTO_USER_ACCESS("Go to User Access", Category.GO_TO),
        GOTO_USER_ACCOUNTS("Go to User Accounts", Category.GO_TO),
        GOTO_USER_PROFILE("Go to User Profile", Category.GO_TO),
        GOTO_USERS("Go to Users", Category.GO_TO),
        GOTO_USER_GROUPS("Go to User Groups", Category.GO_TO),
        GOTO_USER_PREFERENCES("Go to User Preferences", Category.GO_TO),
        GOTO_USER_PERMISSION_REPORT("Go to User Permissions Report", Category.GO_TO),

        // Create Doc key sequences
        CREATE_ANNOTATION("Create Annotation", Category.CREATE), // A
        CREATE_ELASTIC_INDEX("Create Elastic Index", Category.CREATE), // C
        CREATE_DASHBOARD("Create Dashboard", Category.CREATE), // D
        CREATE_FEED("Create Feed", Category.CREATE), // E
        CREATE_FOLDER("Create Folder", Category.CREATE), // F
        CREATE_DICTIONARY("Create Dictionary", Category.CREATE), // I
        CREATE_LUCENE_INDEX("Create Lucene Index", Category.CREATE), // L
        CREATE_DOCUMENTATION("Create Documentation", Category.CREATE), // O
        CREATE_PIPELINE("Create Pipeline", Category.CREATE), // P
        CREATE_QUERY("Create Query", Category.CREATE), // Q
        CREATE_ANALYTIC_RULE("Create Analytic Rule", Category.CREATE), // R
        CREATE_TEXT_CONVERTER("Create Text Converter", Category.CREATE), // T
        CREATE_VIEW("Create View", Category.CREATE), // V
        CREATE_XSLT("Create XSLT", Category.CREATE); // X

        private final String displayName;
        private final Category category;

        Action(final String displayName, final Category category) {
            this.displayName = displayName;
            this.category = category;
        }

        /// @return The name of the action to show the user.
        public String getDisplayName() {
            return displayName;
        }

        /// @return The group the action is shown in, or null if the user can't change its
        /// bindings.
        public Category getCategory() {
            return category;
        }

        /// @return Whether the user can change the action's bindings.
        public boolean isConfigurable() {
            return category != null;
        }
    }
}
