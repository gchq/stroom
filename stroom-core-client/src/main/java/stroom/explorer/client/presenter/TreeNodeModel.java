package stroom.explorer.client.presenter;

import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.user.cellview.client.TreeNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class TreeNodeModel<T> implements OpenHandler<TreeNode>, CloseHandler<TreeNode> {
    private final HashSet<T> openItems = new HashSet<T>();

    @SuppressWarnings("unchecked")
    @Override
    public void onOpen(final OpenEvent<TreeNode> event) {
        final TreeNode treeNode = event.getTarget();
        if (treeNode != null) {
            final T item = (T) treeNode.getValue();
            addOpenItem(item);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onClose(final CloseEvent<TreeNode> event) {
        final TreeNode treeNode = event.getTarget();
        if (treeNode != null) {
            final T item = (T) treeNode.getValue();
            removeOpenItem(item);
        }
    }

    public void addOpenItems(final Collection<T> items) {
        // Add is done this way so that subclasses can respond to addition of
        // individual items.
        for (final T item : items) {
            addOpenItem(item);
        }
    }

    protected void clearOpenItems() {
        // Clear is done this way so that subclasses can respond to removal of
        // individual items.
        final List<T> items = new ArrayList<T>(openItems);
        for (final T item : items) {
            removeOpenItem(item);
        }
    }

    protected void removeOpenItem(final T item) {
        openItems.remove(item);
    }

    protected void addOpenItem(final T item) {
        openItems.add(item);
    }

    public void toggleOpenState(final T item) {
        if (openItems.contains(item)) {
            openItems.remove(item);
        } else {
            openItems.add(item);
        }
    }

    public HashSet<T> getOpenItems() {
        // Ensure that we always get a new set returned so that changes to the open items after this set is returned are
        // not reflected in the returned set.
        final HashSet<T> set = new HashSet<T>();
        if (openItems != null) {
            set.addAll(openItems);
        }
        return set;
    }

    public void setOpenItems(final Collection<T> items) {
        clearOpenItems();
        addOpenItems(items);
    }
}
