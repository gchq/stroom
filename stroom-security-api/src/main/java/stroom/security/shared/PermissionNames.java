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

/**
 * Provide string constants for the permissions.
 */
public final class PermissionNames {
    /**
     * Administrators have UNRESTRICTED permission on SYSTEM
     */
    public static final String ADMINISTRATOR = "Administrator";
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

    public static final String[] PERMISSIONS = new String[]{
            ADMINISTRATOR,
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
            EXPORT_CONFIGURATION
    };

    private PermissionNames() {
        // Constants
    }
}
