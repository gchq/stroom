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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SingleAnnotationChangeRequest {

    @JsonProperty
    private final DocRef annotationRef;
    @JsonProperty
    private final Long annotationId;
    @JsonProperty
    private final AbstractAnnotationChange change;

    @JsonCreator
    public SingleAnnotationChangeRequest(@JsonProperty("annotationRef") final DocRef annotationRef,
                                         @JsonProperty("annotationId") final Long annotationId,
                                         @JsonProperty("change") final AbstractAnnotationChange change) {
        this.annotationRef = annotationRef;
        this.annotationId = annotationId;
        this.change = change;
    }

    public SingleAnnotationChangeRequest(final DocRef annotationRef,
                                         final AbstractAnnotationChange change) {
        this(annotationRef, null, change);
    }

    public SingleAnnotationChangeRequest(final AnnotationIdentity annotationIdentity,
                                         final AbstractAnnotationChange change) {
        this(Objects.requireNonNull(annotationIdentity).getDocRef(),
                annotationIdentity.getId(),
                change);
    }

    public DocRef getAnnotationRef() {
        return annotationRef;
    }

    /**
     * @return The Annotaion ID, if known, else null;
     */
    public Long getAnnotationId() {
        return annotationId;
    }

    public boolean hasAnnotationId() {
        return annotationId != null;
    }

    public AbstractAnnotationChange getChange() {
        return change;
    }

    /**
     * Clone this {@link SingleAnnotationChangeRequest}, adding the supplied annotationId
     */
    public SingleAnnotationChangeRequest withAnnotationId(final long annotationId) {
        return new SingleAnnotationChangeRequest(annotationRef, annotationId, change);
    }

    @Override
    public String toString() {
        return "SingleAnnotationChangeRequest{" +
               "annotationRef=" + annotationRef +
               ", annotationId=" + annotationId +
               ", change=" + change +
               '}';
    }
}
