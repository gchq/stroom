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

package stroom.security.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.util.shared.UserRef;

import event.logging.AddGroups;
import event.logging.AuthorisationActionType;
import event.logging.AuthoriseEventAction;
import event.logging.Event;
import event.logging.Group;
import event.logging.Outcome;
import event.logging.RemoveGroups;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorisationEventLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorisationEventLog.class);

    private final StroomEventLoggingService eventLoggingService;

    @Inject
    public AuthorisationEventLog(final StroomEventLoggingService eventLoggingService) {
        this.eventLoggingService = eventLoggingService;
    }

    public void addPermission(final UserRef userRef,
                              final String permission,
                              final boolean success,
                              final String outcomeDescription) {
        final AddGroups addGroups = AddGroups.builder()
                .addGroups(Group.builder()
                        .withName(permission)
                        .build())
                .build();

        authorisationEvent(
                "addUserToGroup",
                "Adding user to group",
                userRef,
                addGroups,
                null,
                success,
                outcomeDescription);
    }

    public void removePermission(final UserRef userName,
                                 final String permission,
                                 final boolean success,
                                 final String outcomeDescription) {
        final RemoveGroups removeGroups = RemoveGroups.builder()
                .withGroups(Group.builder()
                        .withName(permission)
                        .build())
                .build();

        authorisationEvent(
                "removeUserFromGroup",
                "Removing user from group",
                userName,
                null,
                removeGroups,
                success,
                outcomeDescription);
    }

    private void authorisationEvent(final String typeId,
                                    final String description,
                                    final UserRef userName,
                                    final AddGroups addGroups,
                                    final RemoveGroups removeGroups,
                                    final boolean success,
                                    final String outcomeDescription) {
        try {
            final Outcome outcome = success
                    ? null
                    : Outcome.builder()
                            .withSuccess(success)
                            .withDescription(outcomeDescription)
                            .build();

            final Event event = eventLoggingService.createEvent(
                    typeId,
                    description,
                    AuthoriseEventAction.builder()
                            .addUser(StroomEventLoggingUtil.createUser(userName))
                            .withAction(AuthorisationActionType.MODIFY)
                            .withAddGroups(addGroups)
                            .withRemoveGroups(removeGroups)
                            .withOutcome(outcome)
                            .build());

            eventLoggingService.log(event);
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to create authorisation event!", e);
        }
    }
}
