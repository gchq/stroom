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

package stroom.security.shared;

import stroom.docref.HasDisplayValue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * !!!!!!!! WARNING !!!!!!!!!
 * </p>
 * DO NOT change the displayValue of these items unless you also write a migration
 * script to migrate the value in the permission_app_id table, else it will break
 * users' permissions.
 */
public enum AppPermission implements HasDisplayValue {

    ADMINISTRATOR(
            "Administrator",
            "Full administrator rights to access and manage everything."),
    ANNOTATIONS(
            "Annotations",
            "Create and view annotations in query results."),
    CREDENTIALS(
            "Credentials",
            "Create and view credentials"),
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
    MANAGE_CONTENT_TEMPLATES_PERMISSION(
            "Manage Content Templates",
            "Access the Content Templates screen to view and manage the templates for auto-creating " +
            "content."),
    MANAGE_DATA_RECEIPT_RULES_PERMISSION(
            "Manage Data Receipt Rules",
            "Access the Data Receipt Rules screen to view and manage the rules for accepting, " +
            "rejecting or dropping received data."),
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
            ""),
    CHECK_RECEIPT_STATUS(
            "Check Receipt Status",
            "Ability to check the receipt status of a feed."),
    FETCH_HASHED_RECEIPT_POLICY_RULES(
            "Fetch hashed receipt policy rules",
            "Ability to fetch the obfuscated receipt policy rules. This permission is required for " +
            "any Stroom-Proxy that needs to fetch receipt policy rules."),
    VERIFY_API_KEY(
            "Check the validity of an API Key.",
            "Ability to call the API endpoint to verify an API Key. This is required for any " +
            "Stroom-Proxy that needs to verify API keys sent to it from upstream Stroom-Proxies."),
    STROOM_PROXY(
            "Stroom Proxy",
            "Intended to be granted to a non-human user account that is used by a Stroom-Proxy instance " +
            "to communicate with Stroom or another Stroom-Proxy. It provides the permission to check feed status, " +
            "fetch receipt policy rules and verify API keys.");

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
    private final AppPermissionSet appPermissionSet;

    AppPermission(final String displayValue,
                  final String description) {
        this.displayValue = displayValue;
        this.description = description;
        // Saves us having to wrap the perm every time we use SecurityContext
        this.appPermissionSet = AppPermissionSet.of(this);
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getDescription() {
        return description;
    }

    public AppPermissionSet asAppPermissionSet() {
        return appPermissionSet;
    }
}
