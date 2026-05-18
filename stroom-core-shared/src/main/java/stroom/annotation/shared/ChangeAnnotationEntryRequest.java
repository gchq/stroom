/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.annotation.shared;

import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ChangeAnnotationEntryRequest {

    @JsonProperty
    private final AnnotationIdentity annotationIdentity;
    @JsonProperty
    private final long annotationEntryId;
    @JsonProperty
    private final AnnotationEntryType annotationEntryType;
    @JsonProperty
    private final String data;

    @JsonCreator
    public ChangeAnnotationEntryRequest(
            @JsonProperty("annotationIdentity") final AnnotationIdentity annotationIdentity,
            @JsonProperty("annotationEntryId") final long annotationEntryId,
            @JsonProperty("annotationEntryType") final AnnotationEntryType annotationEntryType,
            @JsonProperty("data") final String data) {

        this.annotationIdentity = Objects.requireNonNull(annotationIdentity);
        this.annotationEntryId = annotationEntryId;
        this.annotationEntryType = annotationEntryType;
        this.data = data;
    }

    @SerialisationTestConstructor
    public ChangeAnnotationEntryRequest() {
        this.annotationIdentity = new AnnotationIdentity("my-uuid", 1);
        this.annotationEntryId = 1;
        this.annotationEntryType = AnnotationEntryType.COMMENT;
        this.data = null;
    }

    public AnnotationIdentity getAnnotationIdentity() {
        return annotationIdentity;
    }

    public long getAnnotationEntryId() {
        return annotationEntryId;
    }

    public AnnotationEntryType getAnnotationEntryType() {
        return annotationEntryType;
    }

    public String getData() {
        return data;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ChangeAnnotationEntryRequest that = (ChangeAnnotationEntryRequest) o;
        return annotationEntryId == that.annotationEntryId && Objects.equals(annotationIdentity,
                that.annotationIdentity) && annotationEntryType == that.annotationEntryType && Objects.equals(data,
                that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationIdentity, annotationEntryId, annotationEntryType, data);
    }

    @Override
    public String toString() {
        return "ChangeAnnotationEntryRequest{" +
               "annotationIdentity=" + annotationIdentity +
               ", annotationEntryId=" + annotationEntryId +
               ", annotationEntryType=" + annotationEntryType +
               ", data='" + data + '\'' +
               '}';
    }
}
