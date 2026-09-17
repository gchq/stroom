package stroom.pipeline.xsltfunctions;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the stepping additions to {@link TaskScopeMap} - the snapshot/restore/clear that let stepping scope
 * {@code stroom:put}/{@code get} to a record and hand a reprocess the puts made by elements it does not
 * re-run. The normal processing path uses only put/get and is unaffected.
 */
class TestTaskScopeMap {

    @Test
    void testSnapshotIsADetachedCopy() {
        final TaskScopeMap map = new TaskScopeMap();
        map.put("user", "jbloggs");

        final Map<String, String> snapshot = map.snapshot();
        map.put("user", "changed");
        map.put("host", "server1");

        assertThat(snapshot).containsExactlyEntriesOf(Map.of("user", "jbloggs"));
    }

    @Test
    void testClearScopesToTheRecord() {
        final TaskScopeMap map = new TaskScopeMap();
        map.put("user", "jbloggs");
        map.clear();

        assertThat(map.get("user")).isNull();
    }

    @Test
    void testRestoreReplacesRatherThanMerges() {
        final TaskScopeMap map = new TaskScopeMap();
        map.put("stale", "fromAnEarlierRecord");
        map.restore(Map.of("user", "jbloggs"));

        assertThat(map.get("user")).isEqualTo("jbloggs");
        assertThat(map.get("stale")).isNull();
    }

    @Test
    void testRestoredEntriesCanBeOverwrittenByTheRerunElements() {
        // A reprocess restores what upstream put, then the re-run elements put their own values over the top.
        final TaskScopeMap map = new TaskScopeMap();
        map.restore(Map.of("upstream", "kept", "own", "old"));
        map.put("own", "new");

        assertThat(map.get("upstream")).isEqualTo("kept");
        assertThat(map.get("own")).isEqualTo("new");
    }

    @Test
    void testRestoreOfNullOrEmptyJustClears() {
        final TaskScopeMap map = new TaskScopeMap();
        map.put("stale", "value");
        map.restore(null);
        assertThat(map.get("stale")).isNull();

        map.put("stale", "value");
        map.restore(Map.of());
        assertThat(map.get("stale")).isNull();
    }

    @Test
    void testRestoreIsDetachedFromTheSourceMap() {
        final TaskScopeMap map = new TaskScopeMap();
        final Map<String, String> source = new HashMap<>(Map.of("k", "v"));
        map.restore(source);
        source.clear();

        assertThat(map.get("k")).isEqualTo("v");
    }
}
