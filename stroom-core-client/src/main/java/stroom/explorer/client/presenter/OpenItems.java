package stroom.explorer.client.presenter;

import java.util.HashSet;
import java.util.Set;

public class OpenItems<T> {
    private final Set<T> openItems = new HashSet<>();
    private Set<T> temporaryOpenItems = new HashSet<>();

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

    Set<T> getAllOpenItems() {
        // Ensure that we always get a new set returned so that changes to the open items after this set is returned are
        // not reflected in the returned set.
        final HashSet<T> combined = new HashSet<>();
        combined.addAll(openItems);
        combined.addAll(temporaryOpenItems);
        return combined;
    }

    Set<T> getOpenItems() {
        return new HashSet<>(this.openItems);
    }

    Set<T> getTemporaryOpenItems() {
        return new HashSet<>(this.temporaryOpenItems);
    }

    void setTemporaryOpenItems(final Set<T> temporaryOpenItems) {
        this.temporaryOpenItems = temporaryOpenItems;
    }
}
