package stroom.index.mock;

import stroom.index.impl.IndexVolumeGroupService;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MockIndexVolumeGroupService implements IndexVolumeGroupService {

    private final List<IndexVolumeGroup> groups = new ArrayList<>();
    private static final String TEST_USER = "testVolumeGroupUser";

    @Override
    public List<String> getNames() {
        return groups.stream()
                .map(IndexVolumeGroup::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        return groups;
    }

    @Override
    public IndexVolumeGroup create() {
        final IndexVolumeGroup group = new IndexVolumeGroup();
        group.setName("New name");
        AuditUtil.stamp(() -> TEST_USER, group);
        groups.add(group);
        return group;
    }

    @Override
    public IndexVolumeGroup getOrCreate(final String name) {
        final IndexVolumeGroup group = new IndexVolumeGroup();
        group.setName(name);
        AuditUtil.stamp(() -> TEST_USER, group);
        groups.add(group);
        return group;
    }

    @Override
    public IndexVolumeGroup update(final IndexVolumeGroup indexVolumeGroup) {
        return null;
    }

    @Override
    public IndexVolumeGroup get(String name) {
        return null;
    }

    @Override
    public IndexVolumeGroup get(final int id) {
        return groups.stream()
                .filter(g -> id == g.getId())
                .findFirst()
                .orElse(null);
    }

    @Override
    public void delete(final int id) {
        groups.removeIf(i -> id == i.getId());
    }

    @Override
    public void ensureDefaultVolumes() {

    }
}
