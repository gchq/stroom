package stroom.explorer.shared;

import stroom.util.shared.SharedObject;

import java.util.Set;

public class FindExplorerNodeCriteria implements SharedObject {
    private static final long serialVersionUID = 6474393620176001033L;

    private Set<ExplorerNode> openItems;
    private ExplorerTreeFilter filter;
    private Integer minDepth;
    private Set<ExplorerNode> ensureVisible;

    public FindExplorerNodeCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindExplorerNodeCriteria(final Set<ExplorerNode> openItems, final ExplorerTreeFilter filter, final Integer minDepth, final Set<ExplorerNode> ensureVisible) {
        this.openItems = openItems;
        this.filter = filter;
        this.minDepth = minDepth;
        this.ensureVisible = ensureVisible;
    }

    public Set<ExplorerNode> getOpenItems() {
        return openItems;
    }

    public ExplorerTreeFilter getFilter() {
        return filter;
    }

    public Integer getMinDepth() {
        return minDepth;
    }

    public Set<ExplorerNode> getEnsureVisible() {
        return ensureVisible;
    }
}
