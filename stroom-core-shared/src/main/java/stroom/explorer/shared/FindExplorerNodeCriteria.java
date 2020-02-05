package stroom.explorer.shared;

import java.util.Objects;
import java.util.Set;

public class FindExplorerNodeCriteria {
    private Set<String> openItems;
    private Set<String> temporaryOpenedItems;
    private ExplorerTreeFilter filter;
    private Integer minDepth;
    private Set<String> ensureVisible;

    public FindExplorerNodeCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindExplorerNodeCriteria(final Set<String> openItems,
                                    final Set<String> temporaryOpenedItems,
                                    final ExplorerTreeFilter filter,
                                    final Integer minDepth,
                                    final Set<String> ensureVisible) {
        this.openItems = openItems;
        this.temporaryOpenedItems = temporaryOpenedItems;
        this.filter = filter;
        this.minDepth = minDepth;
        this.ensureVisible = ensureVisible;
    }

    public Set<String> getOpenItems() {
        return openItems;
    }

    public void setOpenItems(final Set<String> openItems) {
        this.openItems = openItems;
    }

    public Set<String> getTemporaryOpenedItems() {
        return temporaryOpenedItems;
    }

    public void setTemporaryOpenedItems(final Set<String> temporaryOpenedItems) {
        this.temporaryOpenedItems = temporaryOpenedItems;
    }

    public ExplorerTreeFilter getFilter() {
        return filter;
    }

    public void setFilter(final ExplorerTreeFilter filter) {
        this.filter = filter;
    }

    public Integer getMinDepth() {
        return minDepth;
    }

    public void setMinDepth(final Integer minDepth) {
        this.minDepth = minDepth;
    }

    public Set<String> getEnsureVisible() {
        return ensureVisible;
    }

    public void setEnsureVisible(final Set<String> ensureVisible) {
        this.ensureVisible = ensureVisible;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FindExplorerNodeCriteria criteria = (FindExplorerNodeCriteria) o;
        return Objects.equals(openItems, criteria.openItems) &&
                Objects.equals(temporaryOpenedItems, criteria.temporaryOpenedItems) &&
                Objects.equals(filter, criteria.filter) &&
                Objects.equals(minDepth, criteria.minDepth) &&
                Objects.equals(ensureVisible, criteria.ensureVisible);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openItems, temporaryOpenedItems, filter, minDepth, ensureVisible);
    }
}
