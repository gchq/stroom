package stroom.index;

import stroom.index.service.IndexVolumeGroupService;
import stroom.index.shared.IndexVolumeGroup;

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
    public IndexVolumeGroup create(final String name) {
        final IndexVolumeGroup group = new IndexVolumeGroup.Builder()
                .name(name)
                .createTimeMs(System.currentTimeMillis())
                .createUser(TEST_USER)
                .build();
        groups.add(group);
        return group;
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return groups.stream()
                .filter(g -> name.equals(g.getName()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void delete(final String name) {
        groups.removeIf(i -> name.equals(i.getName()));
    }
}
