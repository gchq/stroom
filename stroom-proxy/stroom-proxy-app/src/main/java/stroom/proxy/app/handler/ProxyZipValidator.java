package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.util.io.FileName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>
 * This class is used to ensure that zip files conform to the proxy zip standard.
 * </p><p>
 * The proxy file format consists of entries of 10 numeric characters and appropriate file extensions.
 * The entry order will also be specific with associated entries having the order (manifest, meta, context, data).
 * </p><p>
 * Note that it is not necessary for a manifest file (.mf) to exist at all but if must be the first file if it is
 * present. Meta files may be omitted if there is accompanying meta data received via header arguments.
 * It is also not necessary for context files (.ctx) to exist (in fact they are rarely used), but if present
 * they must follow the meta file (.meta). Finally all entry sets must include the actual data to be valid (.dat).
 * </p><p>
 * An example zip file will look like this:
 * 0000000001.mf
 * 0000000001.meta
 * 0000000001.ctx
 * 0000000001.dat
 * 0000000002.meta
 * 0000000002.ctx
 * 0000000002.dat
 * 0000000003.meta
 * 0000000003.dat
 * ...
 * </p>
 */
public class ProxyZipValidator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyZipValidator.class);

    private String lastBaseName;
    private StroomZipFileType lastType;
    private boolean valid = true;
    private long count;
    private String errorMessage;

    public void addEntry(final String entryName) {
        // Stop checking when entries are no longer valid.
        if (valid) {
            final FileName fileName = FileName.parse(entryName);
            final Optional<StroomZipFileType> optionalStroomZipFileType = Arrays
                    .stream(StroomZipFileType.values())
                    .filter(type -> type.getExtension().equals(fileName.getExtension()))
                    .findAny();
            if (optionalStroomZipFileType.isEmpty()) {
                error("An unknown entry type was found '" + entryName + "'");

            } else {
                final StroomZipFileType stroomZipFileType = optionalStroomZipFileType.get();

                if (!Objects.equals(lastBaseName, fileName.getBaseName())) {
                    // If we are switching base name then check the base name is valid.
                    final String expectedBaseName = NumericFileNameUtil.create(++count);
                    if (!expectedBaseName.equals(fileName.getBaseName())) {
                        error("Unexpected base name found '" +
                                fileName.getBaseName() +
                                "' expected '" +
                                expectedBaseName +
                                "'");
                    } else {
                        // Check the extension order with no previous extension as this is a new group.
                        checkExtensionOrder(entryName, stroomZipFileType, null);
                    }

                } else {
                    // Check that the file type follows the expected order.
                    checkExtensionOrder(entryName, stroomZipFileType, lastType);
                }

                lastBaseName = fileName.getBaseName();
                lastType = stroomZipFileType;
            }
        }
    }

    private void checkExtensionOrder(final String entryName,
                                     final StroomZipFileType type,
                                     final StroomZipFileType lastType) {
        // Check that the file type follows the expected order.
        switch (type) {
            case MANIFEST -> {
                if (lastBaseName != null) {
                    error("A manifest entry was found but was not the first item '" + entryName + "'");
                }
            }
            case META -> {
                if (lastType != null &&
                        !lastType.equals(StroomZipFileType.MANIFEST)) {
                    error("A meta entry was found after " + lastType + " '" + entryName + "'");
                }
            }
            case CONTEXT -> {
                if (lastType != null &&
                        !lastType.equals(StroomZipFileType.MANIFEST) &&
                        !lastType.equals(StroomZipFileType.META)) {
                    error("A context entry was found after " + lastType + " '" + entryName + "'");
                }
            }
            case DATA -> {
                if (lastType != null &&
                        !lastType.equals(StroomZipFileType.MANIFEST) &&
                        !lastType.equals(StroomZipFileType.META) &&
                        !lastType.equals(StroomZipFileType.CONTEXT)) {
                    error("A data entry was found after " + lastType + " '" + entryName + "'");
                }
            }
        }
    }

    private void finalCheck() {
        if (valid) {
            if (lastType == null) {
                error("No entries added");
            } else if (!StroomZipFileType.DATA.equals(lastType)) {
                error("The final entry was not a data entry '" + lastType + "'");
            }
        }
    }

    private void error(final String message) {
        valid = false;
        errorMessage = message;
        LOGGER.debug(errorMessage);
    }

    public boolean isValid() {
        finalCheck();
        return valid;
    }

    public String getErrorMessage() {
        finalCheck();
        return errorMessage;
    }
}
