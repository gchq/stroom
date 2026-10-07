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

package stroom.security.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestUserAndGroupsPresenter {

    private MockedStatic<GWT> gwt;
    private UserAndGroupsPresenter presenter;
    private UserListPresenter users;
    private UserListPresenter parents;
    private UserListPresenter children;
    private UserRefPopupPresenter popup;
    private RestFactory restFactory;
    private ButtonView addParent;
    private ButtonView removeParent;
    private ButtonView addChild;
    private ButtonView removeChild;

    @BeforeEach
    void setUp() {
        gwt = mockStatic(GWT.class);
        gwt.when(() -> GWT.create(any())).thenAnswer(call -> mock((Class<?>) call.getArgument(0)));
        users = mock(UserListPresenter.class, RETURNS_DEEP_STUBS);
        parents = mock(UserListPresenter.class, RETURNS_DEEP_STUBS);
        children = mock(UserListPresenter.class, RETURNS_DEEP_STUBS);
        when(users.getSelectionModel().getSelected()).thenReturn(null);
        when(parents.getSelectionModel().getSelected()).thenReturn(null);
        when(children.getSelectionModel().getSelected()).thenReturn(null);
        when(users.addButton(any())).thenAnswer(call -> mock(ButtonView.class));
        addParent = mock(ButtonView.class);
        removeParent = mock(ButtonView.class);
        addChild = mock(ButtonView.class);
        removeChild = mock(ButtonView.class);
        when(parents.addButton(any())).thenReturn(addParent, removeParent);
        when(children.addButton(any())).thenReturn(addChild, removeChild);
        popup = mock(UserRefPopupPresenter.class);
        restFactory = mock(RestFactory.class, RETURNS_DEEP_STUBS);
        final List<UserListPresenter> lists = List.of(users, parents, children);
        final AtomicInteger index = new AtomicInteger();
        presenter = new UserAndGroupsPresenter(
                new SimpleEventBus(),
                mock(UserAndGroupsPresenter.UserAndGroupsView.class),
                () -> lists.get(index.getAndIncrement()),
                mock(CreateUserPresenter.class),
                () -> mock(CreateNewGroupPresenter.class),
                () -> popup,
                mock(UiConfigCache.class),
                restFactory);
        presenter.onBind();
    }

    @AfterEach
    void tearDown() {
        if (gwt != null) {
            gwt.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAddParent(final boolean fixedUser) {
        final User owner = selectOwner(fixedUser, false);
        final User group = user("parent", true);
        selectPopupResult(group.asRef());
        click(addParent);
        final UserResource resource = executeRequest();
        verify(resource).addUserToGroup(owner.getUuid(), group.getUuid());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAddChild(final boolean fixedUser) {
        final User owner = selectOwner(fixedUser, true);
        final User member = user("member", false);
        selectPopupResult(member.asRef());
        click(addChild);
        final UserResource resource = executeRequest();
        verify(resource).addUserToGroup(member.getUuid(), owner.getUuid());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRemoveParent(final boolean fixedUser) {
        final User owner = selectOwner(fixedUser, false);
        final User group = user("parent", true);
        when(parents.getSelectionModel().getSelected()).thenReturn(group);
        click(removeParent);
        final UserResource resource = executeRequest();
        verify(resource).removeUserFromGroup(owner.getUuid(), group.getUuid());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRemoveChild(final boolean fixedUser) {
        final User owner = selectOwner(fixedUser, true);
        final User member = user("member", false);
        when(children.getSelectionModel().getSelected()).thenReturn(member);
        click(removeChild);
        final UserResource resource = executeRequest();
        verify(resource).removeUserFromGroup(member.getUuid(), owner.getUuid());
    }

    @Test
    void testRefreshKeepsFixedOwner() {
        selectOwner(true, true);
        clearInvocations(addParent, addChild);
        presenter.refresh();
        verify(addParent).setEnabled(true);
        verify(addChild).setEnabled(true);
    }

    @Test
    void testFixedOwnerOverridesHiddenTableSelection() {
        final User owner = selectOwner(true, false);
        when(users.getSelectionModel().getSelected()).thenReturn(user("stale", false));
        final User group = user("parent", true);
        selectPopupResult(group.asRef());
        click(addParent);
        verify(executeRequest()).addUserToGroup(owner.getUuid(), group.getUuid());
    }

    private User selectOwner(final boolean fixedUser, final boolean group) {
        final User owner = user("owner", group);
        if (fixedUser) {
            presenter.setUserRef(owner.asRef());
            assertThat(users.getSelectionModel().getSelected()).isNull();
        } else {
            when(users.getSelectionModel().getSelected()).thenReturn(owner);
            presenter.refresh();
        }
        return owner;
    }

    private User user(final String id, final boolean group) {
        return User.builder().uuid(id).subjectId(id).group(group).build();
    }

    private void selectPopupResult(final UserRef selected) {
        doAnswer(call -> {
            final Consumer<UserRef> consumer = call.getArgument(1);
            consumer.accept(selected);
            return null;
        }).when(popup).show(anyString(), any());
    }

    private void click(final ButtonView button) {
        final ArgumentCaptor<ClickHandler> handler = ArgumentCaptor.forClass(ClickHandler.class);
        verify(button).addClickHandler(handler.capture());
        final ClickEvent event = mock(ClickEvent.class);
        when(event.getNativeButton()).thenReturn(NativeEvent.BUTTON_LEFT);
        handler.getValue().onClick(event);
    }

    private UserResource executeRequest() {
        final ArgumentCaptor<Function<UserResource, Boolean>> request = ArgumentCaptor.captor();
        verify(restFactory.create(any(UserResource.class))).method(request.capture());
        final UserResource resource = mock(UserResource.class);
        request.getValue().apply(resource);
        return resource;
    }
}
