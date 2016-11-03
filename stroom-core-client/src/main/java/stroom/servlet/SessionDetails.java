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

package stroom.servlet;

import stroom.util.shared.SharedObject;

public class SessionDetails implements SharedObject {
    private static final long serialVersionUID = -7654691243590208784L;

    private String userName;
    private Long createMs;
    private Long lastAccessedMs;
    private String lastAccessedAgent;
    private String id;
    private String nodeName;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(Long createMs) {
        this.createMs = createMs;
    }

    public Long getLastAccessedMs() {
        return lastAccessedMs;
    }

    public void setLastAccessedMs(Long lastAccessedMs) {
        this.lastAccessedMs = lastAccessedMs;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getLastAccessedAgent() {
        return lastAccessedAgent;
    }

    public void setLastAccessedAgent(String lastAccessedAgent) {
        this.lastAccessedAgent = lastAccessedAgent;
    }

}
