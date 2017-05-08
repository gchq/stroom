package stroom.explorer.client.presenter;

import java.util.HashSet;

public class OpenItems<T> {
    private final HashSet<T> openItems = new HashSet<>();
    private HashSet<T> temporaryOpenItems = new HashSet<>();

    public void open(final T item) {
        openItems.add(item);
    }

    public void close(final T item) {
        openItems.remove(item);
        temporaryOpenItems.remove(item);
    }

    public void clear() {
        openItems.clear();
        temporaryOpenItems.clear();
    }

    public boolean isOpen(final T item) {
        return openItems.contains(item) || temporaryOpenItems.contains(item);
    }

    public boolean toggleOpenState(final T item) {
        if (isOpen(item)) {
            close(item);
            return false;
        } else {
            open(item);
            return true;
        }
    }

    HashSet<T> getAllOpenItems() {
        // Ensure that we always get a new set returned so that changes to the open items after this set is returned are
        // not reflected in the returned set.
        final HashSet<T> combined = new HashSet<>();
        combined.addAll(openItems);
        combined.addAll(temporaryOpenItems);
        return combined;
    }

    void setTemporaryOpenItems(final HashSet<T> temporaryOpenItems) {
        this.temporaryOpenItems = temporaryOpenItems;
    }
}
