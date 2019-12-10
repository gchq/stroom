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

package stroom.meta.impl;


import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import stroom.app.guice.CoreModule;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.MetaSecurityFilter;
import stroom.meta.shared.MetaService;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.resource.impl.ResourceModule;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserTokenUtil;
import stroom.security.impl.DocumentPermissionServiceImpl;
import stroom.security.impl.SecurityContextModule;
import stroom.security.impl.UserService;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.User;
import stroom.test.AppConfigTestModule;
import stroom.test.IntegrationTestSetupUtil;
import stroom.test.common.util.db.TestDbModule;
import stroom.test.common.util.test.TempDirExtension;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(TestDbModule.class)
@IncludeModule(AppConfigTestModule.class)
@IncludeModule(CoreModule.class)
@IncludeModule(ResourceModule.class)
@IncludeModule(stroom.cluster.impl.MockClusterModule.class)
@IncludeModule(VolumeTestConfigModule.class)
@IncludeModule(SecurityContextModule.class)
@IncludeModule(MockMetaStatisticsModule.class)
@IncludeModule(stroom.test.DatabaseTestControlModule.class)
@ExtendWith(TempDirExtension.class)
class TestMetaService {
    private static final String TEST_USER = "test_user";
    private static final String FEED_NO_PERMISSION = "FEED_NO_PERMISSION";
    private static final String FEED_USE_PERMISSION = "FEED_USE_PERMISSION";
    private static final String FEED_READ_PERMISSION = "FEED_READ_PERMISSION";

    @Inject
    private IntegrationTestSetupUtil integrationTestSetupUtil;
    @Inject
    private UserService userService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private MetaSecurityFilter metaSecurityFilter;
    @Inject
    private MetaService metaService;
    @Inject
    private DocumentPermissionServiceImpl documentPermissionService;
    @Inject
    private SecurityContext securityContext;

    @BeforeAll
    public static void beforeAll() {
        IntegrationTestSetupUtil.reset();
    }

    @BeforeEach
    public void beforeEach() {
        integrationTestSetupUtil.cleanup(() -> false);
    }

    @Test
    void test() {
        securityContext.asProcessingUser(() -> {
            final User user = userService.createUser(TEST_USER);

            final DocRef docref1 = feedStore.createDocument(FEED_NO_PERMISSION);
            final DocRef docref2 = feedStore.createDocument(FEED_USE_PERMISSION);
            final DocRef docref3 = feedStore.createDocument(FEED_READ_PERMISSION);

            documentPermissionService.addPermission(docref2.getUuid(), user.getUuid(), DocumentPermissionNames.USE);
            documentPermissionService.addPermission(docref3.getUuid(), user.getUuid(), DocumentPermissionNames.READ);

            securityContext.asUser(UserTokenUtil.create(user.getName()), () -> {
                final Optional<ExpressionOperator> useExpression = metaSecurityFilter.getExpression(DocumentPermissionNames.USE);
                final Optional<ExpressionOperator> readExpression = metaSecurityFilter.getExpression(DocumentPermissionNames.READ);

                assertThat(useExpression).isNotEmpty();
                assertThat(useExpression.get().getChildren().size() == 1);
                assertThat(readExpression).isNotEmpty();
                assertThat(readExpression.get().getChildren().size() == 1);

                final Meta meta1 = metaService.create(createProps(FEED_NO_PERMISSION));
                final Meta meta2 = metaService.create(createProps(FEED_USE_PERMISSION));
                final Meta meta3 = metaService.create(createProps(FEED_READ_PERMISSION));

                final List<Meta> readList = metaService.find(new FindMetaCriteria());
                assertThat(readList.size()).isEqualTo(1);

                securityContext.useAsRead(() -> {
                    final Optional<ExpressionOperator> useExpression2 = metaSecurityFilter.getExpression(DocumentPermissionNames.USE);
                    final Optional<ExpressionOperator> readExpression2 = metaSecurityFilter.getExpression(DocumentPermissionNames.READ);

                    assertThat(useExpression2).isNotEmpty();
                    assertThat(useExpression2.get().getChildren().size() == 2);
                    assertThat(readExpression2).isNotEmpty();
                    assertThat(readExpression2.get().getChildren().size() == 1);

                    final List<Meta> useAndReadList = metaService.find(new FindMetaCriteria());
                    assertThat(useAndReadList.size()).isEqualTo(2);
                });
            });
        });
    }

    private MetaProperties createProps(final String feedName) {
        final long now = System.currentTimeMillis();
        return new MetaProperties.Builder()
                .feedName(feedName)
                .typeName("Raw Events")
                .createMs(now)
                .build();
    }
}
