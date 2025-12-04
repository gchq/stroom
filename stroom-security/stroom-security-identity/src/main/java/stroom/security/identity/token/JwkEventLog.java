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

package stroom.security.identity.token;

import stroom.event.logging.api.StroomEventLoggingService;

import jakarta.inject.Inject;

/**
 * A service to allow other components to easily create Stroom logging events.
 */
public class JwkEventLog {

    private final StroomEventLoggingService eventLoggingService;

    @Inject
    public JwkEventLog(final StroomEventLoggingService eventLoggingService) {
        this.eventLoggingService = eventLoggingService;
    }

//    public void successfulLogin(final HttpServletRequest request, String usersEmail) {
//        Event event = createAuthenticateEvent("Logon",
//                request, usersEmail, AuthenticateAction.LOGON,
//                "User logged in successfully.");
//        eventLoggingService.log(event);
//    }
//
//    public void failedLogin(final HttpServletRequest request, String usersEmail) {
//        Event event = createAuthenticateEvent("Logon",
//                request, usersEmail, AuthenticateAction.LOGON,
//                "User attempted to log in but failed.");
//        AuthenticateOutcome authenticateOutcome = new AuthenticateOutcome();
//        authenticateOutcome.setReason(INCORRECT_USERNAME_OR_PASSWORD);
//        event.getEventDetail().getAuthenticate().setOutcome(authenticateOutcome);
//        eventLoggingService.log(event);
//    }
//
//
//    public void failedLoginBecause(final HttpServletRequest request, String usersEmail, String failedStatus) {
//        Event event = createAuthenticateEvent("Logon",
//                request, usersEmail, AuthenticateAction.LOGON,
//                "User attempted to log in but failed because the account is " + failedStatus + ".");
//        AuthenticateOutcome authenticateOutcome = new AuthenticateOutcome();
//        authenticateOutcome.setReason(ACCOUNT_LOCKED);
//        event.getEventDetail().getAuthenticate().setOutcome(authenticateOutcome);
//        eventLoggingService.log(event);
//    }
//
//    public void logout(final HttpServletRequest request, String usersEmail) {
//        Event event = createAuthenticateEvent("Logoff",
//                request, usersEmail, AuthenticateAction.LOGOFF,
//                "User logged off.");
//        eventLoggingService.log(event);
//    }
//
//    public void resetPassword(final HttpServletRequest request, String usersEmail) {
//        Event event = createAuthenticateEvent("ResetPassword",
//                request, usersEmail, AuthenticateAction.RESET_PASSWORD,
//                "User reset their password");
//        eventLoggingService.log(event);
//    }
//
//    public void changePassword(final HttpServletRequest request, String usersEmail) {
//        Event event = createAuthenticateEvent("ChangePassword",
//                request, usersEmail, AuthenticateAction.CHANGE_PASSWORD,
//                "User changed their password.");
//        eventLoggingService.log(event);
//    }

//
//    public void search(
//            String typeId,
//            HttpServletRequest request,
//            String usersEmail,
//            Search search,
//            String description) {
//        Event.EventDetail eventDetail = new Event.EventDetail();
//        eventDetail.setSearch(search);
//        eventDetail.setTypeId(typeId);
//        eventDetail.setDescription(description);
//        Event event = stroomEventFactory.createEvent(request, usersEmail);
//        event.setEventDetail(eventDetail);
//
//        stroomEventFactory.log(event);
//    }
//
//    public void create(
//            String typeId,
//            HttpServletRequest request,
//            String usersEmail,
//            ObjectOutcome objectOutcome,
//            String description) {
//        Event.EventDetail eventDetail = new Event.EventDetail();
//        eventDetail.setCreate(objectOutcome);
//        eventDetail.setDescription(description);
//        eventDetail.setTypeId(typeId);
//        Event event = stroomEventFactory.createEvent(request, usersEmail);
//        event.setEventDetail(eventDetail);
//
//        stroomEventFactory.log(event);
//    }
//
//    public void view(
//            String typeId,
//            HttpServletRequest request,
//            String usersEmail,
//            ObjectOutcome objectOutcome,
//            String description) {
//        Event.EventDetail eventDetail = new Event.EventDetail();
//        eventDetail.setView(objectOutcome);
//        eventDetail.setDescription(description);
//        eventDetail.setTypeId(typeId);
//        Event event = stroomEventFactory.createEvent(request, usersEmail);
//        event.setEventDetail(eventDetail);
//
//        stroomEventFactory.log(event);
//    }
//
//    public void update(
//            String typeId,
//            HttpServletRequest request,
//            String usersEmail,
//            Event.EventDetail.Update update,
//            String description) {
//        Event.EventDetail eventDetail = new Event.EventDetail();
//        eventDetail.setUpdate(update);
//        eventDetail.setDescription(description);
//        eventDetail.setTypeId(typeId);
//        Event event = stroomEventFactory.createEvent(request, usersEmail);
//        event.setEventDetail(eventDetail);
//
//        stroomEventFactory.log(event);
//    }
//
//    public void delete(
//            String typeId,
//            HttpServletRequest request,
//            String usersEmail,
//            ObjectOutcome objectOutcome,
//            String description) {
//        Event.EventDetail eventDetail = new Event.EventDetail();
//        eventDetail.setDelete(objectOutcome);
//        eventDetail.setDescription(description);
//        eventDetail.setTypeId(typeId);
//        Event event = stroomEventFactory.createEvent(request, usersEmail);
//        event.setEventDetail(eventDetail);
//
//        stroomEventFactory.log(event);
//    }
//
//    public Event createAuthenticateEvent(
//            String typeId,
//            HttpServletRequest request,
//            String usersEmail,
//            AuthenticateAction authenticateAction,
//            String description) {
//        User user = new User();
//        user.setId(usersEmail);
//        Event.EventDetail.Authenticate authenticate = new Event.EventDetail.Authenticate();
//        authenticate.setAction(authenticateAction);
//        authenticate.setLogonType(AuthenticateLogonType.INTERACTIVE);
//        authenticate.setUser(user);
//        Event.EventDetail eventDetail = new Event.EventDetail();
//        eventDetail.setAuthenticate(authenticate);
//        eventDetail.setDescription(description);
//        eventDetail.setTypeId(typeId);
//        Event event = eventLoggingService.createEvent(request, usersEmail);
//        event.setEventDetail(eventDetail);
//
//        return event;
//    }

}
