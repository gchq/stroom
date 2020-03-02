
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

package stroom.authentication.resources.user.v1;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import event.logging.Event;
import event.logging.MultiObject;
import event.logging.ObjectOutcome;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.clients.AuthorisationService;
import stroom.authentication.clients.UserServiceClient;
import stroom.authentication.impl.db.UserDao;
import stroom.authentication.service.eventlogging.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.RestResource;

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
import java.util.Optional;

import static stroom.auth.db.Tables.USERS;

@Singleton
@Path("/user/v1")
@Produces({"application/json"})
@Api(description = "Stroom User API", tags = {"User"})
public final class UserResource implements RestResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResource.class);

    private AuthorisationService authorisationService;
    private UserService userService;
    private stroom.security.impl.UserService securityUserService;
    private UserDao userDao;
    private StroomEventLoggingService stroomEventLoggingService;
    private SecurityContext securityContext;

    @Inject
    public UserResource(
            @NotNull final AuthorisationService authorisationService,
            final UserService userService,
            final stroom.security.impl.UserService securityUserService,
            final UserDao userDao,
            final StroomEventLoggingService stroomEventLoggingService,
            final SecurityContext securityContext) {
        super();
        this.authorisationService = authorisationService;
        this.userService = userService;
        this.securityUserService = securityUserService;
        this.userDao = userDao;
        this.stroomEventLoggingService = stroomEventLoggingService;
        this.securityContext = securityContext;
    }


    @ApiOperation(
            value = "Get all users.",
            response = String.class,
            tags = {"User"})
    @GET
    @Path("/")
    @Timed
    @NotNull
    public final Response getAll(@Context @NotNull HttpServletRequest httpServletRequest ) {
        String usersAsJson = userService.getAllAsJson();
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
            @ApiParam("user") @NotNull User user) {
        int newUserId = userService.create(user);
        return Response.status(Response.Status.OK).entity(newUserId).build();
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
            @QueryParam("email") String email) {
        String usersAsJson = userService.search(email);
        return Response.status(Response.Status.OK).entity(usersAsJson).build();
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
            @PathParam("id") int userId) {
        Optional<User> optionalUser = userService.get(userId);
        if(optionalUser.isPresent()){
            return Response.status(Response.Status.OK).entity(optionalUser.get()).build();
        }
        else {
            return Response.status(Response.Status.NOT_FOUND).build();
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
            @ApiParam("user") @NotNull User user,
            @PathParam("id") int userId) {
        userService.update(user, userId);
        return Response.status(Response.Status.NO_CONTENT).build();
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
            @PathParam("id") int userId) {

        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            userDao.delete(userId);

            event.logging.User user = new event.logging.User();
            user.setId(Integer.valueOf(userId).toString());
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(user);
            stroomEventLoggingService.delete(
                    "DeleteUser",
                    httpServletRequest,
                    securityContext.getUserId(),
                    objectOutcome,
                    "Delete a user by ID");

            Response response = Response.status(Response.Status.NO_CONTENT).build();
            return response;
        });
    }
}

