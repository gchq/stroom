package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeFields;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import com.google.common.collect.ImmutableMap;

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

public class IndexVolumeServiceImpl implements IndexVolumeService, Clearable {
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
    public ResultPage<IndexVolume> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(() -> indexVolumeDao.find(criteria));
    }

    @Override
    public IndexVolume create(IndexVolume indexVolume) {
        AuditUtil.stamp(securityContext.getUserId(), indexVolume);

        var names = indexVolumeDao.getAll().stream().map(i -> isNullOrEmpty(i.getNodeName()) ? "" : i.getNodeName())
                .collect(Collectors.toList());
        indexVolume.setNodeName(isNullOrEmpty(indexVolume.getNodeName())
                ? NextNameGenerator.getNextName(names, "New index volume")
                : indexVolume.getNodeName());
        indexVolume.setPath(isNullOrEmpty(indexVolume.getPath()) ? null : indexVolume.getPath());
        indexVolume.setIndexVolumeGroupId(indexVolume.getIndexVolumeGroupId());

        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.create(indexVolume));
    }

    @Override
    public IndexVolume read(final int id) {
        return securityContext.secureResult(() -> indexVolumeDao.fetch(id).orElse(null));
    }

    @Override
    public IndexVolume update(IndexVolume updateVolumeDTO) {
        final var indexVolume = securityContext.secureResult(() -> indexVolumeDao.fetch(updateVolumeDTO.getId()).orElse(null));

        // Map from DTO to entity
        indexVolume.setIndexVolumeGroupId(updateVolumeDTO.getIndexVolumeGroupId());
        indexVolume.setPath((updateVolumeDTO.getPath()));
        indexVolume.setNodeName(updateVolumeDTO.getNodeName());
        indexVolume.setBytesLimit(updateVolumeDTO.getBytesLimit());
        indexVolume.setState(updateVolumeDTO.getState());


        AuditUtil.stamp(securityContext.getUserId(), indexVolume);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.update(indexVolume));
    }

    @Override
    public Boolean delete(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.delete(id));
    }

    @Override
    public void rescan() {
        final String nodeName = nodeInfo.getThisNodeName();
        final ExpressionOperator expression = ExpressionUtil.equals(IndexVolumeFields.NODE_NAME, nodeName);
        final List<IndexVolume> volumes = find(new ExpressionCriteria(expression)).getValues();
        for (final IndexVolume volume : volumes) {
            updateVolumeState(volume);

            // Record some statistics for the use of this volume.
            recordStats(volume);
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

    @Override
    public void clear() {
        // Delete the contents of all index volumes.
        find(new ExpressionCriteria()).getValues()
                .forEach(volume -> {
                    // The parent will also pick up the index shard (as well as the
                    // store)
                    LOGGER.info("Clearing index volume {}", volume.getPath());
                    FileUtil.deleteContents(Paths.get(volume.getPath()));
                });
    }
}
