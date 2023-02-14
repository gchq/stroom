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

package stroom.security.shared;

import java.util.HashMap;
import java.util.Map;

/**
 * Provide string constants for the permissions.
 */
public final class PermissionNames {

    /**
     * Administrators have UNRESTRICTED permission on SYSTEM
     */
    public static final String ADMINISTRATOR = "Administrator";
    public static final String ANNOTATIONS = "Annotations";
    public static final String MANAGE_CACHE_PERMISSION = "Manage Cache";
    public static final String VIEW_DATA_PERMISSION = "Data - View";
    public static final String VIEW_DATA_WITH_PIPELINE_PERMISSION = "Data - View With Pipeline";
    public static final String DELETE_DATA_PERMISSION = "Data - Delete";
    public static final String IMPORT_DATA_PERMISSION = "Data - Import";
    public static final String EXPORT_DATA_PERMISSION = "Data - Export";
    public static final String MANAGE_USERS_PERMISSION = "Manage Users";
    public static final String STEPPING_PERMISSION = "Pipeline Stepping";
    public static final String MANAGE_PROCESSORS_PERMISSION = "Manage Processors";
    public static final String MANAGE_TASKS_PERMISSION = "Manage Tasks";
    public static final String DOWNLOAD_SEARCH_RESULTS_PERMISSION = "Download Search Results";
    public static final String MANAGE_JOBS_PERMISSION = "Manage Jobs";
    public static final String MANAGE_PROPERTIES_PERMISSION = "Manage Properties";
    public static final String MANAGE_POLICIES_PERMISSION = "Manage Policies";
    public static final String MANAGE_NODES_PERMISSION = "Manage Nodes";
    public static final String MANAGE_INDEX_SHARDS_PERMISSION = "Manage Index Shards";
    public static final String MANAGE_VOLUMES_PERMISSION = "Manage Volumes";
    public static final String MANAGE_DB_PERMISSION = "Manage DB";
    public static final String IMPORT_CONFIGURATION = "Import Configuration";
    public static final String EXPORT_CONFIGURATION = "Export Configuration";
    public static final String VIEW_SYSTEM_INFO_PERMISSION = "View System Information";

    public static final String[] PERMISSIONS = new String[]{
            ADMINISTRATOR,
            ANNOTATIONS,
            MANAGE_CACHE_PERMISSION,
            VIEW_DATA_PERMISSION,
            VIEW_DATA_WITH_PIPELINE_PERMISSION,
            DELETE_DATA_PERMISSION,
            IMPORT_DATA_PERMISSION,
            EXPORT_DATA_PERMISSION,
            MANAGE_USERS_PERMISSION,
            STEPPING_PERMISSION,
            MANAGE_PROCESSORS_PERMISSION,
            MANAGE_TASKS_PERMISSION,
            DOWNLOAD_SEARCH_RESULTS_PERMISSION,
            MANAGE_JOBS_PERMISSION,
            MANAGE_PROPERTIES_PERMISSION,
            MANAGE_POLICIES_PERMISSION,
            MANAGE_NODES_PERMISSION,
            MANAGE_INDEX_SHARDS_PERMISSION,
            MANAGE_VOLUMES_PERMISSION,
            MANAGE_DB_PERMISSION,
            IMPORT_CONFIGURATION,
            EXPORT_CONFIGURATION,
            VIEW_SYSTEM_INFO_PERMISSION
    };

    // TODO: 11/01/2023 This whole class ought to be refactored to an enum
    private static final Map<String, String> PERMISSION_TO_DESCRIPTION_MAP = new HashMap<>();
    static {
        PERMISSION_TO_DESCRIPTION_MAP.put(
                ADMINISTRATOR,
                "Full administrator rights to access and manage everything.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                ANNOTATIONS,
                "Create and view annotations in query results.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_CACHE_PERMISSION,
                "Access the Caches screen to view and clear system caches.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                VIEW_DATA_PERMISSION,
                "View stream data (e.g. in the Data Viewer or a Dashboard text pane).");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                VIEW_DATA_WITH_PIPELINE_PERMISSION,
                "View data in a Dashboard text pane that uses a pipeline.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                DELETE_DATA_PERMISSION,
                "Delete streams.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                IMPORT_DATA_PERMISSION,
                "Upload stream data into a feed.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                EXPORT_DATA_PERMISSION,
                "Download/export streams from a feed.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_USERS_PERMISSION,
                "Access the screens to manage users, groups, permissions and API keys.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                STEPPING_PERMISSION,
                "Step data through a pipeline using the Stepper.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_PROCESSORS_PERMISSION,
                "Access the Processors tab and manage the processors/filters used to process stream data through " +
                        "pipelines.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_TASKS_PERMISSION,
                "Access the Server Tasks screen to view/stop tasks running on the nodes.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                DOWNLOAD_SEARCH_RESULTS_PERMISSION,
                "Download search result data on a Dashboard.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_JOBS_PERMISSION,
                "Access the Jobs screen to manage Stroom's background jobs.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_PROPERTIES_PERMISSION,
                "Access to the Properties to manage the system configuration.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_POLICIES_PERMISSION,
                "Access the Data Retention screen to manage data retention rules.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_NODES_PERMISSION,
                "Access the Nodes screen to view the nodes the cluster and manage their priority and enabled states.");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_INDEX_SHARDS_PERMISSION,
                "");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_VOLUMES_PERMISSION,
                "");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                MANAGE_DB_PERMISSION,
                "");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                IMPORT_CONFIGURATION,
                "");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                EXPORT_CONFIGURATION,
                "");
        PERMISSION_TO_DESCRIPTION_MAP.put(
                VIEW_SYSTEM_INFO_PERMISSION,
                "");
    }

    private PermissionNames() {
        // Constants
    }

    public static String getDescription(final String permissionName) {
        if (permissionName == null || permissionName.isEmpty()) {
            return null;
        } else {
            return PERMISSION_TO_DESCRIPTION_MAP.get(permissionName);
        }
    }
}
