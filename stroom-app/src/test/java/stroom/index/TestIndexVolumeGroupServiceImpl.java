package stroom.index;

import stroom.index.impl.IndexVolumeGroupService;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexVolumeGroup;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.capacity.RoundRobinCapacitySelector;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class TestIndexVolumeGroupServiceImpl extends AbstractCoreIntegrationTest {

    @Inject
    private IndexVolumeGroupService indexVolumeGroupService;
    @Inject
    private Provider<VolumeConfig> volumeConfigProvider;

    @Test
    void testDefaultGroups() {

        Assertions.assertThat(volumeConfigProvider.get().isCreateDefaultIndexVolumesOnStart())
                        .isTrue();

        setConfigValueMapper(VolumeConfig.class, volumeConfig ->
                        volumeConfig.withVolumeSelector(RoundRobinCapacitySelector.NAME));

        final String defaultIndexVolumeGroupName = volumeConfigProvider.get().getDefaultIndexVolumeGroupName();

        Assertions.assertThat(indexVolumeGroupService.getNames())
                .containsExactly(defaultIndexVolumeGroupName);
    }

    @Test
    void testCreateGroup() {
        final List<String> groupNames = new ArrayList<>(indexVolumeGroupService.getNames());

        final String newGroupname = "newGroupName";

        Assertions.assertThat(groupNames)
                        .doesNotContain(newGroupname);

        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate(newGroupname);
        final IndexVolumeGroup volumeGroup2 = indexVolumeGroupService.getOrCreate(newGroupname);
        groupNames.add(newGroupname);

        Assertions.assertThat(volumeGroup)
                        .isEqualTo(volumeGroup2);

        Assertions.assertThat(indexVolumeGroupService.getNames())
                .containsExactlyElementsOf(groupNames);

        indexVolumeGroupService.delete(volumeGroup.getId());
        groupNames.remove(volumeGroup.getName());

        Assertions.assertThat(indexVolumeGroupService.getNames())
                .containsExactlyElementsOf(groupNames);
    }
}
