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

package stroom.activity.impl.db;


import org.jooq.exception.DataChangedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import stroom.activity.api.ActivityService;
import stroom.activity.impl.ActivityDao;
import stroom.activity.impl.ActivityServiceImpl;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.Prop;
import stroom.activity.shared.ActivityValidationResult;
import stroom.activity.shared.FindActivityCriteria;
import stroom.db.util.HikariConfigHolder;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BaseResultList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestActivityServiceImpl {
    private ActivityService activityService;

    @Mock
    private SecurityContext securityContext;

    @BeforeEach
    void before() {
        Mockito.when(securityContext.getUserId()).thenReturn("testUser");
        Mockito.when(securityContext.isLoggedIn()).thenReturn(true);

        final ConnectionProvider connectionProvider = new ActivityDbModule()
                .getConnectionProvider(ActivityConfig::new, new HikariConfigHolder());

        final ActivityDao activityDao = new ActivityDaoImpl(connectionProvider);
        activityService = new ActivityServiceImpl(securityContext, activityDao);
    }

    @Test
    void test() {
        // Delete all existing.
        BaseResultList<Activity> list = activityService.find(new FindActivityCriteria());
        list.forEach(activity -> activityService.delete(activity.getId()));

        // Create 1
        Activity activity1 = activityService.create();
        activity1.getDetails().add(createProp("foo"), "bar");
        activity1.getDetails().add(createProp("this"), "that");
        activity1 = activityService.update(activity1);

        // Update 1
        activity1.getDetails().add(createProp("foo"), "bar");
        activity1.getDetails().add(createProp("this"), "that");
        Activity updatedActivity1 = activityService.update(activity1);

        final Activity oldActivity = activity1;
        assertThatThrownBy(() -> {
            // Ensure that we aren't allowed to update the old version.
            // Update 1
            activityService.update(oldActivity);
        }).isInstanceOf(DataChangedException.class);

        // Find one
        list = activityService.find(new FindActivityCriteria());
        assertThat(list.size()).isEqualTo(1);

        // Save 2
        Activity activity2 = activityService.create();
        activity2.getDetails().add(createProp("lorem"), "ipsum");
        activity2 = activityService.update(activity2);

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
        activityService.delete(activity1.getId());
        final BaseResultList<Activity> list4 = activityService.find(new FindActivityCriteria());
        assertThat(list4.size()).isEqualTo(1);

        // Delete the other
        activityService.delete(activity2.getId());
        final BaseResultList<Activity> list5 = activityService.find(new FindActivityCriteria());
        assertThat(list5.size()).isEqualTo(0);
    }

    @Test
    void testValidation() {
        // Save 1
        Activity activity1 = new Activity();
        activity1.getDetails().add(createProp("foo", "\\w{3,}"), "bar");
        activity1.getDetails().add(createProp("this", "\\w{4,}"), "that");
        activity1.setUserId("test");
        final ActivityValidationResult activityValidationResult1 = activityService.validate(activity1);
        assertThat(activityValidationResult1.isValid()).isTrue();

        Activity activity2 = new Activity();
        activity2.getDetails().add(createProp("foo", ".{3,}"), "bar");
        activity2.getDetails().add(createProp("this", ".{80,}"), "that");
        activity2.setUserId("test");
        final ActivityValidationResult activityValidationResult2 = activityService.validate(activity2);
        assertThat(activityValidationResult2.isValid()).isFalse();
    }

    private Prop createProp(final String name) {
        return createProp(name, null);
    }

    private Prop createProp(final String name, final String validation) {
        final Prop prop = new Prop();
        prop.setId(name);
        prop.setName(name);
        prop.setValidation(validation);
        return prop;
    }
}
