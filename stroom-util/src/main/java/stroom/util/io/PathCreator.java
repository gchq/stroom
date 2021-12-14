package stroom.util.io;

import com.google.inject.ImplementedBy;

import java.time.ZonedDateTime;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@ImplementedBy(SimplePathCreator.class)
public interface PathCreator {

    String replaceTimeVars(String path);

    String replaceTimeVars(String path, ZonedDateTime dateTime);

    String replaceSystemProperties(String path);

    String makeAbsolute(String path);

    String replaceUUIDVars(String path);

    String replaceFileName(String path, String fileName);

    String[] findVars(String path);

    String replace(String path,
                   String type,
                   LongSupplier replacementSupplier,
                   int pad);

    String replace(String path,
                   String type,
                   Supplier<String> replacementSupplier);

    String replaceAll(String path);

    String replaceContextVars(String path);

    @Override
    String toString();
}
