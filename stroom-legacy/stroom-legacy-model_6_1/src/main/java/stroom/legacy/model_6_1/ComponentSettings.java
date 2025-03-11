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

import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;

@XmlType(name = "settings")
@XmlSeeAlso({
        QueryComponentSettings.class,
        TableComponentSettings.class,
        VisComponentSettings.class,
        TextComponentSettings.class})
@Deprecated
public abstract class ComponentSettings implements Serializable {

    private static final long serialVersionUID = 2110282486749818888L;

    public ComponentSettings() {
        // Default constructor necessary for GWT serialisation.
    }
}
