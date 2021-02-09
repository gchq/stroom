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
    private static final String IMAGES_PATH = "images/";

    public static final SvgPreset ABOUT = enabled("oo.svg", "About");
    public static final SvgPreset ADD = enabled("add.svg", "Add");
    public static final SvgPreset ALERT = enabled("alert.svg", "Alert");
    public static final SvgPreset ANNOTATE = disabled("edit.svg", "Annotate");
    public static final SvgPreset ANNOTATIONS = enabled("document/AnnotationsIndex.svg", "Annotations");
    public static final SvgPreset CLEAR = disabled("clear.svg", "Clear");
    public static final SvgPreset CLOSE = disabled("close.svg", "Close");
    public static final SvgPreset COPY = disabled("copy.svg", "Copy");
    public static final SvgPreset DATABASE = enabled("database.svg", "Database");
    public static final SvgPreset DELETE = disabled("delete.svg", "Delete");
    public static final SvgPreset DELETED = disabled("deleted.svg", "Deleted");
    public static final SvgPreset DEPENDENCIES = enabled("dependencies.svg", "Dependencies");
    public static final SvgPreset DISABLE = disabled("disable.svg", "Disable");
    public static final SvgPreset DOWN = disabled("down.svg", "Down");
    public static final SvgPreset DOWNLOAD = disabled("download.svg", "Download");
    public static final SvgPreset EDIT = disabled("edit.svg", "Edit");
    public static final SvgPreset ELASTIC_SEARCH = enabled("document/ElasticIndex.svg", "Elastic Search");
    public static final SvgPreset ERROR = enabled("error.svg", "Error");
    public static final SvgPreset EXPLORER = enabled("explorer.svg", "Explorer");
    public static final SvgPreset FATAL = enabled("fatal.svg", "Fatal");
    public static final SvgPreset FAVOURITES = disabled("favourites.svg", "Favourites");
    public static final SvgPreset FEED = enabled("feed.svg", "Feed");
    public static final SvgPreset FIELD = enabled("field.svg", "Field");
    public static final SvgPreset FILE = enabled("file.svg", "File");
    public static final SvgPreset FILE_RAW = enabled("file-raw.svg", "Raw File");
    public static final SvgPreset FILE_FORMATTED = enabled("file-formatted.svg", "Formatted File");
    public static final SvgPreset FILTER = enabled("filter.svg", "Filter");
    public static final SvgPreset FOLDER = enabled("folder.svg", "Folder");
    public static final SvgPreset FOLDER_TREE = enabled("folder-tree.svg", "Folder Tree");
    public static final SvgPreset FORMAT = enabled("format.svg", "Format & Indent");
    public static final SvgPreset FUNCTION = enabled("function.svg", "Function");
    public static final SvgPreset GENERATE = enabled("generate.svg", "Auto-generate roll-up permutations");
    public static final SvgPreset HELP = enabled("help.svg", "Help");
    public static final SvgPreset HISTORY = disabled("history.svg", "History");
    public static final SvgPreset INFO = enabled("info.svg", "Info");
    public static final SvgPreset INFO_DELETED = enabled("info-deleted.svg", "Info (Deleted)");
    public static final SvgPreset INFO_WARNING = enabled("info-warning.svg", "Info (Warning)");
    public static final SvgPreset INSERT_ABOVE = enabled("insert-above.svg", "Insert above");
    public static final SvgPreset INSERT_BELOW = enabled("insert-below.svg", "Insert below");
    public static final SvgPreset JOBS = enabled("jobs.svg", "Jobs");
    public static final SvgPreset KEY = enabled("key.svg", "API Keys");
    public static final SvgPreset LINK = enabled("dependencies.svg", "Dependencies");
    public static final SvgPreset LOGOUT = enabled("logout.svg", "Logout");
    public static final SvgPreset MONITORING = enabled("monitoring.svg", "Monitor");
    public static final SvgPreset MOVE = disabled("move.svg", "Move");
    public static final SvgPreset NEW_ITEM = enabled("add.svg", "New");
    public static final SvgPreset NODES = enabled("nodes.svg", "Nodes");
    public static final SvgPreset OPEN = disabled("edit.svg", "Open");
    public static final SvgPreset OPERATOR = enabled("operator.svg", "Add Operator");
    public static final SvgPreset PASSWORD = enabled("password.svg", "Change Password");
    public static final SvgPreset PROCESS = disabled("process.svg", "Process");
    public static final SvgPreset PROPERTIES = enabled("properties.svg", "Properties");
    public static final SvgPreset RAW = disabled("raw.svg", "Raw");
    public static final SvgPreset REMOVE = disabled("remove.svg", "Remove");
    public static final SvgPreset RULESET = enabled("ruleset.svg", "Rule Set");
    public static final SvgPreset RUN = enabled("play-green.svg", "Run");
    public static final SvgPreset STOP = enabled("stop-red.svg", "Stop");
    public static final SvgPreset SAVE = disabled("save.svg", "Save");
    public static final SvgPreset SAVE_AS = disabled("saveas.svg", "Save As");
    public static final SvgPreset SETTINGS = enabled("settings.svg", "Settings");
    public static final SvgPreset SETTINGS_BLUE = enabled("settings-blue.svg", "Settings");
    public static final SvgPreset SHARD_CLOSE = disabled("shard-close.svg", "Close Selected Shards");
    public static final SvgPreset SHARD_FLUSH = disabled("shard-flush.svg", "Flush Selected Shards");
    public static final SvgPreset SPINNER = enabled("spinner.svg", null);
    public static final SvgPreset TABLE = enabled("table.svg", "Table");
    public static final SvgPreset TABLE_NESTED = enabled("table-nested.svg", "Nested Table");
    public static final SvgPreset UNDO = disabled("undo.svg", "Undo");
    public static final SvgPreset UP = disabled("up.svg", "Up");
    public static final SvgPreset UPLOAD = enabled("upload.svg", "Upload");
    public static final SvgPreset USER = enabled("user.svg", "User");
    public static final SvgPreset USER_DISABLED = enabled("user-disabled.svg", "User");
    public static final SvgPreset USER_GROUP = enabled("users.svg", "User Group");
    public static final SvgPreset USER_GROUP_DISABLED = enabled("users-disabled.svg", "User Group");
    public static final SvgPreset VOLUMES = enabled("volumes.svg", "Volumes");

    public static final SvgPreset COLLAPSE_UP = enabled("collapse-up.svg", "Collapse");
    public static final SvgPreset EXPAND_DOWN = enabled("expand-down.svg", "Expand");

    public static final SvgPreset UNLOCKED_GREEN = enabled("unlocked-green.svg", "Unlocked");
    public static final SvgPreset LOCKED_AMBER = enabled("locked-amber.svg", "Locked");

    public static final SvgPreset UNLOCK_AMBER = enabled("unlock-amber.svg", "Unlock");
    public static final SvgPreset LOCK_GREEN = enabled("lock-green.svg", "Lock");

    public static final SvgPreset FAST_BACKWARD_BLUE = disabled("fast-backward.svg", "First");
    public static final SvgPreset STEP_BACKWARD_BLUE = disabled("step-backward.svg", "Backward");
    public static final SvgPreset STEP_FORWARD_BLUE = disabled("step-forward.svg", "Forward");
    public static final SvgPreset FAST_FORWARD_BLUE = disabled("fast-forward.svg", "Last");
    public static final SvgPreset REFRESH_BLUE = enabled("refresh.svg", "Refresh");

    public static final SvgPreset FAST_BACKWARD_GREEN = disabled("fast-backward-green.svg", "First");
    public static final SvgPreset STEP_BACKWARD_GREEN = disabled("step-backward-green.svg", "Backward");
    public static final SvgPreset STEP_FORWARD_GREEN = disabled("step-forward-green.svg", "Forward");
    public static final SvgPreset FAST_FORWARD_GREEN = disabled("fast-forward-green.svg", "Last");
    public static final SvgPreset REFRESH_GREEN = enabled("refresh-green.svg", "Refresh");

    public static final SvgPreset SHOW = enabled("show.svg", "Show");
    public static final SvgPreset HIDE = enabled("hide.svg", "Hide");

    private SvgPresets() {
        // Utility class.
    }

    public static SvgPreset of(final SvgPreset svgPreset, final String title, final boolean enabled) {
        return svgPreset.with(title, enabled);
    }

    private static SvgPreset enabled(final String url, final String title) {
        return new SvgPreset(IMAGES_PATH + url, title, true);
    }

    private static SvgPreset disabled(final String url, final String title) {
        return new SvgPreset(IMAGES_PATH + url, title, false);
    }
}
