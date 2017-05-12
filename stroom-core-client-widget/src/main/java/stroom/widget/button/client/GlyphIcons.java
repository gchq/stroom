/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.button.client;

public final class GlyphIcons {
    public static final GlyphIcon.ColourSet RED = GlyphIcon.ColourSet.create("#D32F2F", "#E53935");
    public static final GlyphIcon.ColourSet GREEN = GlyphIcon.ColourSet.create("#4CAF50", "#66BB6A");
    public static final GlyphIcon.ColourSet BLUE = GlyphIcon.ColourSet.create("#1E88E5", "#2196F3");
    public static final GlyphIcon.ColourSet YELLOW = GlyphIcon.ColourSet.create("#FF8F00", "#FFA000");
    public static final GlyphIcon.ColourSet GREY = GlyphIcon.ColourSet.create("#757575", "#9E9E9E");

    public static final GlyphIcon ADD = new GlyphIcon("fa fa-plus", BLUE, "Add", true);
    public static final GlyphIcon ALERT = new GlyphIcon("fa fa-exclamation-triangle", YELLOW, "Alert", true);
    public static final GlyphIcon CLEAR = new GlyphIcon("fa fa-times-circle", BLUE, "Clear", false);
    public static final GlyphIcon CLOSE = new GlyphIcon("fa fa-times", RED, "Close", false);
    public static final GlyphIcon COPY = new GlyphIcon("fa fa-clone", BLUE, "Copy", false);
    public static final GlyphIcon DATABASE = new GlyphIcon("fa fa-database", GREY, "Database", true);
    public static final GlyphIcon DELETE = new GlyphIcon("fa fa-trash", BLUE, "Delete", false);
    public static final GlyphIcon DISABLE = new GlyphIcon("fa fa-ban", RED, "Disable", false);
    public static final GlyphIcon DOWN = new GlyphIcon("fa fa-arrow-down", BLUE, "Down", false);
    public static final GlyphIcon DOWNLOAD = new GlyphIcon("fa fa-arrow-down", GREEN, "Download", false);
    public static final GlyphIcon EDIT = new GlyphIcon("fa fa-pencil", BLUE, "Edit", false);
    public static final GlyphIcon EXPLORER = new GlyphIcon("fa fa-sitemap", BLUE, "Explorer", true);
    public static final GlyphIcon ERROR = new GlyphIcon("fa fa-times-circle", RED, "Error", true);
    public static final GlyphIcon FILTER = new GlyphIcon("fa fa-filter", BLUE, "Filter", true);
    public static final GlyphIcon HELP = new GlyphIcon("fa fa-question-circle", BLUE, "Help", true);
    public static final GlyphIcon HISTORY = new GlyphIcon("fa fa-clock-o", BLUE, "History", false);
    public static final GlyphIcon INFO = new GlyphIcon("fa fa-info-circle", BLUE, "Info", true);
    public static final GlyphIcon JOBS = new GlyphIcon("fa fa-tasks", GREY, "Jobs", true);
    public static final GlyphIcon LOGOUT = new GlyphIcon("fa fa-sign-out", BLUE, "Logout", true);
    public static final GlyphIcon MONITORING = new GlyphIcon("fa fa-line-chart", BLUE, "Monitor", true);
    public static final GlyphIcon MOVE = new GlyphIcon("fa fa-arrow-circle-o-right", BLUE, "Move", false);
    public static final GlyphIcon NEW_ITEM = new GlyphIcon("fa fa-plus", BLUE, "New", true);
    public static final GlyphIcon NODES = new GlyphIcon("fa fa-share-alt", GREY, "Nodes", true);
    public static final GlyphIcon OPEN = new GlyphIcon("fa fa-pencil", BLUE, "Open", false);
    public static final GlyphIcon PASSWORD = new GlyphIcon("fa fa-asterisk", GREY, "Change Password", true);
    public static final GlyphIcon PERMISSIONS = new GlyphIcon("fa fa-lock", YELLOW, "Permissions", true);
    public static final GlyphIcon PROPERTIES = new GlyphIcon("fa fa-cog", YELLOW, "Properties", true);
    public static final GlyphIcon REFRESH = new GlyphIcon("fa fa-refresh", GREEN, "Refresh", true);
    public static final GlyphIcon REMOVE = new GlyphIcon("fa fa-minus", BLUE, "Remove", false);
    public static final GlyphIcon SAVE = new GlyphIcon("fa fa-floppy-o", BLUE, "Save", false);
    public static final GlyphIcon SAVE_AS = new GlyphIcon("fa fa-code-fork", BLUE, "Save As", true);
    public static final GlyphIcon UNDO = new GlyphIcon("fa fa-undo", BLUE, "Undo", false);
    public static final GlyphIcon UP = new GlyphIcon("fa fa-arrow-up", BLUE, "Up", false);
    public static final GlyphIcon UPLOAD = new GlyphIcon("fa fa-arrow-up", GREEN, "Upload", true);
    public static final GlyphIcon USER = new GlyphIcon("fa fa-user", BLUE, "User", true);
    public static final GlyphIcon USER_GROUP = new GlyphIcon("fa fa-users", BLUE, "User Group", true);
    public static final GlyphIcon USER_DISABLED = new GlyphIcon("fa fa-user", GREY, "User", true);
    public static final GlyphIcon USER_GROUP_DISABLED = new GlyphIcon("fa fa-users", GREY, "User Group", true);
    public static final GlyphIcon VOLUMES = new GlyphIcon("fa fa-hdd-o", GREY, "Volumes", true);

    private GlyphIcons() {
        // Utility class.
    }
}
