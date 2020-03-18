package stroom.index.impl;

import com.google.common.collect.ImmutableMap;
import stroom.index.shared.CreateVolumeRequest;
import stroom.index.shared.IndexVolume;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;

public class IndexVolumeServiceImpl implements IndexVolumeService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexVolumeServiceImpl.class);

    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final InternalStatisticsReceiver statisticsReceiver;

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao,
                           final SecurityContext securityContext,
                           final NodeInfo nodeInfo,
                           final InternalStatisticsReceiver statisticsReceiver) {
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.statisticsReceiver = statisticsReceiver;
    }

    @Override
    public IndexVolume create(CreateVolumeRequest createVolumeRequest) {
        final IndexVolume indexVolume = new IndexVolume();
        AuditUtil.stamp(securityContext.getUserId(), indexVolume);

        var names = indexVolumeDao.getAll().stream().map(i -> isNullOrEmpty(i.getNodeName()) ? "" : i.getNodeName())
                .collect(Collectors.toList());
        indexVolume.setNodeName(isNullOrEmpty(createVolumeRequest.getNodeName())
                ? NextNameGenerator.getNextName(names, "New index volume")
                : createVolumeRequest.getNodeName());
        indexVolume.setPath(isNullOrEmpty(createVolumeRequest.getPath()) ? null : createVolumeRequest.getPath());
        indexVolume.setIndexVolumeGroupName(createVolumeRequest.getIndexVolumeGroupName());

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
    public IndexVolume update(IndexVolume updateVolumeDTO) {
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
    public Boolean delete(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.delete(id));
    }

    @Override
    public void rescan() {
        final List<IndexVolume> volumes = getAll();
        final String nodeName = nodeInfo.getThisNodeName();
        for (final IndexVolume volume : volumes) {
            if (nodeName.equals(volume.getNodeName())) {
                updateVolumeState(volume);

                // Record some statistics for the use of this volume.
                recordStats(volume);
            }
        }
    }

    private IndexVolume updateVolumeState(final IndexVolume volume) {
        final Path path = Paths.get(volume.getPath());

        // Ensure the path exists
        if (Files.isDirectory(path)) {
            LOGGER.debug(LambdaLogUtil.message("updateVolumeState() path exists: {}", path));
            setSizes(path, volume);
        } else {
            try {
                Files.createDirectories(path);
                LOGGER.debug(LambdaLogUtil.message("updateVolumeState() path created: {}", path));
                setSizes(path, volume);
            } catch (final IOException e) {
                LOGGER.error(LambdaLogUtil.message("updateVolumeState() path not created: {}", path));
            }
        }

        LOGGER.debug(LambdaLogUtil.message("updateVolumeState() exit {}", volume));
        return volume;
    }

    private void setSizes(final Path path, final IndexVolume volumeState) {
        try {
            final FileStore fileStore = Files.getFileStore(path);
            final long usableSpace = fileStore.getUsableSpace();
            final long freeSpace = fileStore.getUnallocatedSpace();
            final long totalSpace = fileStore.getTotalSpace();

            volumeState.setUpdateTimeMs(System.currentTimeMillis());
            volumeState.setBytesTotal(totalSpace);
            volumeState.setBytesFree(usableSpace);
            volumeState.setBytesUsed(totalSpace - freeSpace);

            indexVolumeDao.updateVolumeState(
                    volumeState.getId(),
                    volumeState.getUpdateTimeMs(),
                    volumeState.getBytesUsed(),
                    volumeState.getBytesFree(),
                    volumeState.getBytesTotal());
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void recordStats(final IndexVolume volume) {
        if (statisticsReceiver != null) {
            try {
                final long now = System.currentTimeMillis();
                final List<InternalStatisticEvent> events = new ArrayList<>();
                addStatisticEvent(events, now, volume, "Limit", volume.getBytesLimit());
                addStatisticEvent(events, now, volume, "Used", volume.getBytesUsed());
                addStatisticEvent(events, now, volume, "Free", volume.getBytesFree());
                addStatisticEvent(events, now, volume, "Total", volume.getBytesTotal());
                statisticsReceiver.putEvents(events);
            } catch (final RuntimeException e) {
                LOGGER.warn(e::getMessage);
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private void addStatisticEvent(final List<InternalStatisticEvent> events,
                                   final long timeMs,
                                   final IndexVolume volume,
                                   final String type,
                                   final Long bytes) {
        if (bytes != null) {
            Map<String, String> tags = ImmutableMap.<String, String>builder()
                    .put("Id", String.valueOf(volume.getId()))
                    .put("Path", volume.getPath())
                    .put("Type", type)
                    .build();

            InternalStatisticEvent event = InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.VOLUMES, timeMs, tags, bytes.doubleValue());
            events.add(event);
        }
    }
}
