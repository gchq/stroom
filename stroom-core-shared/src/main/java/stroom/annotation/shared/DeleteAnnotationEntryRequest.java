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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DeleteAnnotationEntryRequest {

    @JsonProperty
    private final AnnotationIdentity annotationIdentity;
    @JsonProperty
    private final AnnotationEntryType annotationEntryType;
    @JsonProperty
    private final long annotationEntryId;

    @JsonCreator
    public DeleteAnnotationEntryRequest(
            @JsonProperty("annotationIdentity") final AnnotationIdentity annotationIdentity,
            @JsonProperty("annotationEntryType") final AnnotationEntryType annotationEntryType,
            @JsonProperty("annotationEntryId") final long annotationEntryId) {
        this.annotationIdentity = annotationIdentity;
        this.annotationEntryType = annotationEntryType;
        this.annotationEntryId = annotationEntryId;
    }

    public AnnotationIdentity getAnnotationIdentity() {
        return annotationIdentity;
    }

    public AnnotationEntryType getAnnotationEntryType() {
        return annotationEntryType;
    }

    public long getAnnotationEntryId() {
        return annotationEntryId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteAnnotationEntryRequest that = (DeleteAnnotationEntryRequest) o;
        return annotationEntryId == that.annotationEntryId && Objects.equals(annotationIdentity,
                that.annotationIdentity) && annotationEntryType == that.annotationEntryType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationIdentity, annotationEntryType, annotationEntryId);
    }

    @Override
    public String toString() {
        return "DeleteAnnotationEntryRequest{" +
               "annotationIdentity=" + annotationIdentity +
               ", annotationEntryType=" + annotationEntryType +
               ", annotationEntryId=" + annotationEntryId +
               '}';
    }
}
