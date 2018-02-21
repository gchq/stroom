/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.node;

import org.junit.Assert;
import org.junit.Test;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Volume;
import stroom.test.AbstractCoreIntegrationTest;

import javax.annotation.Resource;
import java.util.List;

public class TestVolumeService extends AbstractCoreIntegrationTest {
    @Resource
    private VolumeService volumeService;

    @Test
    public void testFind() {
        final FindVolumeCriteria criteria = new FindVolumeCriteria();
        final List<Volume> volumeList = volumeService.find(criteria);

        for (final Volume volume : volumeList) {
            volumeService.save(volume);
        }

        Assert.assertEquals(volumeList.size(), volumeService.find(criteria).size());
    }
}
