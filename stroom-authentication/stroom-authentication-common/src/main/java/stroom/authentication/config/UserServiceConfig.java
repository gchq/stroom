/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.AbstractConfig;

import javax.validation.constraints.NotNull;

public class UserServiceConfig extends AbstractConfig {

    @NotNull
    @JsonProperty
    private String url = "http://localhost:8080/api/appPermissions/v";

////    @NotNull
////    @JsonProperty
////    private String canManageUsersPath;
//
//    @NotNull
//    @JsonProperty
//    private String canManageUsersPermission;

//    @NotNull
//    @JsonProperty
//    private String setStatusPath;

//    @NotNull
//    @JsonProperty
//    private String groupsForUserPath;
//
//    @NotNull
//    @JsonProperty
//    private String appPermissionsUrl;

    public String getUrl() {
        return url;
    }

//    public String getCanManageUsersUrl(){
//        return url + canManageUsersPath;
//    }

//    public String getCanManageUsersPermission() {
//        return canManageUsersPermission;
//    }

//    public String getSetStatusPath(){
//        return setStatusPath;
//    }

//    public String getGroupsForUserPath() {
//        return groupsForUserPath;
//    }
//
//    public String getGroupsForUserUrl() {
//        return url + groupsForUserPath;
//    }
//
//    public String getAppPermissionsUrl() {
//        return appPermissionsUrl;
//    }

}
