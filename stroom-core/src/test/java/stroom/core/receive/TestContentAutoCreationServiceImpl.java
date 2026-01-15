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

package stroom.core.receive;

import stroom.docref.DocRef;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.util.shared.UserDesc;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestContentAutoCreationServiceImpl {

    @InjectMocks
    private ContentAutoCreationServiceImpl contentAutoCreationService;
    @Mock
    private Provider<AutoContentCreationConfig> mockAutoContentCreationConfigProvider;
    @Mock
    private ContentTemplateStore mockContentTemplateStore;
    @Mock
    private FeedStore mockFeedStore;
    @Mock
    private ExpressionMatcherFactory mockExpressionMatcherFactory;
    @Mock
    private ExpressionMatcher mockExpressionMatcher;
    @Mock
    private UserService mockUserService;
    @Mock
    private SecurityContext mockSecurityContext;

    @Test
    void testFeedAlreadyExists() {

        final String feedName = "FEED_X";
        final DocRef docRef = FeedDoc.buildDocRef()
                .randomUuid()
                .name(feedName)
                .build();
        final FeedDoc feedDoc = FeedDoc.builder()
                .uuid(docRef.getUuid())
                .name(feedName)
                .build();
        final UserDesc userDesc = UserDesc.forSubjectId("user1");
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);

        Mockito.when(mockFeedStore.findByName(Mockito.eq(feedName)))
                .thenReturn(List.of(docRef));
        Mockito.when(mockFeedStore.readDocument(Mockito.eq(docRef)))
                .thenReturn(feedDoc);

        final Optional<FeedDoc> optFeedDoc = contentAutoCreationService.tryCreateFeed(
                feedName, userDesc, attributeMap);

        assertThat(optFeedDoc)
                .hasValue(feedDoc);
    }

    @Test
    void testFeedNotExist_NoTemplates() {

        final String feedName = "FEED_X";
        final UserDesc userDesc = UserDesc.forSubjectId("user1");
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        final ContentTemplates contentTemplates = ContentTemplates.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Templates")
                .build();
        Mockito.when(mockFeedStore.findByName(Mockito.eq(feedName)))
                .thenReturn(List.of());
        Mockito.when(mockContentTemplateStore.getOrCreate())
                .thenReturn(contentTemplates);

        final Optional<FeedDoc> optFeedDoc = contentAutoCreationService.tryCreateFeed(
                feedName, userDesc, attributeMap);

        assertThat(optFeedDoc)
                .isEmpty();
    }

    @Test
    void testFeedNotExist_NoActiveTemplates() {
        final String feedName = "FEED_X";
        final UserDesc userDesc = UserDesc.forSubjectId("user1");
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        final ContentTemplates contentTemplates = ContentTemplates.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Templates")
                .contentTemplates(List.of(ContentTemplate.builder()
                        .withEnabled(false)
                        .withExpression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.FEED)
                                        .condition(Condition.EQUALS)
                                        .value(feedName)
                                        .build())
                                .build())
                        .build()))
                .build();
        Mockito.when(mockFeedStore.findByName(Mockito.eq(feedName)))
                .thenReturn(List.of());
        Mockito.when(mockContentTemplateStore.getOrCreate())
                .thenReturn(contentTemplates);

        final Optional<FeedDoc> optFeedDoc = contentAutoCreationService.tryCreateFeed(
                feedName, userDesc, attributeMap);

        assertThat(optFeedDoc)
                .isEmpty();
    }

    @Test
    void testFeedNotExist_NoMatchingTemplate() {
        final String feedName = "FEED_X";
        final UserDesc userDesc = UserDesc.forSubjectId("user1");
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);

        final ContentTemplates contentTemplates = ContentTemplates.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Templates")
                .contentTemplates(List.of(ContentTemplate.builder()
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.FEED)
                                        .condition(Condition.EQUALS)
                                        .value(feedName)
                                        .build())
                                .build())
                        .build()))
                .build();
        Mockito.when(mockFeedStore.findByName(Mockito.eq(feedName)))
                .thenReturn(List.of());
        Mockito.when(mockContentTemplateStore.getOrCreate())
                .thenReturn(contentTemplates);
        Mockito.when(mockExpressionMatcherFactory.create(Mockito.any()))
                .thenReturn(mockExpressionMatcher);
        Mockito.when(mockExpressionMatcher.match(Mockito.any(), Mockito.any()))
                .thenReturn(false);
        Mockito.when(mockAutoContentCreationConfigProvider.get())
                .thenReturn(new AutoContentCreationConfig());

        final Optional<FeedDoc> optFeedDoc = contentAutoCreationService.tryCreateFeed(
                feedName, userDesc, attributeMap);

        assertThat(optFeedDoc)
                .isEmpty();
    }

//    @Test
//    void testFeedNotExist_MatchingTemplate() {
//        final String feedName = "FEED_X";
//        final DocRef docRef = FeedDoc.buildDocRef()
//                .randomUuid()
//                .name(feedName)
//                .build();
//        final FeedDoc feedDoc = FeedDoc.builder()
//                .withName(feedName)
//                .withDocRef(docRef)
//                .build();
//        final UserDesc userDesc = UserDesc.forSubjectId("user1");
//        final String runAsUserSubjectId = "user2";
//        final AttributeMap attributeMap = new AttributeMap();
//        attributeMap.put(StandardHeaderArguments.FEED, feedName);
//
//        Mockito.when(mockFeedStore.findByName(Mockito.eq(feedName)))
//                .thenReturn(List.of());
//
//        final ContentTemplates contentTemplates = ContentTemplates.builder()
//                .withName("Templates")
//                .withContentTemplates(List.of(ContentTemplate.builder()
//                        .withEnabled(true)
//                        .withExpression(ExpressionOperator.builder()
//                                .addTerm(ExpressionTerm.builder()
//                                        .field(StandardHeaderArguments.FEED)
//                                        .condition(Condition.EQUALS)
//                                        .value(feedName)
//                                        .build())
//                                .build())
//                        .build()))
//                .build();
//        Mockito.when(mockContentTemplateStore.getOrCreate())
//                .thenReturn(contentTemplates);
//        Mockito.when(mockExpressionMatcherFactory.create(Mockito.any()))
//                .thenReturn(mockExpressionMatcher);
//        Mockito.when(mockExpressionMatcher.match(Mockito.any(), Mockito.any()))
//                .thenReturn(true);
//        Mockito.when(mockAutoContentCreationConfigProvider.get())
//                .thenReturn(AutoContentCreationConfig.builder()
//                        .createAsSubjectId(runAsUserSubjectId)
//                        .createAsType(UserType.USER)
//                        .build());
//        Mockito.when(mockUserService.getUserBySubjectId(Mockito.eq(runAsUserSubjectId)))
//                .thenReturn(Optional.of(User.builder()
//                        .subjectId(runAsUserSubjectId)
//                        .group(false)
//                        .build()));
//        Mockito.doAnswer(invocation -> {
//            final Supplier<?> supplier = invocation.getArgument(1);
//            return supplier.get();
//        }).when(mockSecurityContext).asUserResult(Mockito.any(UserRef.class), Mockito.any());
//
//        final Optional<FeedDoc> optFeedDoc = contentAutoCreationService.tryCreateFeed(
//                feedName, userDesc, attributeMap);
//
//        assertThat(optFeedDoc)
//                .hasValue(feedDoc);
//    }
}
