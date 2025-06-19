package stroom.core.client;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;

@Singleton
public class HasSaveRegistry implements HasSave {

    private final Set<HasSave> set = new HashSet<>();

    public void register(final HasSave hasSave) {
        set.add(hasSave);
    }

    public void unregister(final HasSave hasSave) {
        set.remove(hasSave);
    }

    @Override
    public void save() {
        for (final HasSave hasSave : set) {
            hasSave.save();
        }
    }

    @Override
    public boolean isDirty() {
        for (final HasSave hasSave : set) {
            if (hasSave.isDirty()) {
                return true;
            }
        }
        return false;
    }
}
