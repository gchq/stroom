package stroom.explorer.shared;

import stroom.util.shared.SharedObject;

import java.util.Set;

public class FindExplorerNodeCriteria implements SharedObject {
    private static final long serialVersionUID = 6474393620176001033L;

    private Set<ExplorerNode> openItems;
    private Set<ExplorerNode> temporaryOpenedItems;
    private ExplorerTreeFilter filter;
    private Integer minDepth;
    private Set<ExplorerNode> ensureVisible;

    public FindExplorerNodeCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindExplorerNodeCriteria(final Set<ExplorerNode> openItems,
                                    final Set<ExplorerNode> temporaryOpenedItems,
                                    final ExplorerTreeFilter filter,
                                    final Integer minDepth,
                                    final Set<ExplorerNode> ensureVisible) {
        this.openItems = openItems;
        this.temporaryOpenedItems = temporaryOpenedItems;
        this.filter = filter;
        this.minDepth = minDepth;
        this.ensureVisible = ensureVisible;
    }

    public Set<ExplorerNode> getOpenItems() {
        return openItems;
    }

    public Set<ExplorerNode> getTemporaryOpenedItems() {
        return temporaryOpenedItems;
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
