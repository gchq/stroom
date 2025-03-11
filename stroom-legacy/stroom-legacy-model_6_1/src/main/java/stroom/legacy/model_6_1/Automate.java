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
 */

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "automate", propOrder = {"open", "refresh", "refreshInterval"})
@XmlRootElement(name = "automate")
@Deprecated
public class Automate implements SharedObject {

    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "open")
    private boolean open;

    @XmlElement(name = "refresh")
    private boolean refresh;

    @XmlElement(name = "refreshInterval")
    private String refreshInterval = "10s";

    public Automate() {
        // Default constructor necessary for GWT serialisation.
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(final boolean open) {
        this.open = open;
    }

    public boolean isRefresh() {
        return refresh;
    }

    public void setRefresh(final boolean refresh) {
        this.refresh = refresh;
    }

    public String getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(final String refreshInterval) {
        this.refreshInterval = refreshInterval;
    }
}
