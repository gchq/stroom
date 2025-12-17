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

package stroom.ui.config.shared;

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.AllMatchPattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    private final InfoPopupConfig infoPopup;
    @JsonProperty
    private final Set<String> dashboardPipelineSelectorIncludedTags;
    @JsonProperty
    private final Set<String> viewPipelineSelectorIncludedTags;
    @JsonProperty
    private final Set<String> indexPipelineSelectorIncludedTags;

    public QueryConfig() {
        infoPopup = new InfoPopupConfig();

        // At some point we might want to do this if we are certain
        dashboardPipelineSelectorIncludedTags = StandardExplorerTags.asTagNameSet(StandardExplorerTags.EXTRACTION);
        viewPipelineSelectorIncludedTags = StandardExplorerTags.asTagNameSet(StandardExplorerTags.EXTRACTION);
        indexPipelineSelectorIncludedTags = StandardExplorerTags.asTagNameSet(StandardExplorerTags.EXTRACTION);
    }

    //    @Inject
    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public QueryConfig(
            @JsonProperty("infoPopup") final InfoPopupConfig infoPopup,
            @JsonProperty("dashboardPipelineSelectorIncludedTags") final Set<String> dashboardPipelineSelectorIncludedTags,
            @JsonProperty("viewPipelineSelectorIncludedTags") final Set<String> viewPipelineSelectorIncludedTags,
            @JsonProperty("indexPipelineSelectorIncludedTags") final Set<String> indexPipelineSelectorIncludedTags) {
        this.infoPopup = infoPopup;
//        if (infoPopup != null) {
//            this.infoPopup = infoPopup;
//        } else {
//            this.infoPopup = new InfoPopupConfig();
//        }
        this.dashboardPipelineSelectorIncludedTags = dashboardPipelineSelectorIncludedTags;
        this.viewPipelineSelectorIncludedTags = viewPipelineSelectorIncludedTags;
        this.indexPipelineSelectorIncludedTags = indexPipelineSelectorIncludedTags;
    }

    public InfoPopupConfig getInfoPopup() {
        return infoPopup;
    }

    @AllMatchPattern(pattern = ExplorerNode.TAG_PATTERN_STR)
    @JsonPropertyDescription("Set of explorer tags to use as a filter on the Dashboard screen's Extraction Pipeline " +
            "selector. " +
            "Explorer nodes will only be included if they have at least all the tags in this property. " +
            "This property should contain a sub set of the tags in property stroom.explorer.suggestedTags")
    public Set<String> getDashboardPipelineSelectorIncludedTags() {
        return dashboardPipelineSelectorIncludedTags;
    }

    @AllMatchPattern(pattern = ExplorerNode.TAG_PATTERN_STR)
    @JsonPropertyDescription("Set of explorer tags to use as a filter on the View screen's Pipeline selector. " +
            "Explorer nodes will only be included if they have at least all the tags in this property. " +
            "This property should contain a sub set of the tags in property stroom.explorer.suggestedTags")
    public Set<String> getViewPipelineSelectorIncludedTags() {
        return viewPipelineSelectorIncludedTags;
    }

    @AllMatchPattern(pattern = ExplorerNode.TAG_PATTERN_STR)
    @JsonPropertyDescription("Set of explorer tags to use as a filter on the Index screen's Pipeline selector. " +
            "Explorer nodes will only be included if they have at least all the tags in this property. " +
            "This property should contain a sub set of the tags in property stroom.explorer.suggestedTags")
    public Set<String> getIndexPipelineSelectorIncludedTags() {
        return indexPipelineSelectorIncludedTags;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryConfig that = (QueryConfig) o;
        return Objects.equals(infoPopup, that.infoPopup)
                && Objects.equals(dashboardPipelineSelectorIncludedTags, that.dashboardPipelineSelectorIncludedTags)
                && Objects.equals(viewPipelineSelectorIncludedTags, that.viewPipelineSelectorIncludedTags)
                && Objects.equals(indexPipelineSelectorIncludedTags, that.indexPipelineSelectorIncludedTags);

    }

    @Override
    public int hashCode() {
        return Objects.hash(infoPopup,
                dashboardPipelineSelectorIncludedTags,
                viewPipelineSelectorIncludedTags,
                indexPipelineSelectorIncludedTags);
    }

    @Override
    public String toString() {
        return "QueryConfig{" +
                "infoPopup=" + infoPopup +
                ", dashboardPipelineSelectorIncludedTags=" + dashboardPipelineSelectorIncludedTags +
                ", viewPipelineSelectorIncludedTags=" + viewPipelineSelectorIncludedTags +
                ", indexPipelineSelectorIncludedTags=" + indexPipelineSelectorIncludedTags +
                '}';
    }
}
