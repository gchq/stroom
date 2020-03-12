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

package stroom.security.impl.session;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class SessionDetails {
    private static final long serialVersionUID = -7654691243590208784L;

    @JsonProperty
    private String userName;
    @JsonProperty
    private Long createMs;
    @JsonProperty
    private Long lastAccessedMs;
    @JsonProperty
    private String lastAccessedAgent;
    @JsonProperty
    private String nodeName;

    @JsonCreator
    SessionDetails(@JsonProperty("username") final String userName,
                   @JsonProperty("createMs") final Long createMs,
                   @JsonProperty("lastAccessedMss") final Long lastAccessedMs,
                   @JsonProperty("lastAccessedAgent") final String lastAccessedAgent,
                   @JsonProperty("nodeName") final String nodeName) {
        this.userName = userName;
        this.createMs = createMs;
        this.lastAccessedMs = lastAccessedMs;
        this.lastAccessedAgent = lastAccessedAgent;
        this.nodeName = nodeName;
    }

    public String getUserName() {
        return userName;
    }

//    public void setUserName(String userName) {
//        this.userName = userName;
//    }

    public Long getCreateMs() {
        return createMs;
    }

//    public void setCreateMs(Long createMs) {
//        this.createMs = createMs;
//    }

    public Long getLastAccessedMs() {
        return lastAccessedMs;
    }

//    public void setLastAccessedMs(Long lastAccessedMs) {
//        this.lastAccessedMs = lastAccessedMs;
//    }

    public String getNodeName() {
        return nodeName;
    }

//    public void setNodeName(String nodeName) {
//        this.nodeName = nodeName;
//    }

    public String getLastAccessedAgent() {
        return lastAccessedAgent;
    }

//    public void setLastAccessedAgent(String lastAccessedAgent) {
//        this.lastAccessedAgent = lastAccessedAgent;
//    }


}
