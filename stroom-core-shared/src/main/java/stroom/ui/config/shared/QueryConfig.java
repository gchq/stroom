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

    public QueryConfig() {
        infoPopup = new InfoPopupConfig();

        // At some point we might want to do this if we are certain
        dashboardPipelineSelectorIncludedTags = StandardExplorerTags.asTagNameSet(StandardExplorerTags.EXTRACTION);
        viewPipelineSelectorIncludedTags = StandardExplorerTags.asTagNameSet(
                StandardExplorerTags.EXTRACTION,
                StandardExplorerTags.DYNAMIC);
    }

    //    @Inject
    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public QueryConfig(
            @JsonProperty("infoPopup") final InfoPopupConfig infoPopup,
            @JsonProperty("dashboardPipelineSelectorIncludedTags") final Set<String> dashboardPipelineSelectorIncludedTags,
            @JsonProperty("viewPipelineSelectorIncludedTags") final Set<String> viewPipelineSelectorIncludedTags) {
        this.infoPopup = infoPopup;
//        if (infoPopup != null) {
//            this.infoPopup = infoPopup;
//        } else {
//            this.infoPopup = new InfoPopupConfig();
//        }
        this.dashboardPipelineSelectorIncludedTags = dashboardPipelineSelectorIncludedTags;
        this.viewPipelineSelectorIncludedTags = viewPipelineSelectorIncludedTags;
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
                && Objects.equals(viewPipelineSelectorIncludedTags, that.viewPipelineSelectorIncludedTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(infoPopup, dashboardPipelineSelectorIncludedTags, viewPipelineSelectorIncludedTags);
    }
}
