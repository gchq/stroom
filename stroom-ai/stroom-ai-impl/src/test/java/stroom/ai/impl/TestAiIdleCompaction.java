/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl;

import stroom.ai.impl.AiDao.IdleChat;
import stroom.ai.impl.AskStroomAIService.IdleCompactionResult;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.User;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

/// Which idle chats the 'AI Chat Idle Compaction' job compacts, and how much it does in a run.
class TestAiIdleCompaction {

    private static final long NOW = 10_000_000L;
    private static final long TEN_MINUTES = 600_000L;

    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final AskStroomAIService service = Mockito.mock(AskStroomAIService.class);
    private final UserService userService = Mockito.mock(UserService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private AiCompactionConfig compactionConfig = new AiCompactionConfig();
    private AiIdleCompaction job;

    @BeforeEach
    void setUp() {
        final AiConfig aiConfig = Mockito.mock(AiConfig.class);
        Mockito.when(aiConfig.getCompaction()).thenAnswer(invocation -> compactionConfig);
        Mockito.when(userService.loadByUuid("user1")).thenReturn(Optional.of(user("user1", true)));
        Mockito.when(securityContext.asUserResult(any(UserRef.class), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        job = new AiIdleCompaction(aiDao, () -> aiConfig, () -> service, userService, securityContext);
    }

    @Test
    void anIdleChatOverTheIdleThresholdIsCompactedAsItsOwner() {
        final IdleChat chat = idleChat(1, 11);
        found(List.of(chat));
        Mockito.when(service.compactWhileIdle(chat)).thenReturn(IdleCompactionResult.STARTED);

        assertThat(job.exec(NOW)).isEqualTo(1);

        // Chats whose last message is older than the idle time, looking back no further than the window.
        Mockito.verify(aiDao).findIdleChats(eq(NOW - TEN_MINUTES),
                eq(NOW - TEN_MINUTES - AiIdleCompaction.LOOK_BACK.toMillis()), eq(Set.of()),
                eq(AiIdleCompaction.MAX_CHECKED_PER_RUN));
        final ArgumentCaptor<UserRef> runAs = ArgumentCaptor.forClass(UserRef.class);
        Mockito.verify(securityContext).asUserResult(runAs.capture(), any());
        assertThat(runAs.getValue().getUuid()).isEqualTo("user1");
    }

    @Test
    void nothingIsLookedAtWhenTheFeatureIsOff() {
        compactionConfig = new AiCompactionConfig(null, null, null, null, null, null);
        assertThat(job.exec(NOW)).isZero();

        compactionConfig = new AiCompactionConfig(null, null, null, null, StroomDuration.ZERO, null);
        assertThat(job.exec(NOW)).isZero();

        Mockito.verify(aiDao, Mockito.never()).findIdleChats(anyLong(), anyLong(), anyCollection(), anyInt());
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void aChatUnderTheIdleThresholdIsNotMeasuredAgainUntilItHasANewMessage() {
        final IdleChat chat = idleChat(1, 11);
        found(List.of(chat));
        Mockito.when(service.compactWhileIdle(chat)).thenReturn(IdleCompactionResult.UNDER_THRESHOLD);

        assertThat(job.exec(NOW)).isZero();
        assertThat(job.exec(NOW + 60_000)).isZero();

        // The second run leaves out the chat's last message, so the chat isn't measured again; a new message
        // isn't left out.
        final ArgumentCaptor<Collection<Integer>> excluded = collectionCaptor();
        Mockito.verify(aiDao, Mockito.times(2)).findIdleChats(anyLong(), anyLong(), excluded.capture(), anyInt());
        assertThat(excluded.getAllValues().get(0)).isEmpty();
        assertThat(excluded.getAllValues().get(1)).containsExactly(11);
    }

    @Test
    void whatIsRememberedIsForgottenOnceTheChatHasLeftTheWindow() {
        final IdleChat chat = idleChat(1, 11);
        found(List.of(chat));
        Mockito.when(service.compactWhileIdle(chat)).thenReturn(IdleCompactionResult.UNDER_THRESHOLD);
        job.exec(NOW);
        assertThat(job.rememberedCount()).isOne();

        found(List.of());
        job.exec(NOW + AiIdleCompaction.LOOK_BACK.toMillis() + TEN_MINUTES + 1);

        assertThat(job.rememberedCount()).isZero();
    }

    @Test
    void aBusyChatIsTriedAgainNextTime() {
        final IdleChat chat = idleChat(1, 11);
        found(List.of(chat));
        Mockito.when(service.compactWhileIdle(chat)).thenReturn(IdleCompactionResult.BUSY);

        job.exec(NOW);
        job.exec(NOW + 60_000);

        assertThat(job.rememberedCount()).isZero();
        Mockito.verify(service, Mockito.times(2)).compactWhileIdle(chat);
    }

    @Test
    void aRunStartsNoMoreThanItsShareOfCompactions() {
        final List<IdleChat> chats = IntStream.rangeClosed(1, AiIdleCompaction.MAX_STARTED_PER_RUN + 3)
                .mapToObj(id -> idleChat(id, id * 10))
                .toList();
        found(chats);
        Mockito.when(service.compactWhileIdle(any())).thenReturn(IdleCompactionResult.STARTED);

        assertThat(job.exec(NOW)).isEqualTo(AiIdleCompaction.MAX_STARTED_PER_RUN);

        Mockito.verify(service, Mockito.times(AiIdleCompaction.MAX_STARTED_PER_RUN)).compactWhileIdle(any());
    }

    @Test
    void aChatOfAUserWhoCanNoLongerUseStroomIsLeftAlone() {
        final IdleChat disabled = new IdleChat(1, "user2", 11, NOW - TEN_MINUTES - 1);
        final IdleChat deleted = new IdleChat(2, "user3", 21, NOW - TEN_MINUTES - 1);
        found(List.of(disabled, deleted));
        Mockito.when(userService.loadByUuid("user2")).thenReturn(Optional.of(user("user2", false)));
        Mockito.when(userService.loadByUuid("user3")).thenReturn(Optional.empty());

        assertThat(job.exec(NOW)).isZero();

        Mockito.verifyNoInteractions(service);
        assertThat(job.rememberedCount()).isEqualTo(2);
    }

    @Test
    void aFailureWithOneChatDoesNotStopTheOthers() {
        final IdleChat failing = idleChat(1, 11);
        final IdleChat fine = idleChat(2, 21);
        found(List.of(failing, fine));
        Mockito.when(service.compactWhileIdle(failing)).thenThrow(new IllegalStateException("boom"));
        Mockito.when(service.compactWhileIdle(fine)).thenReturn(IdleCompactionResult.STARTED);

        assertThat(job.exec(NOW)).isOne();
    }

    private void found(final List<IdleChat> chats) {
        Mockito.when(aiDao.findIdleChats(anyLong(), anyLong(), anyCollection(), anyInt())).thenReturn(chats);
    }

    private static IdleChat idleChat(final int chatId, final int lastMessageId) {
        return new IdleChat(chatId, "user1", lastMessageId, NOW - TEN_MINUTES - 1);
    }

    private static User user(final String uuid, final boolean enabled) {
        return User.builder().uuid(uuid).subjectId(uuid).enabled(enabled).build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<Integer>> collectionCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }
}
