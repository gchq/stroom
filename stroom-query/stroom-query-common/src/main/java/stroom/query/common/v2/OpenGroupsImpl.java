package stroom.query.common.v2;

import java.util.HashSet;
import java.util.Set;

public class OpenGroupsImpl implements OpenGroups {

    private final Set<Key> openKeys;

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

    @Override
    public boolean isOpen(final Key key) {
        return openKeys.contains(key);
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
