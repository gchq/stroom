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

package stroom.query.shared;

import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "automate", propOrder = {"open", "refresh", "refreshInterval"})
@XmlRootElement(name = "automate")
public class Automate extends ComponentSettings implements SharedObject {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "open")
    private boolean open;

    @XmlElement(name = "refresh")
    private boolean refresh;

    @XmlElement(name = "refreshInterval")
    private int refreshInterval = 10;

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

    public int getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(final int refreshInterval) {
        this.refreshInterval = refreshInterval;
    }
}
