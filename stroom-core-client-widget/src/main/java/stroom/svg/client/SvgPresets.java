/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.svg.client;

public final class SvgPresets {

    public static final Preset ABOUT = enabled("svgIcon-oo", "About");
    public static final Preset ADD = enabled("svgIcon-add", "Add");
    public static final Preset ADD_ABOVE = enabled("svgIcon-add-above", "Add above");
    public static final Preset ADD_BELOW = enabled("svgIcon-add-below", "Add below");
    public static final Preset ALERT = enabled("svgIcon-alert", "Alert");
    public static final Preset ANNOTATE = disabled("svgIcon-edit", "Annotate");
    public static final Preset CLEAR = disabled("svgIcon-clear", "Clear");
    public static final Preset CLOSE = disabled("svgIcon-close", "Close");
    public static final Preset COPY = disabled("svgIcon-copy", "Copy");
    public static final Preset DATABASE = enabled("svgIcon-database", "Database");
    public static final Preset DELETE = disabled("svgIcon-delete", "Delete");
    public static final Preset DELETED = disabled("svgIcon-deleted", "Deleted");
    public static final Preset DEPENDENCIES = enabled("svgIcon-dependencies", "Dependencies");
    public static final Preset DISABLE = disabled("svgIcon-disable", "Disable");
    public static final Preset DOWN = disabled("svgIcon-down", "Down");
    public static final Preset DOWNLOAD = disabled("svgIcon-download", "Download");
    public static final Preset EDIT = disabled("svgIcon-edit", "Edit");
    public static final Preset ELLIPSES_HORIZONTAL = enabled("svgIcon-ellipses-horizontal", "Actions...");
    public static final Preset ELLIPSES_VERTICAL = enabled("svgIcon-ellipses-vertical", "Actions...");
    public static final Preset ERROR = enabled("svgIcon-error", "Error");
    public static final Preset EXPLORER = enabled("svgIcon-explorer", "Explorer");
    public static final Preset FATAL = enabled("svgIcon-fatal", "Fatal");
    public static final Preset FAVOURITES = disabled("svgIcon-favourites", "Favourites");
    public static final Preset FEED = enabled("svgIcon-feed", "Feed");
    public static final Preset FIELD = enabled("svgIcon-field", "Field");
    public static final Preset FILE = enabled("svgIcon-file", "File");
    public static final Preset FILE_RAW = enabled("svgIcon-file-raw", "Raw File");
    public static final Preset FILE_FORMATTED = enabled("svgIcon-file-formatted", "Formatted File");
    public static final Preset FILTER = enabled("svgIcon-filter", "Filter");
    public static final Preset FOLDER = enabled("svgIcon-folder", "Folder");
    public static final Preset FOLDER_TREE = enabled("svgIcon-folder-tree", "Folder Tree");
    public static final Preset FORMAT = enabled("svgIcon-format", "Format & Indent");
    public static final Preset FUNCTION = enabled("svgIcon-function", "Function");
    public static final Preset GENERATE = enabled("svgIcon-generate", "Auto-generate roll-up permutations");
    public static final Preset HELP = enabled("svgIcon-help", "Help");
    public static final Preset HISTORY = disabled("svgIcon-history", "History");
    public static final Preset INFO = enabled("svgIcon-info", "Info");
    public static final Preset INFO_DELETED = enabled("svgIcon-info-deleted", "Info (Deleted)");
    public static final Preset INFO_WARNING = enabled("svgIcon-info-warning", "Info (Warning)");
    public static final Preset INSERT_ABOVE = enabled("svgIcon-insert-above", "Insert above");
    public static final Preset INSERT_BELOW = enabled("svgIcon-insert-below", "Insert below");
    public static final Preset JOBS = enabled("svgIcon-jobs", "Jobs");
    public static final Preset KEY = enabled("svgIcon-key", "API Keys");
    public static final Preset LINK = enabled("svgIcon-dependencies", "Dependencies");
    public static final Preset LOGOUT = enabled("svgIcon-logout", "Logout");
    public static final Preset MONITORING = enabled("svgIcon-monitoring", "Monitor");
    public static final Preset MOVE = disabled("svgIcon-move", "Move");
    public static final Preset NEW_ITEM = enabled("svgIcon-add", "New");
    public static final Preset NODES = enabled("svgIcon-nodes", "Nodes");
    public static final Preset OPEN = disabled("svgIcon-edit", "Open");
    public static final Preset OPERATOR = enabled("svgIcon-operator", "Add Operator");
    public static final Preset PASSWORD = enabled("svgIcon-password", "Change Password");
    public static final Preset PROCESS = disabled("svgIcon-process", "Process");
    public static final Preset PROPERTIES = enabled("svgIcon-properties", "Properties");
    public static final Preset RAW = disabled("svgIcon-raw", "Raw");
    public static final Preset REMOVE = disabled("svgIcon-remove", "Remove");
    public static final Preset RULESET = enabled("svgIcon-ruleset", "Rule Set");
    public static final Preset RUN = enabled("svgIcon-play-green", "Run");
    public static final Preset STOP = enabled("svgIcon-stop-red", "Stop");
    public static final Preset SAVE = disabled("svgIcon-save", "Save");
    public static final Preset SAVE_AS = disabled("svgIcon-saveas", "Save As");
    public static final Preset SETTINGS = enabled("svgIcon-settings", "Settings");
    public static final Preset SETTINGS_BLUE = enabled("svgIcon-settings-blue", "Settings");
    public static final Preset SHARD_CLOSE = disabled("svgIcon-shard-close", "Close Selected Shards");
    public static final Preset SHARD_FLUSH = disabled("svgIcon-shard-flush", "Flush Selected Shards");
    public static final Preset TABLE = enabled("svgIcon-table", "Table");
    public static final Preset TABLE_NESTED = enabled("svgIcon-table-nested", "Nested Table");
    public static final Preset UNDO = disabled("svgIcon-undo", "Undo");
    public static final Preset UP = disabled("svgIcon-up", "Up");
    public static final Preset UPLOAD = enabled("svgIcon-upload", "Upload");
    public static final Preset USER = enabled("svgIcon-user", "User");
    public static final Preset USER_DISABLED = enabled("svgIcon-user-disabled", "User");
    public static final Preset USER_GROUP = enabled("svgIcon-users", "User Group");
    public static final Preset USER_GROUP_DISABLED = enabled("svgIcon-users-disabled", "User Group");
    public static final Preset VOLUMES = enabled("svgIcon-volumes", "Volumes");

