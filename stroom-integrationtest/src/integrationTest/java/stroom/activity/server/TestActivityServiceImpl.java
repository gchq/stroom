/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.activity.shared.Activity;
import stroom.activity.shared.FindActivityCriteria;
import stroom.entity.shared.BaseResultList;

import javax.annotation.Resource;

public class TestActivityServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private ActivityService activityService;

    @Test
    public void test() {
        // Save 1
        Activity activity1 = new Activity();
        activity1.getDetails().addProperty("foo", "bar");
        activity1.getDetails().addProperty("this", "that");
        activity1.setUserId("test");
        activity1 = activityService.save(activity1);

        // Find one
        final BaseResultList<Activity> list = activityService.find(new FindActivityCriteria());
        Assert.assertEquals(1, list.size());

        // Save 2
        Activity activity2 = new Activity();
        activity2.getDetails().addProperty("lorem", "ipsum");
        activity2.setUserId("test2");
        activity2 = activityService.save(activity2);

        // Find both
        final BaseResultList<Activity> list1 = activityService.find(new FindActivityCriteria());
        Assert.assertEquals(2, list1.size());

        // Find each
        final BaseResultList<Activity> list2 = activityService.find(FindActivityCriteria.create("bar"));
        Assert.assertEquals(1, list2.size());
        Assert.assertEquals(activity1.getId(), list2.get(0).getId());

        final BaseResultList<Activity> list3 = activityService.find(FindActivityCriteria.create("ipsum"));
        Assert.assertEquals(1, list3.size());
        Assert.assertEquals(activity2.getId(), list3.get(0).getId());

        // Delete one
        activityService.delete(activity1);
        final BaseResultList<Activity> list4 = activityService.find(new FindActivityCriteria());
        Assert.assertEquals(1, list4.size());

        // Delete the other
        activityService.delete(activity2);
        final BaseResultList<Activity> list5 = activityService.find(new FindActivityCriteria());
        Assert.assertEquals(0, list5.size());
    }
}
