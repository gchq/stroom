
/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.auth.resources.user.v1;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import event.logging.Event;
import event.logging.MultiObject;
import event.logging.ObjectOutcome;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONFormat;
import org.jooq.Record;
import org.jooq.Record13;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.TableField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.clients.AuthorisationService;
import stroom.auth.clients.UserServiceClient;
import stroom.auth.daos.UserDao;
import stroom.auth.daos.UserMapper;
import stroom.auth.service.eventlogging.StroomEventLoggingService;
import stroom.auth.service.security.ServiceUser;
import stroom.auth.db.tables.records.UsersRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static stroom.auth.db.Tables.USERS;

@Singleton
@Path("/user/v1")
@Produces({"application/json"})
@Api(description = "Stroom User API", tags = {"User"})
public final class UserResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResource.class);

    private AuthorisationService authorisationService;
    private UserServiceClient userServiceClient;
    private UserDao userDao;
    private StroomEventLoggingService stroomEventLoggingService;

    @Inject
    public UserResource(
            @NotNull AuthorisationService authorisationService,
            @NotNull UserServiceClient userServiceClient,
            UserDao userDao,
            final StroomEventLoggingService stroomEventLoggingService) {
        super();
        this.authorisationService = authorisationService;
        this.userServiceClient = userServiceClient;
        this.userDao = userDao;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @ApiOperation(
            value = "Get all users.",
            response = String.class,
            tags = {"User"})
    @GET
    @Path("/")
    @Timed
    @NotNull
    public final Response getAll(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @Auth @NotNull ServiceUser authenticatedServiceUser,
            @Context @NotNull DSLContext database) {
        Preconditions.checkNotNull(authenticatedServiceUser);
        Preconditions.checkNotNull(database);

        if (!authorisationService.canManageUsers(authenticatedServiceUser.getName(), authenticatedServiceUser.getJwt())) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(UserServiceClient.UNAUTHORISED_USER_MESSAGE).build();
        }

        TableField orderByEmailField = USERS.EMAIL;
        String usersAsJson = database
                .selectFrom(USERS)
                .orderBy(orderByEmailField)
                .fetch()
                .formatJSON((new JSONFormat())
                        .header(false)
                        .recordFormat(JSONFormat.RecordFormat.OBJECT));

        ObjectOutcome objectOutcome = new ObjectOutcome();
        event.logging.Object object = new event.logging.Object();
        object.setName("GetAllUsers");
        objectOutcome.getObjects().add(object);
        stroomEventLoggingService.view(
                "GetAllUsers",
                httpServletRequest,
                authenticatedServiceUser.getName(),
                objectOutcome,
                "Read all users.");

        return Response.status(Response.Status.OK).entity(usersAsJson).build();
    }

    @ApiOperation(
            value = "Create a user.",
            response = Integer.class,
            tags = {"User"})
    @POST
    @Path("/")
    @Timed
    @NotNull
    public final Response createUser(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @Auth @NotNull ServiceUser authenticatedServiceUser,
            @Context @NotNull DSLContext database,
            @ApiParam("user") @NotNull User user) {
        // Validate
        Preconditions.checkNotNull(authenticatedServiceUser);
        Preconditions.checkNotNull(database);

        if (!authorisationService.canManageUsers(authenticatedServiceUser.getName(), authenticatedServiceUser.getJwt())) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(UserServiceClient.UNAUTHORISED_USER_MESSAGE).build();
        }

        Pair<Boolean, String> validationResults = User.isValidForCreate(user);
        boolean isUserValid = validationResults.getLeft();
        if (!isUserValid) {
            return Response.status(Response.Status.BAD_REQUEST).entity(validationResults.getRight()).build();
        }

        if (doesUserAlreadyExist(database, user.getEmail())) {
            return Response.status(Response.Status.CONFLICT).entity(UserValidationError.USER_ALREADY_EXISTS).build();
        }

        if (Strings.isNullOrEmpty(user.getState())) {
            user.setState(User.UserState.ENABLED.getStateText());
        }

        int newUserId = userDao.create(user, authenticatedServiceUser.getName());

        event.logging.User loggingUser = new event.logging.User();
        loggingUser.setId(user.getEmail());
        ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(loggingUser);
        stroomEventLoggingService.create(
                "CreateUser",
                httpServletRequest,
                authenticatedServiceUser.getName(),
                objectOutcome,
                "Create a user");

        return Response.status(Response.Status.OK).entity(newUserId).build();
    }

    @ApiOperation(
            value = "Get the details of the currently logged-in user.",
            response = String.class,
            tags = {"User"})
    @GET
    @Path("/me")
    @Timed
    @NotNull
    public final Response readCurrentUser(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @Auth @NotNull ServiceUser authenticatedServiceUser,
            @Context @NotNull DSLContext database) {
        // Validate
        Preconditions.checkNotNull(authenticatedServiceUser);
        Preconditions.checkNotNull(database);

        // We're not checking authorisation because the current user is allowed to get infomation about themselves.

        // Get the user
        Record foundUserRecord = database
                .select(USERS.ID,
                        USERS.EMAIL,
                        USERS.FIRST_NAME,
                        USERS.LAST_NAME,
                        USERS.COMMENTS,
                        USERS.STATE,
                        USERS.LOGIN_FAILURES,
                        USERS.LOGIN_COUNT,
                        USERS.LAST_LOGIN,
                        USERS.UPDATED_ON,
                        USERS.UPDATED_BY_USER,
                        USERS.CREATED_ON,
                        USERS.CREATED_BY_USER,
                        USERS.NEVER_EXPIRES,
                        USERS.FORCE_PASSWORD_CHANGE)
                .from(USERS)
                .where(new Condition[]{USERS.EMAIL.eq(authenticatedServiceUser.getName())}).fetchOne();
        Result foundUserResult = database
                .newResult(USERS.ID,
                        USERS.EMAIL,
                        USERS.FIRST_NAME,
                        USERS.LAST_NAME,
                        USERS.COMMENTS,
                        USERS.STATE,
                        USERS.LOGIN_FAILURES,
                        USERS.LOGIN_COUNT,
                        USERS.LAST_LOGIN,
                        USERS.UPDATED_ON,
                        USERS.UPDATED_BY_USER,
                        USERS.CREATED_ON,
                        USERS.CREATED_BY_USER,
                        USERS.NEVER_EXPIRES,
                        USERS.FORCE_PASSWORD_CHANGE
                );
        foundUserResult.add(foundUserRecord);
        String foundUserJson = foundUserResult.formatJSON((new JSONFormat()).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT));

        event.logging.User user = new event.logging.User();
        user.setId(foundUserRecord.get(USERS.EMAIL));
        ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(user);
        stroomEventLoggingService.view(
                "ReadCurrentUser",
                httpServletRequest,
                authenticatedServiceUser.getName(),
                objectOutcome,
                "Read the current user.");

        Response response = Response.status(Response.Status.OK).entity(foundUserJson).build();
        return response;
    }


    @ApiOperation(
            value = "Search for a user by email.",
            response = String.class,
            tags = {"User"})
    @GET
    @Path("search")
    @Timed
    @NotNull
    public final Response searchUsers(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @Auth @NotNull ServiceUser authenticatedServiceUser,
            @Context @NotNull DSLContext database,
            @QueryParam("email") String email) {
        // Validate
        Preconditions.checkNotNull(authenticatedServiceUser);
        Preconditions.checkNotNull(database);

        if (!authorisationService.canManageUsers(authenticatedServiceUser.getName(), (authenticatedServiceUser.getJwt()))) {
            return Response
                    .status(Response.Status.UNAUTHORIZED)
                    .entity(UserServiceClient.UNAUTHORISED_USER_MESSAGE).build();
        }

        // Get the users
        Result<Record13<Integer, String, String, String, String, String, Integer, Integer, Timestamp, Timestamp, String, Timestamp, String>> foundUserRecord = database
                .select(USERS.ID,
                        USERS.EMAIL,
                        USERS.FIRST_NAME,
                        USERS.LAST_NAME,
                        USERS.COMMENTS,
                        USERS.STATE,
                        USERS.LOGIN_FAILURES,
                        USERS.LOGIN_COUNT,
                        USERS.LAST_LOGIN,
                        USERS.UPDATED_ON,
                        USERS.UPDATED_BY_USER,
                        USERS.CREATED_ON,
                        USERS.CREATED_BY_USER)
                .from(USERS)
                .where(new Condition[]{USERS.EMAIL.contains(email)})
                .fetch();

        Response response;
        if (foundUserRecord == null) {
            response = Response.status(Response.Status.NOT_FOUND).build();
            return response;
        } else {
            String users = foundUserRecord.formatJSON((new JSONFormat())
                    .header(false)
                    .recordFormat(JSONFormat.RecordFormat.OBJECT));

            event.logging.User user = new event.logging.User();
            user.setId(email);
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(user);
            stroomEventLoggingService.view(
                    "SearchUser",
                    httpServletRequest,
                    authenticatedServiceUser.getName(),
                    objectOutcome,
                    "Search for a user.");

            response = Response.status(Response.Status.OK).entity(users).build();
            return response;
        }
    }



    @ApiOperation(
            value = "Get a user by ID.",
            response = String.class,
            tags = {"User"})
    @GET
    @Path("{id}")
    @Timed
    @NotNull
    public final Response getUser(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @Auth @NotNull ServiceUser authenticatedServiceUser,
            @Context @NotNull DSLContext database,
            @PathParam("id") int userId) {
        // Validate
        Preconditions.checkNotNull(authenticatedServiceUser);
        Preconditions.checkNotNull(database);

        // Get the user
        UsersRecord foundUserRecord = database
                .selectFrom(USERS)
                .where(new Condition[]{USERS.ID.eq(userId)})
                .fetchOne();
        Response response;
        if (foundUserRecord == null) {
            response = Response.status(Response.Status.NOT_FOUND).build();
            return response;
        } else {

            // We only need to check auth permissions if the user is trying to access a different user.
            String foundUserEmail = foundUserRecord.get(USERS.EMAIL);
            boolean isUserAccessingThemselves = authenticatedServiceUser.getName().equals(foundUserEmail);
            if (!isUserAccessingThemselves) {
                if (!authorisationService.canManageUsers(authenticatedServiceUser.getName(), authenticatedServiceUser.getJwt())) {
                    return Response.status(Response.Status.UNAUTHORIZED).entity(UserServiceClient.UNAUTHORISED_USER_MESSAGE).build();
                }
            }

            event.logging.User user = new event.logging.User();
            user.setId(foundUserRecord.get(USERS.EMAIL));
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(user);
            stroomEventLoggingService.view(
                    "GetUser",
                    httpServletRequest,
                    authenticatedServiceUser.getName(),
                    objectOutcome,
                    "Read a specific user.");

            User foundUser = UserMapper.map(foundUserRecord);
            response = Response.status(Response.Status.OK).entity(foundUser).build();
            return response;
        }
    }

    @ApiOperation(
            value = "Update a user.",
            response = String.class,
            tags = {"User"})
    @PUT
    @Path("{id}")
    @Timed
    @NotNull
    public final Response updateUser(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @Auth @NotNull ServiceUser authenticatedServiceUser,
            @Context @NotNull DSLContext database,
            @ApiParam("user") @NotNull User user,
            @PathParam("id") int userId) {
        UsersRecord usersRecord = (UsersRecord) database
                .selectFrom((Table) USERS)
                .where(new Condition[]{USERS.ID.eq(userId)})
                .fetchOne();

        // We only need to check auth permissions if the user is trying to access a different user.
        String foundUserEmail = usersRecord.get(USERS.EMAIL);
        boolean isUserAccessingThemselves = authenticatedServiceUser.getName().equals(foundUserEmail);
        if (!isUserAccessingThemselves) {
            if (!authorisationService.canManageUsers(authenticatedServiceUser.getName(), authenticatedServiceUser.getJwt())) {
                return Response.status(Response.Status.UNAUTHORIZED).entity(UserServiceClient.UNAUTHORISED_USER_MESSAGE).build();
            }
        }

        if(!usersRecord.getState().equalsIgnoreCase(user.getState())) {
            boolean isEnabled = user.getState().equals("enabled");
            userServiceClient.setUserStatus(authenticatedServiceUser.getJwt(), user.getEmail(), isEnabled);
        }

        user.setUpdatedByUser(authenticatedServiceUser.getName());
        user.setUpdatedOn(LocalDateTime.now().toString());
        UsersRecord updatedUsersRecord = UserMapper.updateUserRecordWithUser(user, usersRecord);
        database
                .update((Table) USERS)
                .set(updatedUsersRecord)
                .where(new Condition[]{USERS.ID.eq(userId)}).execute();

        event.logging.User eventUser = new event.logging.User();
        eventUser.setId(Integer.valueOf(userId).toString());
        MultiObject afterMultiObject = new MultiObject();
        afterMultiObject.getObjects().add(eventUser);
        Event.EventDetail.Update update = new Event.EventDetail.Update();
        update.setAfter(afterMultiObject);
        stroomEventLoggingService.update(
                "UpdateUser",
                httpServletRequest,
                authenticatedServiceUser.getName(),
                update,
                "Toggle whether a token is enabled or not.");

        Response response = Response.status(Response.Status.NO_CONTENT).build();
        return response;
    }

    @ApiOperation(
            value = "Delete a user by ID.",
            response = String.class,
            tags = {"User"})
    @DELETE
    @Path("{id}")
    @Timed
    @NotNull
    public final Response deleteUser(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @Auth @NotNull ServiceUser authenticatedServiceUser,
            @Context @NotNull DSLContext database,
            @PathParam("id") int userId) {
        // Validate
        Preconditions.checkNotNull(authenticatedServiceUser);
        Preconditions.checkNotNull(database);

        if (!authorisationService.canManageUsers(authenticatedServiceUser.getName(), authenticatedServiceUser.getJwt())) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(UserServiceClient.UNAUTHORISED_USER_MESSAGE).build();
        }

        database
                .deleteFrom((Table) USERS)
                .where(new Condition[]{USERS.ID.eq(userId)}).execute();

        event.logging.User user = new event.logging.User();
        user.setId(Integer.valueOf(userId).toString());
        ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(user);
        stroomEventLoggingService.delete(
                "DeleteUser",
                httpServletRequest,
                authenticatedServiceUser.getName(),
                objectOutcome,
                "Delete a user by ID");

        Response response = Response.status(Response.Status.NO_CONTENT).build();
        return response;
    }

    private static Boolean doesUserAlreadyExist(DSLContext database, String email) {
        int countOfSameName = database
                .selectCount()
                .from(USERS)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .fetchOne(0, Integer.TYPE);

        return countOfSameName > 0;
    }
}

