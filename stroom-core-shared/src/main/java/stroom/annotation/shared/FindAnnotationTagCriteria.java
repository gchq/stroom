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

package stroom.annotation.shared;

import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.QuickFilterCriteria;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Finds annotation tags of one {@link AnnotationTagType}, optionally narrowed by a quick filter.
 * <p>
 * The type is a field in its own right rather than a term in an expression, because it is not a
 * filter: it says what kind of thing is being looked for, and every caller of this endpoint knows
 * which kind it wants. Expressing it as a term would put it in the same space as the user's own
 * input, where a filter such as {@code typeid:label} could change what the screen is showing
 * rather than narrowing it.
 * <p>
 * For the same reason this does not extend {@code ExpressionCriteria}: there is no legitimate
 * arbitrary expression for this endpoint, so it does not offer one.
 */
@JsonInclude(Include.NON_NULL)
public class FindAnnotationTagCriteria extends QuickFilterCriteria {

    @JsonProperty
    private final AnnotationTagType type;

    public FindAnnotationTagCriteria(final AnnotationTagType type) {
        this(null, null, type, null);
    }

    public FindAnnotationTagCriteria(final AnnotationTagType type, final String quickFilter) {
        this(null, null, type, quickFilter);
    }

    @JsonCreator
    public FindAnnotationTagCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                     @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                     @JsonProperty("type") final AnnotationTagType type,
                                     @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList, quickFilter);
        this.type = type;
    }

    public AnnotationTagType getType() {
        return type;
    }


    @Override
    public String toString() {
        return "FindAnnotationTagCriteria{" +
               "type=" + type +
               ", quickFilter='" + getQuickFilter() + '\'' +
               '}';
    }
}
