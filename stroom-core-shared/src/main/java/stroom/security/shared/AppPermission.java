package stroom.security.shared;

import stroom.docref.HasDisplayValue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum AppPermission implements HasDisplayValue {
    ADMINISTRATOR(
            "Administrator",
            "Full administrator rights to access and manage everything."),
    ANNOTATIONS(
            "Annotations",
            "Create and view annotations in query results."),
    MANAGE_CACHE_PERMISSION(
            "Manage Cache",
            "Access the Caches screen to view and clear system caches."),
    VIEW_DATA_PERMISSION(
            "Data - View",
            "View stream data (e.g. in the Data Viewer or a Dashboard text pane)."),
    VIEW_DATA_WITH_PIPELINE_PERMISSION(
            "Data - View With Pipeline",
            "View data in a Dashboard text pane that uses a pipeline."),
    DELETE_DATA_PERMISSION(
            "Data - Delete",
            "Delete streams."),
    IMPORT_DATA_PERMISSION(
            "Data - Import",
            "Upload stream data into a feed."),
    EXPORT_DATA_PERMISSION(
            "Data - Export",
            "Download/export streams from a feed."),
    MANAGE_USERS_PERMISSION(
            "Manage Users",
            "Access the screens to manage users, groups, permissions and API keys."),
    CHANGE_OWNER_PERMISSION(
            "Change Owner",
            "Change the ownership of a document or folder to another user."),
    STEPPING_PERMISSION(
            "Pipeline Stepping",
            "Step data through a pipeline using the Stepper."),
    MANAGE_PROCESSORS_PERMISSION(
            "Manage Processors",
            "Access the Processors tab and manage the processors/filters used to process stream data " +
                    "through pipelines."),
    MANAGE_TASKS_PERMISSION(
            "Manage Tasks",
            "Access the Server Tasks screen to view/stop tasks running on the nodes."),
    DOWNLOAD_SEARCH_RESULTS_PERMISSION(
            "Download Search Results",
            "Download search result data on a Dashboard."),
    MANAGE_API_KEYS(
            "Manage API Keys",
            "Access the API Keys screen to view, create, edit, delete the user's own API keys. " +
                    "'" + MANAGE_USERS_PERMISSION.displayValue +
                    "' permission is also required to managed other users' " +
                    "API keys"),
    MANAGE_JOBS_PERMISSION(
            "Manage Jobs",
            "Access the Jobs screen to manage Stroom's background jobs."),
    MANAGE_PROPERTIES_PERMISSION(
            "Manage Properties",
            "Access to the Properties to manage the system configuration."),
    MANAGE_POLICIES_PERMISSION(
            "Manage Policies",
            "Access the Data Retention screen to manage data retention rules."),
    MANAGE_NODES_PERMISSION(
            "Manage Nodes",
            "Access the Nodes screen to view the nodes the cluster and manage their priority and enabled " +
                    "states."),
    MANAGE_INDEX_SHARDS_PERMISSION(
            "Manage Index Shards",
            ""),
    MANAGE_VOLUMES_PERMISSION(
            "Manage Volumes",
            ""),
    MANAGE_DB_PERMISSION(
            "Manage DB",
            ""),
    IMPORT_CONFIGURATION(
            "Import Configuration",
            ""),
    EXPORT_CONFIGURATION(
            "Export Configuration",
            ""),
    VIEW_SYSTEM_INFO_PERMISSION(
            "View System Information",
            "");

    private static final Map<String, AppPermission> APP_PERMISSION_ENUM_MAP;
    public static final List<AppPermission> LIST;

    static {
        APP_PERMISSION_ENUM_MAP = Arrays
                .stream(AppPermission.values())
                .collect(Collectors.toMap(AppPermission::getDisplayValue, Function.identity()));
        LIST = Arrays
                .stream(AppPermission.values())
                .sorted(Comparator.comparing(AppPermission::getDisplayValue))
                .collect(Collectors.toList());
    }

    public static AppPermission getPermissionForName(final String name) {
        return APP_PERMISSION_ENUM_MAP.get(name);
    }

    private final String displayValue;
    private final String description;

    AppPermission(final String displayValue,
                  final String description) {
        this.displayValue = displayValue;
        this.description = description;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getDescription() {
        return description;
    }
}
