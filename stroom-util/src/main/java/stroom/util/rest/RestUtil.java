package stroom.util.rest;

import stroom.util.logging.LogUtil;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.HasUuid;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.function.Supplier;

public class RestUtil {

    private RestUtil() {
    }

    public static void requireNonNull(final Object object) throws BadRequestException {
        if (object == null) {
            throw new BadRequestException();
        }
    }

    public static void requireNonNull(final Object object, String message) throws BadRequestException {
        if (object == null) {
            throw new BadRequestException(message);
        }
    }

    public static void requireNonNull(final Object object, Supplier<String> messageSupplier) throws BadRequestException {
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
        if (object.getId() != id) {
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
        if (object.getUuid().equals(uuid)) {
            throw new BadRequestException("UUID " + uuid + " doesn't match UUID in object " + object.getUuid());
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
}
