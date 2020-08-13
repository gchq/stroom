package stroom.meta.impl.db;

import stroom.util.shared.Clearable;

import javax.inject.Inject;
import java.util.Set;

public class Cleanup {
    private final Set<Clearable> clearables;

    @Inject
    Cleanup(final Set<Clearable> clearables) {
        this.clearables = clearables;
    }

    public void cleanup() {
        // Clear all caches or files that might have been created by previous tests.
        clearables.forEach(Clearable::clear);
    }
}
