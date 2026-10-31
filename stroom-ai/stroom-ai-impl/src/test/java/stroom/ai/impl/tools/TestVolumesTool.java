/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl.tools;

import stroom.data.store.api.FsVolumeStates;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.index.api.IndexVolumeStates;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;

import event.logging.SearchEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestVolumesTool {

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final FsVolumeStates fsVolumeStates = Mockito.mock(FsVolumeStates.class);
    private final IndexVolumeStates indexVolumeStates = Mockito.mock(IndexVolumeStates.class);

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        // The volumes are read as the processing user, the user's view decided by the tool.
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(fixture.securityContext).asProcessingUser(Mockito.any(Runnable.class));
        Mockito.when(fsVolumeStates.volumesByGroup()).thenReturn(Map.of("Default", List.of(FsVolume.builder()
                .path("/stroom/volumes/1")
                .status(VolumeUseStatus.ACTIVE)
                .byteLimit(100L)
                .volumeType(FsVolumeType.STANDARD)
                .volumeState(new FsVolumeState(1, 1, 100L, 900L, 1_000L, 0L))
                .build())));
        Mockito.when(fsVolumeStates.defaultGroup()).thenReturn(Optional.of("Default"));
        Mockito.when(indexVolumeStates.volumesByGroup()).thenReturn(Map.of("Group1", List.of(IndexVolume.builder()
                .nodeName("node1")
                .path("/stroom/index/1")
                .state(VolumeUseState.ACTIVE)
                .bytesUsed(10L)
                .bytesFree(990L)
                .bytesTotal(1_000L)
                .build())));
        Mockito.when(indexVolumeStates.defaultGroup()).thenReturn(Optional.of("Group1"));
    }

    @Test
    void withManageVolumesEachVolumeIsGiven() {
        manageVolumes(true);

        final Map<String, Object> result = run("{}");

        assertThat(result).doesNotContainKey("view");
        assertThat(result.toString())
                .contains("/stroom/volumes/1", "/stroom/index/1")
                .contains("full=true")
                .contains("writable=false");
        assertThat(result).containsKey("behaviour");
        assertThat(fixture.logged(SearchEventAction.class)).singleElement()
                .satisfies(event -> assertThat(event.getData()).anySatisfy(data -> assertThat(data.getValue())
                        .isEqualTo("Full")));
    }

    @Test
    void withoutManageVolumesOnlyWhetherEachGroupCanBeWrittenToIsGiven() {
        manageVolumes(false);

        final Map<String, Object> result = run("{}");

        assertThat(result).containsEntry("view", VolumeReport.REDUCED);
        assertThat(result.toString())
                .doesNotContain("/stroom/volumes/1", "/stroom/index/1")
                .contains("group=Default", "writable=false", "group=Group1", "writable=true");
        // What full volumes do is said either way.
        assertThat(result).containsKey("behaviour");
    }

    @Test
    void aKindOfVolumeCanBeAskedForAlone() {
        manageVolumes(true);

        final Map<String, Object> data = run("{\"kind\":\"data\"}");
        final Map<String, Object> index = run("{\"kind\":\"index\"}");

        assertThat(data).containsKey("data_volume_groups").doesNotContainKey("index_volume_groups");
        assertThat(String.valueOf(((Map<?, ?>) data.get("behaviour")).keySet())).isEqualTo("[data_volumes]");
        assertThat(index).containsKey("index_volume_groups").doesNotContainKey("data_volume_groups");
        assertThat(String.valueOf(((Map<?, ?>) index.get("behaviour")).keySet())).isEqualTo("[index_volumes]");
        Mockito.verify(indexVolumeStates, Mockito.times(1)).volumesByGroup();
        Mockito.verify(fsVolumeStates, Mockito.times(1)).volumesByGroup();
    }

    @Test
    void anUnknownKindIsRefused() {
        final ToolResult result = tool().execute(ToolTestFixture.request(VolumesTool.NAME, "{\"kind\":\"disk\"}"),
                ToolCalls.inChat(1, fixture.workspace));

        assertThat(result.text()).contains("kind must be one of data, index or both");
        Mockito.verifyNoInteractions(fsVolumeStates, indexVolumeStates);
    }

    private void manageVolumes(final boolean held) {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_VOLUMES_PERMISSION))
                .thenReturn(held);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> run(final String arguments) {
        final String text = tool().execute(ToolTestFixture.request(VolumesTool.NAME, arguments),
                ToolCalls.inChat(1, fixture.workspace)).text();
        return JsonUtil.readValue(text, Map.class);
    }

    private VolumesTool tool() {
        return new VolumesTool(() -> fsVolumeStates, () -> indexVolumeStates, fixture.securityContext,
                fixture.auditLog());
    }
}
