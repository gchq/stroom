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

package stroom.meta.impl;

import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.api.MetaSecurityFilter;
import stroom.meta.shared.MetaFields;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.query.api.ExpressionOperator;
import stroom.resource.impl.ResourceModule;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.impl.DocumentPermissionServiceImpl;
import stroom.security.impl.SecurityContextModule;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.User;
import stroom.test.BootstrapTestModule;
import stroom.test.StroomIntegrationTest;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(BootstrapTestModule.class)
@IncludeModule(UriFactoryModule.class)
@IncludeModule(CoreModule.class)
@IncludeModule(ResourceModule.class)
@IncludeModule(stroom.cluster.impl.MockClusterModule.class)
@IncludeModule(VolumeTestConfigModule.class)
@IncludeModule(SecurityContextModule.class)
@IncludeModule(MockMetaStatisticsModule.class)
@IncludeModule(stroom.test.DatabaseTestControlModule.class)
@IncludeModule(JerseyModule.class)
class TestMetaSecurityFilter extends StroomIntegrationTest {

    private static final String TEST_USER = "test_user";
    private static final String FEED_NO_PERMISSION = "FEED_NO_PERMISSION";
    private static final String FEED_USE_PERMISSION = "FEED_USE_PERMISSION";
    private static final String FEED_READ_PERMISSION = "FEED_READ_PERMISSION";

    private static final List<String> FEED_FIELDS = List.of(MetaFields.FIELD_FEED);

    @Inject
    private UserService userService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private MetaSecurityFilter metaSecurityFilter;
    @Inject
    private DocumentPermissionServiceImpl documentPermissionService;
    @Inject
    private SecurityContext securityContext;

    @Test
    void testSecurityFilter() {
        securityContext.asProcessingUser(() -> {
            final User user = userService.getOrCreateUser(TEST_USER);

            final DocRef docref1 = feedStore.createDocument(FEED_NO_PERMISSION);
            final DocRef docref2 = feedStore.createDocument(FEED_USE_PERMISSION);
            final DocRef docref3 = feedStore.createDocument(FEED_READ_PERMISSION);

            documentPermissionService.setPermission(docref2, user.asRef(), DocumentPermission.USE);
            documentPermissionService.setPermission(docref3, user.asRef(), DocumentPermission.VIEW);

            securityContext.asUser(user.asRef(), () -> {
                final Optional<ExpressionOperator> useExpression = metaSecurityFilter.getExpression(
                        DocumentPermission.USE,
                        FEED_FIELDS);
                final Optional<ExpressionOperator> readExpression = metaSecurityFilter.getExpression(
                        DocumentPermission.VIEW,
                        FEED_FIELDS);

                assertThat(useExpression).isNotEmpty();
                assertThat(useExpression.get().getChildren().size() == 1);
                assertThat(readExpression).isNotEmpty();
                assertThat(readExpression.get().getChildren().size() == 1);
            });
        });
    }
}
