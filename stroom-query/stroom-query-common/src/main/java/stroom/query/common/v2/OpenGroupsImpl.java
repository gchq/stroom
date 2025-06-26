package stroom.query.common.v2;

import java.util.HashSet;
import java.util.Set;

public class OpenGroupsImpl implements OpenGroups {

    private final Set<Key> openKeys;
    private boolean isOpen = true;

    public OpenGroupsImpl(final Key openKeys) {
        this.openKeys = new HashSet<>();
        this.openKeys.add(openKeys);
    }

    public static OpenGroups root() {
        return new OpenGroupsImpl(Key.ROOT_KEY);
    }

    public OpenGroupsImpl(final Set<Key> openKeys) {
        this.openKeys = new HashSet<>(openKeys);
    }

    public OpenGroupsImpl(final Set<Key> openKeys, final boolean isOpen) {
        this.openKeys = new HashSet<>(openKeys);
        this.isOpen = isOpen;
    }

    @Override
    public boolean isOpen(final Key key) {
        return openKeys.contains(key) == isOpen;
    }

    @Override
    public void complete(final Key key) {
        openKeys.remove(key);
    }

    @Override
    public boolean isNotEmpty() {
        return !openKeys.isEmpty();
    }
}
