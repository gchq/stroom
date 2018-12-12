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


import org.junit.jupiter.api.Test;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.Prop;
import stroom.activity.shared.FindActivityCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestActivityServiceImpl extends AbstractCoreIntegrationTest {
    @Inject
    private ActivityService activityService;

    @Test
    void test() {
        // Save 1
        Activity activity1 = new Activity();
        activity1.getDetails().add(createProp("foo"), "bar");
        activity1.getDetails().add(createProp("this"), "that");
        activity1.setUserId("test");
        activity1 = activityService.save(activity1);

        // Find one
        final BaseResultList<Activity> list = activityService.find(new FindActivityCriteria());
        assertThat(list.size()).isEqualTo(1);

        // Save 2
        Activity activity2 = new Activity();
        activity2.getDetails().add(createProp("lorem"), "ipsum");
        activity2.setUserId("test2");
        activity2 = activityService.save(activity2);

        // Find both
        final BaseResultList<Activity> list1 = activityService.find(new FindActivityCriteria());
        assertThat(list1.size()).isEqualTo(2);

        // Find each
        final BaseResultList<Activity> list2 = activityService.find(FindActivityCriteria.create("bar"));
        assertThat(list2.size()).isEqualTo(1);
        assertThat(list2.get(0).getId()).isEqualTo(activity1.getId());

        final BaseResultList<Activity> list3 = activityService.find(FindActivityCriteria.create("ipsum"));
        assertThat(list3.size()).isEqualTo(1);
        assertThat(list3.get(0).getId()).isEqualTo(activity2.getId());

        // Delete one
        activityService.delete(activity1);
        final BaseResultList<Activity> list4 = activityService.find(new FindActivityCriteria());
        assertThat(list4.size()).isEqualTo(1);

        // Delete the other
        activityService.delete(activity2);
        final BaseResultList<Activity> list5 = activityService.find(new FindActivityCriteria());
        assertThat(list5.size()).isEqualTo(0);
    }

    private Prop createProp(final String name) {
        final Prop prop = new Prop();
        prop.setId(name);
        prop.setName(name);
        return prop;
    }
}
