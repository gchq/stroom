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
            throw new BadRequestException();
        }
    }

    /**
     * Used to validate a request argument and throw a {@link BadRequestException} if it is null
     */
    public static void requireNonNull(final Object object,
                                      final String message,
                                      final Object... args) throws BadRequestException {
        if (object == null) {
            final String msg = args.length > 0
                    ? LogUtil.message(message, args)
                    : message;
            throw new BadRequestException(msg);
        }
    }

    /**
     * Used to validate a request argument and throw a {@link BadRequestException} if it is null
     */
    public static void requireNonNull(final Object object, Supplier<String> messageSupplier)
            throws BadRequestException {
        if (object == null) {
            throw new BadRequestException(messageSupplier != null
                    ? messageSupplier.get()
                    : null);
        }
    }

    public static void requireMatchingIds(final int id, final HasIntegerId object) {
        if (object == null) {
            throw new BadRequestException("Object is null");
        }
        // Allow for the object not having an id in which case the int id wins
        if (object.getId() != null && object.getId() != id) {
            throw new BadRequestException("Id " + id + " doesn't match id in object " + object.getId());
        }
    }

    public static void requireMatchingUuids(final String uuid, final HasUuid object) {
        if (object == null) {
            throw new BadRequestException("Object is null");
        }
        if (uuid == null) {
            throw new BadRequestException("uuid is null");
        }
        // Allow for the object not having a uuid in which case the string uuid wins
        if (object.getUuid() != null && !object.getUuid().equals(uuid)) {
            throw new BadRequestException("UUID " + uuid + " doesn't match UUID in object " + object.getUuid());
        }
    }

    /**
     * Return a BAD_REQUEST (400) response
     * SLF style messages ({}), NOT String.format (%s).
     */
    public static Response badRequest(final String msg, final Object... args) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(LogUtil.message(msg, args))
                .build();
    }

    /**
     * Return a NOT_FOUND (404) response
     * SLF style messages ({}), NOT String.format (%s).
     */
    public static Response notFound(final String msg, final Object... args) {
        return Response
                .status(Status.NOT_FOUND)
                .entity(LogUtil.message(msg, args))
                .build();
    }

    /**
     * Return an OK (200) response.
     * SLF style messages ({}), NOT String.format (%s).
     */
    public static Response ok(final String msg, final Object... args) {
        return Response
                .ok(LogUtil.message(msg, args))
                .build();
    }

    /**
     * Ensure value is non null or throw a {@link NotFoundException}. For use in validating
     * responses.
     * SLF style messages ({}), NOT String.format (%s).
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
     * SLF style messages ({}), NOT String.format (%s).
     */
    public static <T> T ensureNotEmptyResult(final Optional<T> optValue,
                                             final String msg,
                                             final Object... args) {
        if (optValue == null || optValue.isEmpty()) {
            throw new NotFoundException(LogUtil.message(msg, args));
        }
        return optValue.get();
    }
}
