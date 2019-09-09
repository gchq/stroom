package stroom.index.impl.service;

import stroom.index.impl.CreateVolumeDTO;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.UpdateVolumeDTO;
import stroom.index.shared.IndexVolume;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;

public class IndexVolumeServiceImpl implements IndexVolumeService {
    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao,
                           final SecurityContext securityContext) {
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
    }

    @Override
    public IndexVolume create(CreateVolumeDTO createVolumeDTO) {
        final IndexVolume indexVolume = new IndexVolume();
        AuditUtil.stamp(securityContext.getUserId(), indexVolume);

        var names = indexVolumeDao.getAll().stream().map(i -> isNullOrEmpty(i.getNodeName()) ? "" : i.getNodeName())
                .collect(Collectors.toList());
        indexVolume.setNodeName(isNullOrEmpty(createVolumeDTO.getNodeName())
                ? NextNameGenerator.getNextName(names, "New index volume")
                : createVolumeDTO.getNodeName());
        indexVolume.setPath(isNullOrEmpty(createVolumeDTO.getPath()) ? null : createVolumeDTO.getPath());
        indexVolume.setIndexVolumeGroupName(createVolumeDTO.getIndexVolumeGroupName());

        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.create(indexVolume));
    }

    @Override
    public IndexVolume getById(final int id) {
        return securityContext.secureResult(() -> indexVolumeDao.fetch(id).orElse(null));
    }

    @Override
    public List<IndexVolume> getAll() {
        return securityContext.secureResult(indexVolumeDao::getAll);
    }

    @Override
    public IndexVolume update(UpdateVolumeDTO updateVolumeDTO) {
        final var indexVolume = securityContext.secureResult(() -> indexVolumeDao.fetch(updateVolumeDTO.getId()).orElse(null));

        // Map from DTO to entity
        indexVolume.setIndexVolumeGroupName(updateVolumeDTO.getIndexVolumeGroupName());
        indexVolume.setPath((updateVolumeDTO.getPath()));
        indexVolume.setNodeName(updateVolumeDTO.getNodeName());

        AuditUtil.stamp(securityContext.getUserId(), indexVolume);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.update(indexVolume));
    }

//    @Override
//    public List<IndexVolume> getVolumesInGroup(final String groupName) {
//        return securityContext.secureResult(() -> indexVolumeDao.getVolumesInGroup(groupName));
//    }

//    @Override
//    public List<IndexVolumeGroup> getGroupsForVolume(final int id) {
//        return securityContext.secureResult(() -> indexVolumeDao.getGroupsForVolume(id));
//    }

//    @Override
//    public void addVolumeToGroup(final int volumeId,
//                                 final String name) {
//        securityContext.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
//                () -> indexVolumeDao.addVolumeToGroup(volumeId, name));
//    }
//
//    @Override
//    public void removeVolumeFromGroup(final int volumeId,
//                                      final String name) {
//        securityContext.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
//                () -> indexVolumeDao.removeVolumeFromGroup(volumeId, name));
//    }

    @Override
    public void delete(final int id) {
        securityContext.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.delete(id));
    }

    @Override
    public void clear() {

    }

    @Override
    public void flush() {

    }
}
