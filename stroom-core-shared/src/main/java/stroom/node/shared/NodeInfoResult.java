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

package stroom.node.shared;

import stroom.entity.shared.EntityRow;

public class NodeInfoResult extends EntityRow<Node> {
    private static final long serialVersionUID = -6143973264434353978L;

    private String ping;
    private long pingMs;
    private boolean master;

    public NodeInfoResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public String getPing() {
        return ping;
    }

    public void setPing(final String ping) {
        this.ping = ping;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(final boolean master) {
        this.master = master;
    }

    public long getPingMs() {
        return pingMs;
    }

    public void setPingMs(final long pingMs) {
        this.pingMs = pingMs;
    }
}
