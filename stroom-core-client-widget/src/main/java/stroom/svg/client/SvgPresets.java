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

import stroom.svg.shared.SvgImage;

public final class SvgPresets {

    public static final Preset ADD = enabled(SvgImage.ADD, "Add");
    public static final Preset ADD_ABOVE = enabled(SvgImage.ADD_ABOVE, "Add above");
    public static final Preset ADD_BELOW = enabled(SvgImage.ADD_BELOW, "Add below");
    public static final Preset ADD_MULTIPLE = enabled(SvgImage.ADD_MULTIPLE, "Add Multiple");
    public static final Preset AI = enabled(SvgImage.AI, "AI");
    public static final Preset ALERT = enabled(SvgImage.ALERT, "Alert");
    public static final Preset ANNOTATE = enabled(SvgImage.EDIT, "Annotate");
    public static final Preset ARROW_RIGHT = enabled(SvgImage.ARROW_RIGHT, "Move Right");
    public static final Preset ARROW_LEFT = enabled(SvgImage.ARROW_LEFT, "Move Left");
    public static final Preset ARROW_UP = enabled(SvgImage.ARROW_UP, "Move Up");
    public static final Preset ARROW_DOWN = enabled(SvgImage.ARROW_DOWN, "Move Down");
    public static final Preset CLEAR = disabled(SvgImage.CLEAR, "Clear");
    public static final Preset CLIPBOARD = disabled(SvgImage.CLIPBOARD, "Clipboard");
    public static final Preset CLOSE = disabled(SvgImage.CLOSE, "Close");
    public static final Preset CANCEL = enabled(SvgImage.CANCEL, "Cancel");
    public static final Preset COPY = disabled(SvgImage.COPY, "Copy");
    public static final Preset DELETE = disabled(SvgImage.DELETE, "Delete");
    public static final Preset DELETED = enabled(SvgImage.DELETE, "Deleted");
    public static final Preset DISABLE = disabled(SvgImage.DISABLE, "Disable");
    public static final Preset DOWN = disabled(SvgImage.DOWN, "Down");
    public static final Preset DOWNLOAD = disabled(SvgImage.DOWNLOAD, "Download");
    public static final Preset EDIT = disabled(SvgImage.EDIT, "Edit");
    public static final Preset ELLIPSES_HORIZONTAL = enabled(SvgImage.ELLIPSES_HORIZONTAL, "Actions...");
    public static final Preset ERROR = enabled(SvgImage.ERROR, "Error");
    public static final Preset FATAL = enabled(SvgImage.FATAL, "Fatal");
    public static final Preset FAVOURITES = disabled(SvgImage.FAVOURITES, "Favourites");
    public static final Preset FIELD = enabled(SvgImage.FIELD, "Field");
    public static final Preset FILE = enabled(SvgImage.FILE, "File");
    public static final Preset FILTER = enabled(SvgImage.FILTER, "Filter");
    public static final Preset FUNCTION = enabled(SvgImage.FUNCTION, "Function");
    public static final Preset GENERATE = enabled(SvgImage.GENERATE, "Auto-generate roll-up permutations");
    public static final Preset HELP = enabled(SvgImage.HELP, "Help");
    public static final Preset HISTORY = disabled(SvgImage.HISTORY, "History");
    public static final Preset INFO = enabled(SvgImage.INFO, "Info");
    public static final Preset KEY = enabled(SvgImage.KEY, "Key");
    public static final Preset MOVE = disabled(SvgImage.MOVE, "Move");
    public static final Preset NEW_ITEM = enabled(SvgImage.ADD, "New");
    public static final Preset OPEN = disabled(SvgImage.OPEN, "Open");
    public static final Preset OPERATOR = enabled(SvgImage.OPERATOR, "Add Operator");
    public static final Preset PROCESS = disabled(SvgImage.PROCESS, "Process");
    public static final Preset PROPERTIES = enabled(SvgImage.PROPERTIES, "Properties");
    public static final Preset REMOVE = disabled(SvgImage.REMOVE, "Remove");
    public static final Preset RERUN = enabled((SvgImage.RERUN), "Rerun");
    public static final Preset RUN = enabled(SvgImage.PLAY, "Run");
    public static final Preset SAVE = disabled(SvgImage.SAVE, "Save");
    public static final Preset SAVE_AS = disabled(SvgImage.SAVEAS, "Save As");
    public static final Preset SETTINGS = enabled(SvgImage.SETTINGS, "Settings");
    public static final Preset SETTINGS_BLUE = enabled(SvgImage.SETTINGS, "Settings");
    public static final Preset SHIELD = enabled(SvgImage.SHIELD, "Shield");
    public static final Preset SHARD_FLUSH = disabled(SvgImage.SHARD_FLUSH, "Flush Selected Shards");
    public static final Preset STAMP = enabled(SvgImage.STAMP, "Template");
    public static final Preset STOP = enabled(SvgImage.STOP, "Stop");
    public static final Preset TABLE = enabled(SvgImage.TABLE, "Table");
    public static final Preset TABLE_NESTED = enabled(SvgImage.TABLE_NESTED, "Nested Table");
    public static final Preset TICK = enabled(SvgImage.TICK, "Tick");
    public static final Preset UNDO = disabled(SvgImage.UNDO, "Undo");
    public static final Preset UP = disabled(SvgImage.UP, "Up");
    public static final Preset UPLOAD = enabled(SvgImage.UPLOAD, "Upload");
    public static final Preset USER = enabled(SvgImage.USER, "User");
    public static final Preset USER_GROUP = enabled(SvgImage.USERS, "User Group");
    public static final Preset FIND = enabled(SvgImage.FIND, "Find");

    public static final Preset COLLAPSE_UP = enabled(SvgImage.COLLAPSE_UP, "Collapse");
    public static final Preset EXPAND_DOWN = enabled(SvgImage.EXPAND_DOWN, "Expand");
    public static final Preset COLLAPSE_ALL = enabled(SvgImage.COLLAPSE_ALL, "Collapse All");
    public static final Preset EXPAND_ALL = enabled(SvgImage.EXPAND_ALL, "Expand All");

    public static final Preset LOCKED_AMBER = enabled(SvgImage.LOCKED, "Locked");


    public static final Preset FAST_BACKWARD_BLUE = disabled(SvgImage.FAST_BACKWARD, "First");
    public static final Preset STEP_BACKWARD_BLUE = disabled(SvgImage.STEP_BACKWARD, "Backward");
    public static final Preset STEP_FORWARD_BLUE = disabled(SvgImage.STEP_FORWARD, "Forward");
    public static final Preset FAST_FORWARD_BLUE = disabled(SvgImage.FAST_FORWARD, "Last");
    public static final Preset REFRESH_BLUE = enabled(SvgImage.REFRESH, "Refresh");
    public static final Preset REFRESH_GREEN = enabled(SvgImage.REFRESH, "Refresh");

    private SvgPresets() {
        // Utility class.
    }

    public static Preset of(final Preset svgPreset, final String title, final boolean enabled) {
        return svgPreset.with(title, enabled);
    }

    public static Preset enabled(final SvgImage svgImage, final String title) {
        return new Preset(svgImage, title, true);
    }

    private static Preset disabled(final SvgImage svgImage, final String title) {
        return new Preset(svgImage, title, false);
    }
}
