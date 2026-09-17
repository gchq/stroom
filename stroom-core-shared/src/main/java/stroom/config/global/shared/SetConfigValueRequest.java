/*
 * Copyright 2026 Crown Copyright
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

package stroom.config.global.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Sets a single config property to the supplied value. The value is sent in its natural form, and whichever of the
 * value fields is populated determines how it is stored, so that the client never has to know the string form a
 * property expects. This is the one mechanism for setting a property from the screen that owns it, whatever the
 * value's type.
 * <p>
 * The property is identified by its {@link ConfigTarget} plus the leaf property name rather than a full property
 * path, because a config object's base path is not serialised to the client. An unknown property name is rejected
 * by the config service.
 * </p>
 */
@JsonPropertyOrder({"target", "propertyName", "docRefValue", "stringValue"})
@JsonInclude(Include.NON_NULL)
public class SetConfigValueRequest {

    @JsonProperty
    private final ConfigTarget target;
    @JsonProperty
    private final String propertyName;
    @JsonProperty
    private final DocRef docRefValue;
    @JsonProperty
    private final String stringValue;

    @JsonCreator
    public SetConfigValueRequest(@JsonProperty("target") final ConfigTarget target,
                               @JsonProperty("propertyName") final String propertyName,
                               @JsonProperty("docRefValue") final DocRef docRefValue,
                               @JsonProperty("stringValue") final String stringValue) {
        this.target = target;
        this.propertyName = propertyName;
        this.docRefValue = docRefValue;
        this.stringValue = stringValue;
    }

    public static SetConfigValueRequest docRef(final ConfigTarget target,
                                             final String propertyName,
                                             final DocRef docRefValue) {
        return new SetConfigValueRequest(target, propertyName, docRefValue, null);
    }

    public static SetConfigValueRequest string(final ConfigTarget target,
                                             final String propertyName,
                                             final String stringValue) {
        return new SetConfigValueRequest(target, propertyName, null, stringValue);
    }

    public ConfigTarget getTarget() {
        return target;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public DocRef getDocRefValue() {
        return docRefValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SetConfigValueRequest that = (SetConfigValueRequest) o;
        return target == that.target
               && Objects.equals(propertyName, that.propertyName)
               && Objects.equals(docRefValue, that.docRefValue)
               && Objects.equals(stringValue, that.stringValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, propertyName, docRefValue, stringValue);
    }

    @Override
    public String toString() {
        return "SetConfigValueRequest{" +
               "target=" + target +
               ", propertyName='" + propertyName + '\'' +
               ", docRefValue=" + docRefValue +
               ", stringValue='" + stringValue + '\'' +
               '}';
    }
}
