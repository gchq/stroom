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

package stroom.data.client.component;

import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.feed.client.event.CopyFeedUrlEvent;
import stroom.feed.client.event.OpenFeedEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.shared.AppPermission;
import stroom.util.client.cell.DocRefCell;
import stroom.util.client.cell.FeedRefCell;
import stroom.util.client.cell.UserRefCell;
import stroom.util.shared.UserRef;

import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

// The cells themselves need a browser (their menu items build URLs from the page's location), so
// these capture the callbacks the controller gives a cell's builder; the cells' use of them is
// covered by the Widgets/Cell Renderers stories
class TestRefCellsController {

    private static final UserRef ALICE = UserRef.builder().uuid("alice-uuid").subjectId("alice").build();
    private static final UserRef BOB = UserRef.builder().uuid("bob-uuid").subjectId("bob").build();

    private final SimpleEventBus eventBus = new SimpleEventBus();
    private final List<Object> fired = new ArrayList<>();
    private RefCellsController controller;

    @AfterEach
    void dispose() {
        if (controller != null) {
            controller.dispose();
        }
    }

    @Test
    void aUserManagerCanOpenAnyUser() {
        final ClientSecurityContext securityContext = securityContext(true, ALICE);

        assertThat(RefCellsController.canOpen(securityContext, ALICE)).isTrue();
        assertThat(RefCellsController.canOpen(securityContext, BOB)).isTrue();
    }

    @Test
    void anyoneElseCanOpenOnlyThemself() {
        final ClientSecurityContext securityContext = securityContext(false, ALICE);

        assertThat(RefCellsController.canOpen(securityContext, ALICE)).isTrue();
        assertThat(RefCellsController.canOpen(securityContext, BOB)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDocumentCellOpensItsDocumentOnTheBus() {
        eventBus.addHandler(OpenDocumentEvent.getType(), event -> fired.add(event.getDocRef()));
        final DocRefCell.Builder<Object> builder = Mockito.mock(DocRefCell.Builder.class, Mockito.RETURNS_SELF);
        final DocRef docRef = new DocRef("Feed", "feed-uuid", "MY_FEED");

        controller(false).configure(builder);
        final ArgumentCaptor<Consumer<DocRef>> onOpen = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(builder).onOpen(onOpen.capture());
        onOpen.getValue().accept(docRef);

        assertThat(fired).containsExactly(docRef);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFeedCellOpensItsFeedAndCopiesItsLinkOnTheBus() {
        eventBus.addHandler(OpenFeedEvent.getType(), event -> fired.add("open " + event.getName()));
        eventBus.addHandler(CopyFeedUrlEvent.getType(), event -> fired.add("copy " + event.getName()));
        final FeedRefCell.Builder<Object> builder = Mockito.mock(FeedRefCell.Builder.class, Mockito.RETURNS_SELF);

        controller(false).configure(builder);
        final ArgumentCaptor<Consumer<String>> onOpen = ArgumentCaptor.forClass(Consumer.class);
        final ArgumentCaptor<Consumer<String>> onCopyUrl = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(builder).onOpen(onOpen.capture());
        Mockito.verify(builder).onCopyUrl(onCopyUrl.capture());
        onOpen.getValue().accept("MY_FEED");
        onCopyUrl.getValue().accept("MY_FEED");

        assertThat(fired).containsExactly("open MY_FEED", "copy MY_FEED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aUserCellOpensOnlyThoseTheCurrentUserMay() {
        eventBus.addHandler(OpenUserEvent.getType(), event -> fired.add(event.getUserRef()));
        final UserRefCell.Builder<Object> builder = Mockito.mock(UserRefCell.Builder.class, Mockito.RETURNS_SELF);

        controller(false).configure(builder);
        final ArgumentCaptor<Predicate<UserRef>> canOpen = ArgumentCaptor.forClass(Predicate.class);
        final ArgumentCaptor<Consumer<UserRef>> onOpen = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(builder).canOpen(canOpen.capture());
        Mockito.verify(builder).onOpen(onOpen.capture());
        onOpen.getValue().accept(ALICE);

        assertThat(canOpen.getValue().test(ALICE)).isTrue();
        assertThat(canOpen.getValue().test(BOB)).isFalse();
        assertThat(fired).containsExactly(ALICE);
    }

    // Alice is the current user
    private RefCellsController controller(final boolean managesUsers) {
        controller = new RefCellsController(eventBus, securityContext(managesUsers, ALICE));
        return controller;
    }

    private static ClientSecurityContext securityContext(final boolean managesUsers, final UserRef currentUser) {
        final ClientSecurityContext securityContext = Mockito.mock(ClientSecurityContext.class);
        Mockito.when(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION))
                .thenReturn(managesUsers);
        Mockito.when(securityContext.isCurrentUser(Mockito.any()))
                .thenAnswer(invocation -> currentUser.equals(invocation.getArgument(0)));
        return securityContext;
    }
}
