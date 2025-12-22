/*
 * Copyright 2016-2025 Crown Copyright
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


import stroom.activity.api.ActivityService;
import stroom.activity.impl.ActivityDao;
import stroom.activity.impl.ActivityServiceImpl;
import stroom.activity.impl.db.ActivityConfig.ActivityDbConfig;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.Prop;
import stroom.activity.shared.ActivityValidationResult;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.exception.DataChangedException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestActivityServiceImpl {

    private ActivityService activityService;
    private final SecurityContext securityContext = new MockSecurityContext();

    @BeforeEach
    void before() {
        final ActivityDbConnProvider activityDbConnProvider = DbTestUtil.getTestDbDatasource(
                new ActivityDbModule(), new ActivityDbConfig());
        final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();

        final ActivityDao activityDao = new ActivityDaoImpl(activityDbConnProvider);
        activityService = new ActivityServiceImpl(securityContext, activityDao, expressionPredicateFactory);
    }

    @Test
    void test() {
        // Delete all existing.
        ResultPage<Activity> list = activityService.find(null);
        list.forEach(activity -> activityService.deleteAllByOwner(activity.getId()));

        // Create 1
        Activity activity1 = activityService.create();
        activity1.getDetails().add(createProp("foo"), "bar");
        activity1.getDetails().add(createProp("this"), "that");
        activity1 = activityService.update(activity1);

        // Update 1
        activity1.getDetails().add(createProp("foo"), "bar");
        activity1.getDetails().add(createProp("this"), "that");
        final Activity updatedActivity1 = activityService.update(activity1);

        final Activity oldActivity = activity1;
        assertThatThrownBy(() -> {
            // Ensure that we aren't allowed to update the old version.
            // Update 1
            activityService.update(oldActivity);
        }).isInstanceOf(DataChangedException.class);

        // Find one
        list = activityService.find(null);
        assertThat(list.size()).isEqualTo(1);

        // Save 2
        Activity activity2 = activityService.create();
        activity2.getDetails().add(createProp("lorem"), "ipsum");
        activity2 = activityService.update(activity2);

        // Find both
        final ResultPage<Activity> list1 = activityService.find(null);
        assertThat(list1.size()).isEqualTo(2);

        // Find each
        final ResultPage<Activity> list2 = activityService.find("bar");
        assertThat(list2.size()).isEqualTo(1);
        assertThat(list2.getFirst().getId()).isEqualTo(activity1.getId());

        final ResultPage<Activity> list3 = activityService.find("ipsum");
        assertThat(list3.size()).isEqualTo(1);
        assertThat(list3.getFirst().getId()).isEqualTo(activity2.getId());

        // Delete one
        activityService.deleteAllByOwner(activity1.getId());
        final ResultPage<Activity> list4 = activityService.find(null);
        assertThat(list4.size()).isEqualTo(1);

        // Delete the other
        activityService.deleteAllByOwner(activity2.getId());
        final ResultPage<Activity> list5 = activityService.find(null);
        assertThat(list5.size()).isEqualTo(0);
    }

    @Test
    void testValidation() {
        final UserRef userRef = UserRef.builder().uuid(UUID.randomUUID().toString()).subjectId("test").build();

        // Save 1
        final Activity activity1 = Activity.create();
        activity1.getDetails().add(createProp("foo", "\\w{3,}"), "bar");
        activity1.getDetails().add(createProp("this", "\\w{4,}"), "that");
        activity1.setUserRef(userRef);
        final ActivityValidationResult activityValidationResult1 = activityService.validate(activity1);
        assertThat(activityValidationResult1.isValid()).isTrue();

        final Activity activity2 = Activity.create();
        activity2.getDetails().add(createProp("foo", ".{3,}"), "bar");
        activity2.getDetails().add(createProp("this", ".{80,}"), "that");
        activity2.setUserRef(userRef);
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
