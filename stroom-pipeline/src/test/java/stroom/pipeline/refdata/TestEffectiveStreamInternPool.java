package stroom.pipeline.refdata;

import stroom.meta.api.EffectiveMeta;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class TestEffectiveStreamInternPool {

    @Test
    void name() {

        final EffectiveStreamInternPool internPool = new EffectiveStreamInternPool();

        final int iterations = 100;
        final List<NavigableSet<EffectiveMeta>> setList = new ArrayList<>(6);
        final List<NavigableSet<EffectiveMeta>> internedSetList = new ArrayList<>(6);

        for (int i = 1; i <= 6; i++) {
            final NavigableSet<EffectiveMeta> set = new TreeSet<>();
            final int id = i % 2 == 0
                    ? 2
                    : 1;
            for (int timeMs = 1; timeMs <= iterations; timeMs++) {
                set.add(buildEffectiveMeta(id, timeMs));
            }
            setList.add(set);
            final NavigableSet<EffectiveMeta> internedSet = internPool.intern(set);
            assertThat(internedSet)
                    .isEqualTo(set);
            internedSetList.add(internedSet);
        }

        // 6 added, but 3 for id=1, 3 for id=2, so only 2 interned
        assertThat(internPool.size())
                .isEqualTo(2);
        assertThat(setList.size())
                .isEqualTo(6);
        assertThat(internedSetList.size())
                .isEqualTo(6);
        // Verify the instances interned
        assertThat(System.identityHashCode(setList.get(0)))
                .isEqualTo(System.identityHashCode(internedSetList.get(0)))
                .isEqualTo(System.identityHashCode(internedSetList.get(2)))
                .isEqualTo(System.identityHashCode(internedSetList.get(4)));
        assertThat(System.identityHashCode(setList.get(1)))
                .isEqualTo(System.identityHashCode(internedSetList.get(1)))
                .isEqualTo(System.identityHashCode(internedSetList.get(3)))
                .isEqualTo(System.identityHashCode(internedSetList.get(5)));
    }

    private EffectiveMeta buildEffectiveMeta(final long id, final long effectiveMs) {
        return new EffectiveMeta(id, "DUMMY_FEED", "DummyType", effectiveMs);
    }
}
