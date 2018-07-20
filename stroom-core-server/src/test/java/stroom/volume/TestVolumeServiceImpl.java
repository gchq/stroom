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

package stroom.volume;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.node.NodeCache;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.node.shared.VolumeState;
import stroom.persist.EntityManagerSupport;
import stroom.properties.api.PropertyService;
import stroom.properties.impl.mock.MockPropertyService;
import stroom.security.Security;
import stroom.security.impl.mock.MockSecurityContext;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestVolumeServiceImpl extends StroomUnitTest {

    private static final Path DEFAULT_VOLUMES_PATH = Paths.get(
            System.getProperty("user.home"),
            StroomProperties.USER_CONF_DIR
    ).resolve(VolumeServiceImpl.DEFAULT_VOLUMES_SUBDIR);

    private static final Path DEFAULT_INDEX_VOLUME_PATH = DEFAULT_VOLUMES_PATH.resolve(VolumeServiceImpl.DEFAULT_INDEX_VOLUME_SUBDIR);
    private static final Path DEFAULT_STREAM_VOLUME_PATH = DEFAULT_VOLUMES_PATH.resolve(VolumeServiceImpl.DEFAULT_STREAM_VOLUME_SUBDIR);

    private final MockPropertyService mockStroomPropertyService = new MockPropertyService();
    private final Rack rack1 = Rack.create("rack1");
    private final Rack rack2 = Rack.create("rack2");
    private final Node node1a = Node.create(rack1, "1a");
    private final Node node1b = Node.create(rack1, "1b");
    private final Node node1c = Node.create(rack1, "1c");
    private final Node node2a = Node.create(rack2, "2a");
    private final Node node2b = Node.create(rack2, "2b");
    private final VolumeEntity public1a = VolumeEntity.create(node1a, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_1A")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final VolumeEntity public1b = VolumeEntity.create(node1b, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_1B")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final VolumeEntity public2a = VolumeEntity.create(node2a, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_2A")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final VolumeEntity public2b = VolumeEntity.create(node2b, FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_2B")), VolumeType.PUBLIC,
            VolumeState.create(0, 1000));

    private MockVolumeService volumeServiceImpl = null;
    @Mock
    private StroomEntityManager stroomEntityManager;
    @Mock
    private EntityManagerSupport entityManagerSupport;

    private final Security security = new Security(new MockSecurityContext());

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        deleteDefaultVolumesDir();

        final List<VolumeEntity> volumeList = new ArrayList<>();
        volumeList.add(public1a);
        volumeList.add(public1b);
        volumeList.add(public2a);
        volumeList.add(public2b);

        mockStroomPropertyService.setProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, "2");

        volumeServiceImpl = new MockVolumeService(stroomEntityManager, security, entityManagerSupport, new NodeCache(node1a), mockStroomPropertyService, null);
        volumeServiceImpl.volumeList = volumeList;
    }

    @Test
    public void testNode1aNodeWithCacheAndSomePuckerLocalStorage() {
        final Set<VolumeEntity> call1 = volumeServiceImpl.getStreamVolumeSet(node1a);
        final Set<VolumeEntity> call2 = volumeServiceImpl.getStreamVolumeSet(node1a);
        Assert.assertEquals(2, call1.size());
        Assert.assertEquals(2, call2.size());

        // Check that we only write once in a rack
        Assert.assertTrue(call1.contains(public1a) ^ call1.contains(public1b));
        Assert.assertTrue(call1.contains(public2a) ^ call1.contains(public2b));
        Assert.assertTrue(call2.contains(public1a) ^ call2.contains(public1b));
        Assert.assertTrue(call2.contains(public2a) ^ call2.contains(public2b));

        // Check that we round robin OK
        Assert.assertTrue(call1.contains(public2a) ^ call2.contains(public2a));
        Assert.assertTrue(call1.contains(public2b) ^ call2.contains(public2b));

    }

    @Test
    public void testNode1cNodeWithNoStorage() {
        final Set<VolumeEntity> call1 = volumeServiceImpl.getStreamVolumeSet(node1c);
        final Set<VolumeEntity> call2 = volumeServiceImpl.getStreamVolumeSet(node1c);
        Assert.assertEquals(2, call1.size());
        Assert.assertEquals(2, call2.size());

        // Check that we only write once in a rack
        Assert.assertTrue(call1.contains(public1a) ^ call1.contains(public1b));
        Assert.assertTrue(call1.contains(public2a) ^ call1.contains(public2b));
        Assert.assertTrue(call2.contains(public1a) ^ call2.contains(public1b));
        Assert.assertTrue(call2.contains(public2a) ^ call2.contains(public2b));

        // Check that we round robin OK
        Assert.assertTrue(call1.contains(public1a) ^ call2.contains(public1a));
        Assert.assertTrue(call1.contains(public1b) ^ call2.contains(public1b));
        Assert.assertTrue(call1.contains(public2a) ^ call2.contains(public2a));
        Assert.assertTrue(call1.contains(public2b) ^ call2.contains(public2b));
    }

    @Test
    public void testNode2aNodeWithNoCache() {
        final Set<VolumeEntity> call1 = volumeServiceImpl.getStreamVolumeSet(node2a);
        final Set<VolumeEntity> call2 = volumeServiceImpl.getStreamVolumeSet(node2a);
        Assert.assertEquals(2, call1.size());
        Assert.assertEquals(2, call2.size());

        // Check that we only write once in a rack
        Assert.assertTrue(call1.contains(public1a) ^ call1.contains(public1b));
        Assert.assertTrue(call1.contains(public2a) ^ call1.contains(public2b));
        Assert.assertTrue(call2.contains(public1a) ^ call2.contains(public1b));
        Assert.assertTrue(call2.contains(public2a) ^ call2.contains(public2b));

        // Check that we round robin OK on rack 1
        Assert.assertTrue(call1.contains(public1a) ^ call2.contains(public1a));
        Assert.assertTrue(call1.contains(public1b) ^ call2.contains(public1b));
    }

    @Test
    public void testStartup_Disabled() {
        mockStroomPropertyService.setProperty(VolumeServiceImpl.PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP, "false");

//        volumeServiceImpl.startup();

        Assert.assertFalse(volumeServiceImpl.saveCalled);
        Assert.assertFalse(Files.exists(DEFAULT_INDEX_VOLUME_PATH));
        Assert.assertFalse(Files.exists(DEFAULT_STREAM_VOLUME_PATH));
    }

    @Test
    public void testStartup_EnabledExistingVolumes() {
        mockStroomPropertyService.setProperty(VolumeServiceImpl.PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP, "true");

//        volumeServiceImpl.startup();

        Assert.assertFalse(volumeServiceImpl.saveCalled);
        Assert.assertFalse(Files.exists(DEFAULT_INDEX_VOLUME_PATH));
        Assert.assertFalse(Files.exists(DEFAULT_STREAM_VOLUME_PATH));
    }

    @Test
    public void testStartup_EnabledNoExistingVolumes() {
        mockStroomPropertyService.setProperty(VolumeServiceImpl.PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP, "true");
        volumeServiceImpl.volumeList.clear();
        volumeServiceImpl.getStreamVolumeSet(node1a);
//        volumeServiceImpl.startup();

        Assert.assertTrue(volumeServiceImpl.saveCalled);
        //make sure both paths have been saved
        Assert.assertEquals(2, volumeServiceImpl.savedVolumes.stream()
                .map(VolumeEntity::getPath)
                .filter(path -> path.equals(FileUtil.getCanonicalPath(DEFAULT_INDEX_VOLUME_PATH)) ||
                        path.equals(FileUtil.getCanonicalPath(DEFAULT_STREAM_VOLUME_PATH)))
                .count());
        Assert.assertTrue(Files.exists(DEFAULT_INDEX_VOLUME_PATH));
        Assert.assertTrue(Files.exists(DEFAULT_STREAM_VOLUME_PATH));
    }

    private void deleteDefaultVolumesDir() {
        FileUtil.deleteDir(DEFAULT_INDEX_VOLUME_PATH);
        FileUtil.deleteDir(DEFAULT_STREAM_VOLUME_PATH);
        FileUtil.deleteDir(DEFAULT_VOLUMES_PATH);
    }

    private static class MockVolumeService extends VolumeServiceImpl {
        private List<VolumeEntity> volumeList = null;
        private boolean saveCalled;
        private List<VolumeEntity> savedVolumes = new ArrayList<>();

        MockVolumeService(final StroomEntityManager stroomEntityManager,
                          final Security security,
                          final EntityManagerSupport entityManagerSupport,
                          final NodeCache nodeCache,
                          final PropertyService stroomPropertyService,
                          final Optional<InternalStatisticsReceiver> optionalInternalStatisticsReceiver) {
            super(stroomEntityManager,
                    security,
                    entityManagerSupport,
                    nodeCache,
                    stroomPropertyService,
                    optionalInternalStatisticsReceiver);
        }

        @Override
        public BaseResultList<VolumeEntity> find(final FindVolumeCriteria criteria) {
            return BaseResultList.createUnboundedList(volumeList);
        }

        @Override
        VolumeState saveVolumeState(final VolumeState volumeState) {
            return volumeState;
        }

        @Override
        public VolumeEntity save(VolumeEntity entity) {
            super.save(entity);
            saveCalled = true;
            savedVolumes.add(entity);
            return entity;
        }
    }
}
