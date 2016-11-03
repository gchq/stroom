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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.entity.shared.BaseResultList;
import stroom.node.server.MockStroomPropertyService;
import stroom.node.server.NodeCache;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.node.shared.VolumeState;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestVolumeServiceImpl extends StroomUnitTest {
    private final Rack rack1 = Rack.create("rack1");
    private final Rack rack2 = Rack.create("rack2");

    private final Node node1a = Node.create(rack1, "1a");
    private final Node node1b = Node.create(rack1, "1b");
    private final Node node1c = Node.create(rack1, "1c");

    private final Node node2a = Node.create(rack2, "2a");
    private final Node node2b = Node.create(rack2, "2b");

    private final Volume public1a = Volume.create(node1a, FileUtil.getTempDir().getAbsolutePath() + "/PUBLIC_1A", VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final Volume public1b = Volume.create(node1b, FileUtil.getTempDir().getAbsolutePath() + "/PUBLIC_1B", VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final Volume public2a = Volume.create(node2a, FileUtil.getTempDir().getAbsolutePath() + "/PUBLIC_2A", VolumeType.PUBLIC,
            VolumeState.create(0, 1000));
    private final Volume public2b = Volume.create(node2b, FileUtil.getTempDir().getAbsolutePath() + "/PUBLIC_2B", VolumeType.PUBLIC,
            VolumeState.create(0, 1000));

    private List<Volume> volumeList = null;
    private VolumeServiceImpl volumeServiceImpl = null;

    @Before
    public void init() {
        volumeList = new ArrayList<>();
        volumeList.add(public1a);
        volumeList.add(public1b);
        volumeList.add(public2a);
        volumeList.add(public2b);

        final MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();
        mockStroomPropertyService.setProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, "2");

        volumeServiceImpl = new VolumeServiceImpl(null, new NodeCache(node1a), mockStroomPropertyService, null, null) {
            @Override
            public BaseResultList<Volume> find(final FindVolumeCriteria criteria) {
                return BaseResultList.createUnboundedList(volumeList);
            }

            @Override
            VolumeState saveVolumeState(final VolumeState volumeState) {
                return volumeState;
            }
        };
    }

    @Test
    public void testNode1aNodeWithCacheAndSomePuckerLocalStorage() {
        final Set<Volume> call1 = volumeServiceImpl.getStreamVolumeSet(node1a);
        final Set<Volume> call2 = volumeServiceImpl.getStreamVolumeSet(node1a);
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
        final Set<Volume> call1 = volumeServiceImpl.getStreamVolumeSet(node1c);
        final Set<Volume> call2 = volumeServiceImpl.getStreamVolumeSet(node1c);
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
        final Set<Volume> call1 = volumeServiceImpl.getStreamVolumeSet(node2a);
        final Set<Volume> call2 = volumeServiceImpl.getStreamVolumeSet(node2a);
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

}
