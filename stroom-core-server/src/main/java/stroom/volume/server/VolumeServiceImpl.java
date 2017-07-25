/*
 * Copyright 2016 Crown Copyright
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

package stroom.volume.server;

import com.google.common.collect.ImmutableMap;
import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.Sort.Direction;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.NodeCache;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.node.shared.Volume.VolumeUseStatus;
import stroom.node.shared.VolumeService;
import stroom.node.shared.VolumeState;
import stroom.security.Insecure;
import stroom.security.Secured;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsFacade;
import stroom.statistics.internal.InternalStatisticsFacadeFactory;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Implementation for the volume API.
 */
@Transactional
@Component("volumeService")
@Secured(Volume.MANAGE_VOLUMES_PERMISSION)
@EntityEventHandler(type = Volume.ENTITY_TYPE, action = {EntityAction.CREATE, EntityAction.DELETE})
public class VolumeServiceImpl extends SystemEntityServiceImpl<Volume, FindVolumeCriteria>
        implements VolumeService, EntityEvent.Handler, Clearable {
    /**
     * How many permanent copies should we keep?
     */
    public static final String PROP_RESILIENT_REPLICATION_COUNT = "stroom.streamstore.resilientReplicationCount";
    /**
     * Whether a default volume should be created on application start, but only if other volumes don't already exist
     */
    static final String PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP = "stroom.volumes.createDefaultOnStart";
    static final Path DEFAULT_VOLUMES_SUBDIR = Paths.get("volumes");
    static final Path DEFAULT_INDEX_VOLUME_SUBDIR = Paths.get("defaultIndexVolume");
    static final Path DEFAULT_STREAM_VOLUME_SUBDIR = Paths.get("defaultStreamVolume");
    /**
     * Should we try to write to local volumes if possible?
     */
    private static final String PROP_PREFER_LOCAL_VOLUMES = "stroom.streamstore.preferLocalVolumes";
    /**
     * How should we select volumes to use?
     */
    private static final String PROP_VOLUME_SELECTOR = "stroom.streamstore.volumeSelector";
    private static final String INTERNAL_STAT_KEY_VOLUMES = "volumes";
    private static final Logger LOGGER = LoggerFactory.getLogger(VolumeServiceImpl.class);

    private static final Map<String, VolumeSelector> volumeSelectorMap;
    private static final int DEFAULT_RESILIENT_REPLICATION_COUNT = 1;
    private static final boolean DEFAULT_PREFER_LOCAL_VOLUMES = false;
    private static final VolumeSelector DEFAULT_VOLUME_SELECTOR;

    static {
        volumeSelectorMap = new HashMap<>();
        registerVolumeSelector(new MostFreePercentVolumeSelector());
        registerVolumeSelector(new MostFreeVolumeSelector());
        registerVolumeSelector(new RandomVolumeSelector());
        registerVolumeSelector(new RoundRobinIgnoreLeastFreePercentVolumeSelector());
        registerVolumeSelector(new RoundRobinIgnoreLeastFreeVolumeSelector());
        registerVolumeSelector(new RoundRobinVolumeSelector());
        registerVolumeSelector(new WeightedFreePercentRandomVolumeSelector());
        registerVolumeSelector(new WeightedFreeRandomVolumeSelector());
        DEFAULT_VOLUME_SELECTOR = volumeSelectorMap.get(RoundRobinVolumeSelector.NAME);
    }

    private final StroomEntityManager stroomEntityManager;
    private final NodeCache nodeCache;
    private final StroomPropertyService stroomPropertyService;
    private final StroomBeanStore stroomBeanStore;
    private final InternalStatisticsFacadeFactory internalStatisticsFacadeFactory;
    private final AtomicReference<List<Volume>> currentVolumeState = new AtomicReference<>();

    @Inject
    VolumeServiceImpl(final StroomEntityManager stroomEntityManager, final NodeCache nodeCache,
                      final StroomPropertyService stroomPropertyService, final StroomBeanStore stroomBeanStore,
                      final InternalStatisticsFacadeFactory internalStatisticsFacadeFactory) {
        super(stroomEntityManager);
        this.stroomEntityManager = stroomEntityManager;
        this.nodeCache = nodeCache;
        this.stroomPropertyService = stroomPropertyService;
        this.stroomBeanStore = stroomBeanStore;
        this.internalStatisticsFacadeFactory = internalStatisticsFacadeFactory;
    }

    private static void registerVolumeSelector(final VolumeSelector volumeSelector) {
        volumeSelectorMap.put(volumeSelector.getName(), volumeSelector);
    }

    @Transactional(readOnly = true)
    @Insecure
    @Override
    public Set<Volume> getStreamVolumeSet(final Node node) {
        LocalVolumeUse localVolumeUse = null;
        if (isPreferLocalVolumes()) {
            localVolumeUse = LocalVolumeUse.PREFERRED;
        }

        return getVolumeSet(node, VolumeType.PUBLIC, VolumeUseStatus.ACTIVE, null, localVolumeUse, null,
                getResilientReplicationCount());
    }

    @Transactional(readOnly = true)
    @Insecure
    @Override
    public Set<Volume> getIndexVolumeSet(final Node node, final Set<Volume> allowedVolumes) {
        return getVolumeSet(node, null, null, VolumeUseStatus.ACTIVE, LocalVolumeUse.REQUIRED, allowedVolumes, 1);
    }

    private Set<Volume> getVolumeSet(final Node node, final VolumeType volumeType, final VolumeUseStatus streamStatus,
                                     final VolumeUseStatus indexStatus, final LocalVolumeUse localVolumeUse, final Set<Volume> allowedVolumes,
                                     final int requiredNumber) {
        final VolumeSelector volumeSelector = getVolumeSelector();
        final List<Volume> allVolumeList = getCurrentState();
        final List<Volume> freeVolumes = VolumeListUtil.removeFullVolumes(allVolumeList);
        Set<Volume> set = Collections.emptySet();

        final List<Volume> filteredVolumeList = getFilteredVolumeList(freeVolumes, node, volumeType, streamStatus,
                indexStatus, null, allowedVolumes);
        if (filteredVolumeList.size() > 0) {
            // Create a list of local volumes if we are set to prefer or require
            // local.
            List<Volume> localVolumeList = null;
            if (localVolumeUse != null) {
                localVolumeList = getFilteredVolumeList(freeVolumes, node, volumeType, streamStatus, indexStatus,
                        Boolean.TRUE, allowedVolumes);

                // If we require a local volume and there are none available
                // then return the empty set.
                if (LocalVolumeUse.REQUIRED.equals(localVolumeUse) && localVolumeList.size() == 0) {
                    return set;
                }
            }

            if (requiredNumber <= 1) {
                // With a replication count of 1 any volume will do.
                if (localVolumeList != null && localVolumeList.size() > 0) {
                    set = Collections.singleton(volumeSelector.select(localVolumeList));
                } else if (filteredVolumeList.size() > 0) {
                    set = Collections.singleton(volumeSelector.select(filteredVolumeList));
                }
            } else {
                set = new HashSet<>();

                final List<Volume> remaining = new ArrayList<>(filteredVolumeList);
                List<Volume> remainingInOtherRacks = new ArrayList<>(filteredVolumeList);

                for (int count = 0; count < requiredNumber && remaining.size() > 0; count++) {
                    if (set.size() == 0 && localVolumeList != null && localVolumeList.size() > 0) {
                        // If we are preferring local volumes and this is the
                        // first item then add a local volume here first.
                        final Volume volume = volumeSelector.select(localVolumeList);

                        remaining.remove(volume);
                        remainingInOtherRacks = VolumeListUtil.removeMatchingRack(remainingInOtherRacks,
                                volume.getNode().getRack());

                        set.add(volume);

                    } else if (remainingInOtherRacks.size() > 0) {
                        // Next try and get volumes in other racks.
                        final Volume volume = volumeSelector.select(remainingInOtherRacks);

                        remaining.remove(volume);
                        remainingInOtherRacks = VolumeListUtil.removeMatchingRack(remainingInOtherRacks,
                                volume.getNode().getRack());

                        set.add(volume);

                    } else if (remaining.size() > 0) {
                        // Finally add any other volumes to make up the required
                        // replication count.
                        final Volume volume = volumeSelector.select(remaining);

                        remaining.remove(volume);

                        set.add(volume);
                    }
                }
            }
        }

        if (requiredNumber > set.size()) {
            LOGGER.warn("getVolumeSet - Failed to obtain " + requiredNumber + " volumes as required on node "
                    + nodeCache.getDefaultNode() + " (set=" + set + ")");
        }

        return set;
    }

    private List<Volume> getFilteredVolumeList(final List<Volume> allVolumes, final Node node,
                                               final VolumeType volumeType, final VolumeUseStatus streamStatus, final VolumeUseStatus indexStatus,
                                               final Boolean local, final Set<Volume> allowedVolumes) {
        final List<Volume> list = new ArrayList<>();
        for (final Volume volume : allVolumes) {
            if (allowedVolumes == null || allowedVolumes.contains(volume)) {
                final Node nd = volume.getNode();

                // Check the volume type matches.
                boolean ok = true;
                if (volumeType != null) {
                    ok = volumeType.equals(volume.getVolumeType());
                }

                // Check the stream volume use status matches.
                if (ok) {
                    if (streamStatus != null) {
                        ok = streamStatus.equals(volume.getStreamStatus());
                    }
                }

                // Check the index volume use status matches.
                if (ok) {
                    if (indexStatus != null) {
                        ok = indexStatus.equals(volume.getIndexStatus());
                    }
                }

                // Check the node matches.
                if (ok) {
                    ok = false;
                    if (local == null) {
                        ok = true;
                    } else {
                        if ((Boolean.TRUE.equals(local) && node.equals(nd))
                                || (Boolean.FALSE.equals(local) && !node.equals(nd))) {
                            ok = true;
                        }
                    }
                }

                if (ok) {
                    list.add(volume);
                }
            }
        }
        return list;
    }

    @Override
    public void onChange(final EntityEvent event) {
        currentVolumeState.set(null);
    }

    @Override
    public void clear() {
        currentVolumeState.set(null);
    }

    private List<Volume> getCurrentState() {
        List<Volume> state = currentVolumeState.get();
        if (state == null) {
            synchronized (this) {
                state = currentVolumeState.get();
                if (state == null) {
                    state = refresh();
                    currentVolumeState.set(state);
                }
            }
        }
        return state;
    }

    @StroomFrequencySchedule("5m")
    @JobTrackedSchedule(jobName = "Volume Status", advanced = false, description = "Update the usage status of volumes owned by the node")
    @Override
    public void flush() {
        refresh();
    }

    public List<Volume> refresh() {
        final Node node = nodeCache.getDefaultNode();
        final List<Volume> newState = new ArrayList<>();

        final FindVolumeCriteria findVolumeCriteria = new FindVolumeCriteria();
        findVolumeCriteria.addSort(FindVolumeCriteria.FIELD_ID, Direction.ASCENDING, false);
        final List<Volume> volumeList = find(findVolumeCriteria);
        for (final Volume volume : volumeList) {
            if (volume.getNode().equals(node)) {
                VolumeState volumeState = updateVolumeState(volume);
                volumeState = saveVolumeState(volumeState);
                volume.setVolumeState(volumeState);

                // Record some statistics for the use of this volume.
                recordStats(volume);
            }
            newState.add(volume);
        }

        return newState;
    }

    private void recordStats(final Volume volume) {
        try {
            final VolumeState volumeState = volume.getVolumeState();

            final long now = System.currentTimeMillis();
            InternalStatisticsFacade.BatchBuilder batchBuilder = internalStatisticsFacadeFactory.create().batchBuilder();
            addStatisticEvent(batchBuilder, now, volume, "Limit", volume.getBytesLimit());
            addStatisticEvent(batchBuilder, now, volume, "Used", volumeState.getBytesUsed());
            addStatisticEvent(batchBuilder, now, volume, "Free", volumeState.getBytesFree());
            addStatisticEvent(batchBuilder, now, volume, "Total", volumeState.getBytesTotal());
            batchBuilder.putBatch();
        } catch (final Throwable t) {
            LOGGER.warn(t.getMessage());
            LOGGER.debug(t.getMessage(), t);
        }
    }

    private void addStatisticEvent(final InternalStatisticsFacade.BatchBuilder batchBuilder,
                                   final long timeMs,
                                   final Volume volume,
                                   final String type,
                                   final Long bytes) {
        if (bytes != null) {
            Map<String, String> tags = ImmutableMap.<String, String>builder()
                    .put("Id", String.valueOf(volume.getId()))
                    .put("Node", volume.getNode().getName())
                    .put("Path", volume.getPath())
                    .put("Type", type)
                    .build();

            InternalStatisticEvent event = InternalStatisticEvent.createValueStat(
                    INTERNAL_STAT_KEY_VOLUMES, timeMs, tags, bytes.doubleValue());
            batchBuilder.addEvent(event);
        }
    }

    private VolumeState updateVolumeState(final Volume volume) {
        final VolumeState volumeState = volume.getVolumeState();
        volumeState.setStatusMs(System.currentTimeMillis());
        final File path = new File(volume.getPath());
        // Ensure the path exists
        if (path.mkdirs()) {
            LOGGER.debug("updateVolumeState() path created: " + path);
        } else {
            LOGGER.debug("updateVolumeState() path exists: " + path);
        }

        final long usableSpace = path.getUsableSpace();
        final long freeSpace = path.getFreeSpace();
        final long totalSpace = path.getTotalSpace();

        volumeState.setBytesTotal(totalSpace);
        volumeState.setBytesFree(usableSpace);
        volumeState.setBytesUsed(totalSpace - freeSpace);

        LOGGER.debug("updateVolumeState() exit" + volume);
        return volumeState;
    }

    /**
     * On creating a new volume create the directory Never create afterwards
     */
    @Override
    public Volume save(final Volume entity) throws RuntimeException {
        if (!entity.isPersistent()) {
            VolumeState volumeState = entity.getVolumeState();
            if (volumeState == null) {
                volumeState = new VolumeState();
            }
            // Save initial state
            volumeState = stroomEntityManager.saveEntity(volumeState);
            stroomEntityManager.flush();

            entity.setVolumeState(volumeState);
        }
        return super.save(entity);
    }

    @Override
    public Boolean delete(final Volume entity) throws RuntimeException {
        if (Boolean.TRUE.equals(super.delete(entity))) {
            return stroomEntityManager.deleteEntity(entity.getVolumeState());
        }
        return Boolean.FALSE;
    }

    VolumeState saveVolumeState(final VolumeState volumeState) {
        return stroomEntityManager.saveEntity(volumeState);
    }

    @Override
    public Class<Volume> getEntityClass() {
        return Volume.class;
    }

    @Override
    public FindVolumeCriteria createCriteria() {
        return new FindVolumeCriteria();
    }

    private int getResilientReplicationCount() {
        int resilientReplicationCount = stroomPropertyService.getIntProperty(PROP_RESILIENT_REPLICATION_COUNT,
                DEFAULT_RESILIENT_REPLICATION_COUNT);
        if (resilientReplicationCount < 1) {
            resilientReplicationCount = 1;
        }
        return resilientReplicationCount;
    }

    private boolean isPreferLocalVolumes() {
        return stroomPropertyService.getBooleanProperty(PROP_PREFER_LOCAL_VOLUMES, DEFAULT_PREFER_LOCAL_VOLUMES);
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindVolumeCriteria criteria) {
        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "volumeTypeSet", criteria.getVolumeTypeSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "streamStatusSet", criteria.getStreamStatusSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "indexStatusSet", criteria.getIndexStatusSet());
    }

    private VolumeSelector getVolumeSelector() {
        VolumeSelector volumeSelector = null;

        try {
            final String value = stroomPropertyService.getProperty(PROP_VOLUME_SELECTOR);
            if (value != null) {
                volumeSelector = volumeSelectorMap.get(value);
            }
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage());
        }

        if (volumeSelector == null) {
            volumeSelector = DEFAULT_VOLUME_SELECTOR;
        }

        return volumeSelector;
    }

    @Override
    protected QueryAppender<Volume, FindVolumeCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new VolumeQueryAppender(entityManager);
    }

    @StroomStartup(priority = -1100)
    public void startup() {

        boolean isEnabled = stroomPropertyService.getBooleanProperty(PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP, false);

        if (isEnabled) {
            List<Volume> existingVolumes = getCurrentState();
            if (existingVolumes.size() == 0) {
                Optional<Path> optDefaultVolumePath = getDefaultVolumesPath();

                if (optDefaultVolumePath.isPresent()) {
                    Node node = nodeCache.getDefaultNode();
                    Path indexVolPath = optDefaultVolumePath.get().resolve(DEFAULT_INDEX_VOLUME_SUBDIR);
                    createIndexVolume(indexVolPath, node);
                    Path streamVolPath = optDefaultVolumePath.get().resolve(DEFAULT_STREAM_VOLUME_SUBDIR);
                    createStreamVolume(streamVolPath, node);
                } else {
                    LOGGER.warn("No suitable directory to create default volumes in");
                }
            } else {
                LOGGER.info("Existing volumes exist, won't create default volumes");
            }
        } else {
            LOGGER.info("Creation of default volumes is currently disabled by property: " + PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP);
        }
    }

    private void createIndexVolume(final Path path, final Node node) {
        final Volume vol = new Volume();
        vol.setStreamStatus(VolumeUseStatus.INACTIVE);
        vol.setIndexStatus(VolumeUseStatus.ACTIVE);
        vol.setVolumeType(VolumeType.PRIVATE);
        createVolume(path, node, Optional.of(vol));
    }

    private void createStreamVolume(final Path path, final Node node) {
        final Volume vol = new Volume();
        vol.setStreamStatus(VolumeUseStatus.ACTIVE);
        vol.setIndexStatus(VolumeUseStatus.INACTIVE);
        vol.setVolumeType(VolumeType.PUBLIC);
        createVolume(path, node, Optional.of(vol));
    }

    private void createVolume(final Path path, final Node node, final Optional<Volume> optVolume) {
        String pathStr = path.toAbsolutePath().toString();
        try {
            Files.createDirectories(path);
            LOGGER.info("Creating volume in {} on node {}",
                    pathStr,
                    node.getName());
            final Volume vol = optVolume.orElseGet(Volume::new);
            vol.setPath(pathStr);
            vol.setNode(node);
            //set an arbitrary default limit size of 250MB on each volume to prevent the
            //filesystem from running out of space, assuming they have 500MB free of course.
            getDefaultVolumeLimit(path).ifPresent(vol::setBytesLimit);
            save(vol);
        } catch (IOException e) {
            LOGGER.error("Unable to create volume due to an error creating directory {}", pathStr, e);
        }
    }

    private OptionalLong getDefaultVolumeLimit(final Path path) {
        try {
            long totalBytes = Files.getFileStore(path).getTotalSpace();
            //set an arbitrary limit of 90% of the filesystem total size to ensure we don't fill up the
            //filesystem.  Limit can be configured from within stroom.
            //Should be noted that although if you have multiple volumes on a filesystem the limit will apply
            //to all volumes and any other data on the filesystem. I.e. once the amount of the filesystem in use
            //is greater than the limit writes to those volumes will be prevented. See Volume.isFull() and
            //this.updateVolumeState()
            return OptionalLong.of((long) (totalBytes * 0.9));
        } catch (IOException e) {
            LOGGER.warn("Unable to determine the total space on the filesystem for path: ", path.toAbsolutePath().toString());
            return OptionalLong.empty();
        }
    }

    private Optional<Path> getDefaultVolumesPath() {
        return Stream.<Supplier<Optional<Path>>>of(
                this::getApplicationJarDir,
                this::getUserHomeDir,
                Optional::empty)
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .findFirst()
                .map(Optional::get)
                .flatMap(path -> Optional.of(path.resolve(DEFAULT_VOLUMES_SUBDIR)));
    }

    private Optional<Path> getUserHomeDir() {
        return Optional.ofNullable(System.getProperty("user.home"))
                .flatMap(userHome -> Optional.of(Paths.get(userHome, StroomProperties.USER_CONF_DIR)));
    }

    private Optional<Path> getApplicationJarDir() {
        try {
            String codeSourceLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            if (Pattern.matches(".*/stroom[^/]*.jar$", codeSourceLocation)) {
                return Optional.of(Paths.get(codeSourceLocation).getParent());
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to determine application jar directory due to: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private enum LocalVolumeUse {
        REQUIRED, PREFERRED
    }

    private static class VolumeQueryAppender extends QueryAppender<Volume, FindVolumeCriteria> {
        VolumeQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        public void appendBasicCriteria(HqlBuilder sql, String alias, FindVolumeCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
            sql.appendEntityIdSetQuery(alias + ".node", criteria.getNodeIdSet());
            sql.appendPrimitiveValueSetQuery(alias + ".pindexStatus", criteria.getIndexStatusSet());
            sql.appendPrimitiveValueSetQuery(alias + ".pstreamStatus", criteria.getStreamStatusSet());
            sql.appendPrimitiveValueSetQuery(alias + ".pvolumeType", criteria.getVolumeTypeSet());
        }
    }

}