    public static final Preset COLLAPSE_UP = enabled("svgIcon-collapse-up", "Collapse");
    public static final Preset EXPAND_DOWN = enabled("svgIcon-expand-down", "Expand");

    public static final Preset UNLOCKED_GREEN = enabled("svgIcon-unlocked-green", "Unlocked");
    public static final Preset LOCKED_AMBER = enabled("svgIcon-locked-amber", "Locked");

    public static final Preset UNLOCK_AMBER = enabled("svgIcon-unlock-amber", "Unlock");
    public static final Preset LOCK_GREEN = enabled("svgIcon-lock-green", "Lock");

    public static final Preset FAST_BACKWARD_BLUE = disabled("svgIcon-fast-backward", "First");
    public static final Preset STEP_BACKWARD_BLUE = disabled("svgIcon-step-backward", "Backward");
    public static final Preset STEP_FORWARD_BLUE = disabled("svgIcon-step-forward", "Forward");
    public static final Preset FAST_FORWARD_BLUE = disabled("svgIcon-fast-forward", "Last");
    public static final Preset REFRESH_BLUE = enabled("svgIcon-refresh", "Refresh");

    public static final Preset FILTER_GREEN = enabled("svgIcon-filter-green", "Filter");
    public static final Preset FAST_BACKWARD_GREEN = disabled("svgIcon-fast-backward-green", "First");
    public static final Preset STEP_BACKWARD_GREEN = disabled("svgIcon-step-backward-green", "Backward");
    public static final Preset STEP_FORWARD_GREEN = disabled("svgIcon-step-forward-green", "Forward");
    public static final Preset FAST_FORWARD_GREEN = disabled("svgIcon-fast-forward-green", "Last");
    public static final Preset REFRESH_GREEN = enabled("svgIcon-refresh-green", "Refresh");

    public static final Preset SHOW = enabled("svgIcon-show", "Show");
    public static final Preset HIDE = enabled("svgIcon-hide", "Hide");

    private SvgPresets() {
        // Utility class.
    }

    public static Preset of(final Preset svgPreset, final String title, final boolean enabled) {
        return svgPreset.with(title, enabled);
    }

    private static Preset enabled(final String className, final String title) {
        return new Preset(className, title, true);
    }

    private static Preset disabled(final String className, final String title) {
        return new Preset(className, title, false);
    }
}
