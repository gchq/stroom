package stroom.explorer.shared;

import stroom.util.shared.SharedObject;

import java.util.Set;

public class FindExplorerDataCriteria implements SharedObject {
    private static final long serialVersionUID = 6474393620176001033L;

    private Set<ExplorerData> openItems;
    private ExplorerTreeFilter filter;
    private Integer minDepth;
    private Set<ExplorerData> ensureVisible;

    public FindExplorerDataCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindExplorerDataCriteria(final Set<ExplorerData> openItems, final ExplorerTreeFilter filter, final Integer minDepth, final Set<ExplorerData> ensureVisible) {
        this.openItems = openItems;
        this.filter = filter;
        this.minDepth = minDepth;
        this.ensureVisible = ensureVisible;
    }

    public Set<ExplorerData> getOpenItems() {
        return openItems;
    }

    public ExplorerTreeFilter getFilter() {
        return filter;
    }

    public Integer getMinDepth() {
        return minDepth;
    }

    public Set<ExplorerData> getEnsureVisible() {
        return ensureVisible;
    }
}
