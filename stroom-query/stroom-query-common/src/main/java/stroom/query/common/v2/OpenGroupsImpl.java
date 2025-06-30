package stroom.query.common.v2;

import java.util.HashSet;
import java.util.Set;

public class OpenGroupsImpl implements OpenGroups {

    private final Set<Key> openKeys;
    private final Set<Key> closedKeys;
    private int expandedDepth = 0;

    public OpenGroupsImpl(final Key openKeys) {
        this.closedKeys = new HashSet<>();
        this.openKeys = new HashSet<>();
        this.openKeys.add(openKeys);
    }

    public static OpenGroups root() {
        return new OpenGroupsImpl(Key.ROOT_KEY);
    }

    public OpenGroupsImpl(final Set<Key> openKeys) {
        this.closedKeys = new HashSet<>();
        this.openKeys = new HashSet<>(openKeys);
    }

    public OpenGroupsImpl(final int expandedDepth, final Set<Key> openKeys, final Set<Key> closedKeys) {
        this.closedKeys = new HashSet<>(closedKeys);
        this.openKeys = new HashSet<>(openKeys);
        this.expandedDepth = expandedDepth;
    }

    @Override
    public boolean isOpen(final Key key) {
        return (key.getDepth() < expandedDepth || openKeys.contains(key)) && !closedKeys.contains(key);
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
