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

import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.SharedObject;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "format", propOrder = { "type", "settings" })
public class Format implements SharedObject {
    public enum Type implements HasDisplayValue {
        GENERAL("General"), NUMBER("Number"), DATE_TIME("Date Time"), TEXT("Text");

        private final String displayValue;

        Type(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public static List<Type> TYPES = Arrays.asList(Type.GENERAL, Type.NUMBER, Type.DATE_TIME, Type.TEXT);

    private static final long serialVersionUID = -5380825645719299089L;

    @XmlElement(name = "type")
    private Type type;
    @XmlElements({ @XmlElement(name = "numberFormatSettings", type = NumberFormatSettings.class),
            @XmlElement(name = "dateTimeFormatSettings", type = DateTimeFormatSettings.class) })
    private FormatSettings settings;

    public Format() {
        // Default constructor necessary for GWT serialisation.
    }

    public Format(final Type type) {
        this.type = type;
    }

    public Format(final Type type, final FormatSettings settings) {
        this.type = type;
        this.settings = settings;
    }

    public Type getType() {
        return type;
    }

    public void setType(final Type type) {
        this.type = type;
    }

    public FormatSettings getSettings() {
        return settings;
    }

    public void setSettings(final FormatSettings settings) {
        this.settings = settings;
    }
}
