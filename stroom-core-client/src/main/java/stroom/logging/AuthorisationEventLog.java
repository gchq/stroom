/*
 * Copyright 2016 Crown Copyright
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

package stroom.logging;

import event.logging.Authorisation;
import event.logging.Event;
import event.logging.Group;
import event.logging.Outcome;
import event.logging.User;
import org.springframework.stereotype.Component;
import stroom.util.logging.StroomLogger;

import javax.annotation.Resource;

@Component
public class AuthorisationEventLog {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AuthorisationEventLog.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;
    @Resource
    private CurrentActivity currentActivity;

    public void addUserToGroup(final String userName, final String groupName, final boolean success, final String outcomeDescription) {
        final Event.EventDetail.Authorise.AddGroups addGroups = new Event.EventDetail.Authorise.AddGroups();
        addGroups.getGroup().add(createGroup(groupName));
        authorisationEvent("addUserToGroup", "Adding user to group", userName, addGroups, null, success, outcomeDescription);
    }

    public void removeUserFromGroup(final String userName, final String groupName, final boolean success, final String outcomeDescription) {
        final Event.EventDetail.Authorise.RemoveGroups removeGroups = new Event.EventDetail.Authorise.RemoveGroups();
        removeGroups.getGroup().add(createGroup(groupName));
        authorisationEvent("removeUserFromGroup", "Removing user from group", userName, null, removeGroups, success, outcomeDescription);
    }

    private Group createGroup(final String groupName) {
        final Group group = new Group();
        group.setName(groupName);
        return group;
    }

    private void authorisationEvent(final String typeId, final String description, final String userName,
                                    final Event.EventDetail.Authorise.AddGroups addGroups, final Event.EventDetail.Authorise.RemoveGroups removeGroups, final boolean success, final String outcomeDescription) {
        try {
            final Event event = eventLoggingService.createAction(typeId, description);

            final User user = new User();
            user.setName(userName);

            final Event.EventDetail.Authorise authorise = new Event.EventDetail.Authorise();
            authorise.getObjects().add(user);
            authorise.setAction(Authorisation.MODIFY);
            authorise.setAddGroups(addGroups);
            authorise.setRemoveGroups(removeGroups);
            currentActivity.decorate(authorise::getData);
            event.getEventDetail().setAuthorise(authorise);

            if (!success) {
                final Outcome outcome = new Outcome();
                outcome.setSuccess(success);
                outcome.setDescription(outcomeDescription);
                authorise.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }
}
