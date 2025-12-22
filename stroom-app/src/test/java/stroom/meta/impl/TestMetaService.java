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
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaSecurityFilter;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
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
//@IncludeModule(DbTestModule.class)
//@IncludeModule(AppConfigTestModule.class)
@IncludeModule(UriFactoryModule.class)
//@IncludeModule(DbConnectionsModule.class)
@IncludeModule(CoreModule.class)
@IncludeModule(BootstrapTestModule.class)
@IncludeModule(ResourceModule.class)
@IncludeModule(stroom.cluster.impl.MockClusterModule.class)
@IncludeModule(VolumeTestConfigModule.class)
@IncludeModule(SecurityContextModule.class)
@IncludeModule(MockMetaStatisticsModule.class)
@IncludeModule(stroom.test.DatabaseTestControlModule.class)
@IncludeModule(JerseyModule.class)
//@IncludeModule(GlobalConfigBootstrapModule.class)
//@IncludeModule(GlobalConfigDaoModule.class)
//@IncludeModule(DirProvidersModule.class)
class TestMetaService extends StroomIntegrationTest {

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
    private MetaService metaService;
    @Inject
    private DocumentPermissionServiceImpl documentPermissionService;
    @Inject
    private SecurityContext securityContext;

    @Test
    void testFindWithMetaSecurityFilter() {
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

                createMeta(FEED_NO_PERMISSION);
                createMeta(FEED_USE_PERMISSION);
                createMeta(FEED_READ_PERMISSION);

                final List<Meta> readList = metaService.find(new FindMetaCriteria()).getValues();
                assertThat(readList.size()).isEqualTo(1);

                securityContext.useAsRead(() -> {
                    final Optional<ExpressionOperator> useExpression2 = metaSecurityFilter.getExpression(
                            DocumentPermission.USE,
                            FEED_FIELDS);
                    final Optional<ExpressionOperator> readExpression2 = metaSecurityFilter.getExpression(
                            DocumentPermission.VIEW,
                            FEED_FIELDS);

                    assertThat(useExpression2).isNotEmpty();
                    assertThat(useExpression2.get().getChildren().size() == 2);
                    assertThat(readExpression2).isNotEmpty();
                    assertThat(readExpression2.get().getChildren().size() == 1);

                    final List<Meta> useAndReadList = metaService.find(new FindMetaCriteria()).getValues();
                    assertThat(useAndReadList.size()).isEqualTo(2);
                });
            });
        });
    }

    @Test
    void testFindWithMetaSecurityFilter_noPerms() {
        securityContext.asProcessingUser(() -> {
            final User user = userService.getOrCreateUser(TEST_USER);

            final DocRef docref1 = feedStore.createDocument(FEED_NO_PERMISSION);

//            documentPermissionService.addPermission(docref2.getUuid(), user.getUuid(), DocumentPermissionEnum.USE);
//            documentPermissionService.addPermission(docref3.getUuid(), user.getUuid(), DocumentPermissionEnum.READ);

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

                createMeta(FEED_NO_PERMISSION);
                createMeta(FEED_USE_PERMISSION);
                createMeta(FEED_READ_PERMISSION);

                final List<Meta> readList = metaService.find(new FindMetaCriteria()).getValues();
                assertThat(readList.size())
                        .isEqualTo(0);
            });
        });
    }

    @Test
    void testGetSelectionSummaryWithMetaSecurityFilter() {
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

                final Meta noPermissionMeta = createMeta(FEED_NO_PERMISSION);
                final Meta usePermissionMeta = createMeta(FEED_USE_PERMISSION);
                final Meta readPermissionMeta = createMeta(FEED_READ_PERMISSION);

                final SelectionSummary selectionSummary = metaService.getSelectionSummary(new FindMetaCriteria());
                assertThat(selectionSummary.getItemCount()).isEqualTo(1);

                securityContext.useAsRead(() -> {
                    final Optional<ExpressionOperator> useExpression2 = metaSecurityFilter.getExpression(
                            DocumentPermission.USE,
                            FEED_FIELDS);
                    final Optional<ExpressionOperator> readExpression2 = metaSecurityFilter.getExpression(
                            DocumentPermission.VIEW,
                            FEED_FIELDS);

                    assertThat(useExpression2).isNotEmpty();
                    assertThat(useExpression2.get().getChildren().size() == 2);
                    assertThat(readExpression2).isNotEmpty();
                    assertThat(readExpression2.get().getChildren().size() == 1);

                    final SelectionSummary selectionSummary2 = metaService.getSelectionSummary(new FindMetaCriteria());
                    assertThat(selectionSummary2.getItemCount()).isEqualTo(2);

                    assertThat(metaService.getMeta(noPermissionMeta.getId(), true)).isNull();
                    assertThat(metaService.getMeta(usePermissionMeta.getId(), true)).isNotNull();
                    assertThat(metaService.getMeta(readPermissionMeta.getId(), true)).isNotNull();
                });
            });
        });
    }

    @Test
    void testFindReprocessWithMetaSecurityFilter() {
        securityContext.asProcessingUser(() -> {
            final User user = userService.getOrCreateUser(TEST_USER);

            final DocRef feedNoPermission = feedStore.createDocument(FEED_NO_PERMISSION);
            final DocRef feedReadPermission = feedStore.createDocument(FEED_READ_PERMISSION);
            documentPermissionService.setPermission(feedReadPermission, user.asRef(), DocumentPermission.VIEW);

            securityContext.asUser(user.asRef(), () -> {
                final Optional<ExpressionOperator> readExpression = metaSecurityFilter.getExpression(
                        DocumentPermission.VIEW,
                        FEED_FIELDS);

                assertThat(readExpression).isNotEmpty();
                assertThat(readExpression.get().getChildren().size() == 1);

                final Meta noPermissionParent = createMeta(FEED_NO_PERMISSION);
                final Meta readPermissionParent = createMeta(FEED_READ_PERMISSION);

                List<Meta> readList = metaService.findReprocess(new FindMetaCriteria()).getValues();
                assertThat(readList.size()).isEqualTo(0);

                final Meta noPermissionChild1 = createMeta(noPermissionParent, FEED_NO_PERMISSION, "Cooked Events");
                readList = metaService.findReprocess(new FindMetaCriteria()).getValues();
                assertThat(readList.size()).isEqualTo(0);

                final Meta noPermissionChild2 = createMeta(noPermissionParent, FEED_READ_PERMISSION, "Cooked Events");
                readList = metaService.findReprocess(new FindMetaCriteria()).getValues();
                assertThat(readList.size()).isEqualTo(0);

                final Meta readPermissionChild1 = createMeta(readPermissionParent, FEED_NO_PERMISSION, "Cooked Events");
                readList = metaService.findReprocess(new FindMetaCriteria()).getValues();
                assertThat(readList.size()).isEqualTo(0);

                final Meta readPermissionChild2 = createMeta(readPermissionParent,
                        FEED_READ_PERMISSION,
                        "Cooked Events");
                readList = metaService.findReprocess(new FindMetaCriteria()).getValues();
                assertThat(readList.size()).isEqualTo(1);
            });
        });
    }

    @Test
    void testGetReprocessSelectionSummaryWithMetaSecurityFilter() {
        securityContext.asProcessingUser(() -> {
            final User user = userService.getOrCreateUser(TEST_USER);

            final DocRef feedNoPermission = feedStore.createDocument(FEED_NO_PERMISSION);
            final DocRef feedReadPermission = feedStore.createDocument(FEED_READ_PERMISSION);
            documentPermissionService.setPermission(feedReadPermission,
                    user.asRef(),
                    DocumentPermission.VIEW);

            securityContext.asUser(user.asRef(), () -> {
                final Optional<ExpressionOperator> readExpression = metaSecurityFilter.getExpression(
                        DocumentPermission.VIEW,
                        FEED_FIELDS);

                assertThat(readExpression).isNotEmpty();
                assertThat(readExpression.get().getChildren().size() == 1);

                final Meta noPermissionParent = createMeta(FEED_NO_PERMISSION);
                final Meta readPermissionParent = createMeta(FEED_READ_PERMISSION);

                SelectionSummary selectionSummary = metaService.getReprocessSelectionSummary(new FindMetaCriteria());
                assertThat(selectionSummary.getItemCount()).isEqualTo(0);

                final Meta noPermissionChild1 = createMeta(noPermissionParent, FEED_NO_PERMISSION, "Cooked Events");
                selectionSummary = metaService.getReprocessSelectionSummary(new FindMetaCriteria());
                assertThat(selectionSummary.getItemCount()).isEqualTo(0);

                final Meta noPermissionChild2 = createMeta(noPermissionParent, FEED_READ_PERMISSION, "Cooked Events");
                selectionSummary = metaService.getReprocessSelectionSummary(new FindMetaCriteria());
                assertThat(selectionSummary.getItemCount()).isEqualTo(0);

                final Meta readPermissionChild1 = createMeta(readPermissionParent, FEED_NO_PERMISSION, "Cooked Events");
                selectionSummary = metaService.getReprocessSelectionSummary(new FindMetaCriteria());
                assertThat(selectionSummary.getItemCount()).isEqualTo(0);

                final Meta readPermissionChild2 = createMeta(readPermissionParent,
                        FEED_READ_PERMISSION,
                        "Cooked Events");
                selectionSummary = metaService.getReprocessSelectionSummary(new FindMetaCriteria());
                assertThat(selectionSummary.getItemCount()).isEqualTo(1);
            });
        });
    }

    private Meta createMeta(final String feedName) {
        return createMeta(null, feedName, StreamTypeNames.RAW_EVENTS);
    }

    private Meta createMeta(final Meta parent, final String feedName, final String typeName) {
        return securityContext.asProcessingUserResult(() -> {
            final Meta meta = metaService.create(createProps(parent, feedName, typeName));
            metaService.updateStatus(meta, Status.LOCKED, Status.UNLOCKED);
            return meta;
        });
    }

    private MetaProperties createProps(final Meta parent, final String feedName, final String typeName) {
        final long now = System.currentTimeMillis();
        return MetaProperties.builder()
                .parent(parent)
                .feedName(feedName)
                .typeName(typeName)
                .createMs(now)
                .build();
    }
}
