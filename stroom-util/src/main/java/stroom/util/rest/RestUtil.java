package stroom.util.rest;

import stroom.docref.HasUuid;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasIntegerId;

import java.util.Optional;
import java.util.function.Supplier;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class RestUtil {

    private RestUtil() {
    }

    /**
     * Used to validate a request argument and throw a {@link BadRequestException} if it is null
     */
    public static void requireNonNull(final Object object) throws BadRequestException {
        if (object == null) {
            throw badRequest("Object is null");
        }
    }

    /**
     * Used to validate a request argument and throw a {@link BadRequestException} if it is null
     */
    public static void requireNonNull(final Object object, String message) throws BadRequestException {
        if (object == null) {
            throw badRequest(message);
        }
    }

    /**
     * Used to validate a request argument and throw a {@link BadRequestException} if it is null
     */
    public static void requireNonNull(final Object object, Supplier<String> messageSupplier)
            throws BadRequestException {
        if (object == null) {
            throw badRequest(messageSupplier != null
                    ? messageSupplier.get()
                    : null);
        }
    }

    public static void requireMatchingIds(final int id, final HasIntegerId object) {
        if (object == null) {
            throw badRequest("Object is null");
        }
        // Allow for the object not having an id in which case the int id wins
        if (object.getId() != null && object.getId() != id) {
            throw badRequest("Id " + id + " doesn't match id in object " + object.getId());
        }
    }

    public static void requireMatchingUuids(final String uuid, final HasUuid object) {
        if (object == null) {
            throw badRequest("Object is null");
        }
        if (uuid == null) {
            throw badRequest("uuid is null");
        }
        // Allow for the object not having a uuid in which case the string uuid wins
        if (object.getUuid() != null && !object.getUuid().equals(uuid)) {
            throw badRequest("UUID " + uuid + " doesn't match UUID in object " + object.getUuid());
        }
    }

    public static Response badRequest(final String msg, final Object... args) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(LogUtil.message(msg, args))
                .build();
    }

    public static Response notFound(final String msg, final Object... args) {
        return Response
                .status(Status.NOT_FOUND)
                .entity(LogUtil.message(msg, args))
                .build();
    }

    public static Response ok(final String msg, final Object... args) {
        return Response
                .ok(LogUtil.message(msg, args))
                .build();
    }

    /**
     * Ensure value is non null or throw a {@link NotFoundException}. For use in validating
     * responses.
     */
    public static <T> T ensureNonNullResult(final T value,
                                            final String msg,
                                            final Object... args) {
        if (value == null) {
            throw new NotFoundException(LogUtil.message(msg, args));
        }
        return value;
    }

    /**
     * Ensure value is not empty or throw a {@link NotFoundException}. For use in validating
     * responses.
     */
    public static <T> T ensureNotEmptyResult(final Optional<T> optValue,
                                             final String msg,
                                             final Object... args) {
        if (optValue == null || optValue.isEmpty()) {
            throw new NotFoundException(LogUtil.message(msg, args));
        }
        return optValue.get();
    }

    public static BadRequestException badRequest(final String message, final Throwable e) {
        return new BadRequestException(
                message,
                Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build(),
                e);
    }

    public static BadRequestException badRequest(final Throwable e) {
        return new BadRequestException(
                e.getMessage(),
                Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build(),
                e);
    }

    public static BadRequestException badRequest(final String message) {
        return new BadRequestException(
                message,
                Response.status(Status.BAD_REQUEST).entity(message).build());
    }
}
