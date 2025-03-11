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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "layout", propOrder = {"preferredSize"})
@XmlSeeAlso({SplitLayoutConfig.class, TabLayoutConfig.class})
@Deprecated
public abstract class LayoutConfig implements SharedObject {

    private static final long serialVersionUID = 8743223047838956165L;

    /**
     * The preferred size of this layout in width, height.
     */
    @XmlElement(name = "preferredSize")
    private Size preferredSize = new Size();

    private transient SplitLayoutConfig parent;

    public Size getPreferredSize() {
        return preferredSize;
    }

    public void setPreferredSize(final Size preferredSize) {
        this.preferredSize = preferredSize;
    }

    public SplitLayoutConfig getParent() {
        return parent;
    }

    public void setParent(final SplitLayoutConfig parent) {
        this.parent = parent;
    }
}
