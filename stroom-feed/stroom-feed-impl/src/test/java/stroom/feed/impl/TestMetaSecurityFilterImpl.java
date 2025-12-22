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

package stroom.feed.impl;

import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestMetaSecurityFilterImpl {

    private static final DocumentPermission PERMISSION = DocumentPermission.VIEW;
    private static final String FIELD_1 = "Field1";
    private static final String FIELD_2 = "Field2";
    private static final String FEED_1 = "FEED1";
    private static final String FEED_2 = "FEED2";
    private static final String FEED_3 = "FEED3";
    private static final String FEED_4 = "FEED4";
    private static final String FEED_5 = "FEED5";
    private static final List<String> FEEDS = List.of(
            FEED_1,
            FEED_2,
            FEED_3,
            FEED_4,
            FEED_5);
    protected static final String UUID_SUFFIX = "_UUID";

    @Spy
    private SecurityContext securityContextSpy = new MockSecurityContext();

    @Mock
    private FeedStore mockFeedStore;

    @InjectMocks
    private MetaSecurityFilterImpl metaSecurityFilter;

    @BeforeEach
    void setUp() {
    }

    private void setupFeedStoreMock() {
        Mockito.when(mockFeedStore.list())
                .thenReturn(FEEDS.stream()
                        .map(feed -> FeedDoc.buildDocRef()
                                .uuid(feed + UUID_SUFFIX)
                                .name(feed)
                                .build())
                        .toList());
    }

    @Test
    void getExpression_admin() {
        Mockito.when(securityContextSpy.isAdmin())
                .thenReturn(true);

        final Optional<ExpressionOperator> optExpr = metaSecurityFilter.getExpression(
                PERMISSION, List.of(FIELD_1));

        // admin so no conditions applied
        assertThat(optExpr)
                .isEmpty();
    }

    @Test
    void getExpression_noPerms() {
        setupFeedStoreMock();
        Mockito.when(securityContextSpy.isAdmin())
                .thenReturn(false);
        Mockito.when(securityContextSpy.hasDocumentPermission(Mockito.any(), Mockito.any()))
                .thenReturn(false);

        final Optional<ExpressionOperator> optExpr = metaSecurityFilter.getExpression(
                PERMISSION, List.of(FIELD_1));

        // no perms so an empty in list
        final ExpressionOperator expected = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field(FIELD_1)
                        .condition(Condition.IN)
                        .value("")
                        .build())
                .build();
        assertThat(optExpr)
                .hasValue(expected);
    }

    @Test
    void getExpression_permsOnTwoFeeds() {
        setupFeedStoreMock();
        Mockito.when(securityContextSpy.isAdmin())
                .thenReturn(false);

        Mockito.when(securityContextSpy.hasDocumentPermission(
                        Mockito.eq(feed(FEED_1)), Mockito.eq(PERMISSION)))
                .thenReturn(true);
        Mockito.when(securityContextSpy.hasDocumentPermission(
                        Mockito.eq(feed(FEED_2)), Mockito.eq(PERMISSION)))
                .thenReturn(false);
        Mockito.when(securityContextSpy.hasDocumentPermission(
                        Mockito.eq(feed(FEED_3)), Mockito.eq(PERMISSION)))
                .thenReturn(true);
        Mockito.when(securityContextSpy.hasDocumentPermission(
                        Mockito.eq(feed(FEED_4)), Mockito.eq(PERMISSION)))
                .thenReturn(false);
        Mockito.when(securityContextSpy.hasDocumentPermission(
                        Mockito.eq(feed(FEED_5)), Mockito.eq(PERMISSION)))
                .thenReturn(false);

        final Optional<ExpressionOperator> optExpr = metaSecurityFilter.getExpression(
                PERMISSION, List.of(FIELD_1));

        // no perms so an empty in list
        final ExpressionOperator expected = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field(FIELD_1)
                        .condition(Condition.IN)
                        .value(FEED_1 + "," + FEED_3)
                        .build())
                .build();
        assertThat(optExpr)
                .hasValue(expected);
    }

    private DocRef feed(final String name) {
        return new DocRef("Feed", name + UUID_SUFFIX);
    }
}
