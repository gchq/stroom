package stroom.data.zip;

import stroom.util.io.FileName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StroomZipEntries {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomZipEntries.class);

    // baseName => StroomZipEntryGroup
    private final Map<String, StroomZipEntryGroup> map = new HashMap<>();
    // baseNames in the order seen in the zip
    private final List<String> baseNames = new ArrayList<>();
    // StroomZipEntry basename => FileName (the baseName in FileName may differ)
    private final Map<String, FileName> unknownExtensionFileNames = new HashMap<>();

    public StroomZipEntry addFile(final String fileName) {
        // This treats unknown extensions as part of the base name, e.g.
        // '001.unknown' has baseName '001.unknown' but
        // '001.meta' has baseName '001'
        StroomZipEntry stroomZipEntry = StroomZipEntry.createFromFileName(fileName);
        LOGGER.debug("addFile - fileName: '{}', stroomZipEntry: {}", fileName, stroomZipEntry);

        final String baseName = stroomZipEntry.getBaseName();

        if (!stroomZipEntry.hasKnownExtension()
                && stroomZipEntry.getFullName().contains(".")) {
            // e.g. some random extension '001.mydata'
            // but they may have '001.mydata' and '001.meta' which would have different base names
            // ('001.mydata' and '001') so we need to link them up
            final FileName fn = FileName.parse(stroomZipEntry.getFullName());
            if (map.containsKey(fn.getBaseName())) {
                // re-classify our stroomZipEntry with the other baseName
                final StroomZipEntry newZipEntry = stroomZipEntry.cloneWithNewBaseName(fn.getBaseName());
                LOGGER.debug("Re-classifying {} as {}", stroomZipEntry, newZipEntry);
                stroomZipEntry = newZipEntry;
            } else {
                LOGGER.debug("Found unknown extension, baseName: '{}', fn: {}", baseName, fn);
                unknownExtensionFileNames.put(baseName, fn);
            }
        } else {
            // A known extension or no extension at all, so check if its baseName is the stem or one in
            // unknownExtensionBaseNames
            unknownExtensionFileNames.forEach((zipEntryBaseName, fn2) -> {
                if (Objects.equals(baseName, fn2.getBaseName())) {
                    // Common base name, e.g. '001' in '001.ctx' and '001.mydata', so need to re-classify it
                    final int idx = baseNames.indexOf(zipEntryBaseName);
                    if (idx >= 0) {
                        // Replace the incorrect baseName, i.e. '001.mydata' => '001'
                        baseNames.set(idx, baseName);
                        final StroomZipEntryGroup zipEntryGroup = map.remove(zipEntryBaseName);
                        if (zipEntryGroup != null) {
                            final StroomZipEntryGroup newZipEntryGroup = zipEntryGroup.cloneWithNewBaseName(baseName);
                            LOGGER.debug("Cloning group, {} => {}", zipEntryGroup, newZipEntryGroup);
                            map.put(baseName, newZipEntryGroup);
                        }
                    }
                }
            });
        }

        LOGGER.debug("Adding stroomZipEntry {}", stroomZipEntry);
        map.computeIfAbsent(stroomZipEntry.getBaseName(), k -> {
            LOGGER.debug("Adding baseName '{}'", baseName);
            baseNames.add(k);
            return new StroomZipEntryGroup(k);
        }).add(stroomZipEntry);
        return stroomZipEntry;
    }

    public Optional<StroomZipEntry> getByType(final String baseName, final StroomZipFileType stroomZipFileType) {
        final StroomZipEntryGroup group = map.get(baseName);
        if (group == null) {
            return Optional.empty();
        }
        return group.getByType(stroomZipFileType);
    }

    public Collection<StroomZipEntryGroup> getGroups() {
        return map.values();
    }

    public List<String> getBaseNames() {
        return baseNames;
    }

    @Override
    public String toString() {
        return "StroomZipEntries{" +
                "map=" + map +
                ", baseNames=" + baseNames +
                '}';
    }

    // --------------------------------------------------------------------------------


    public static class StroomZipEntryGroup {

        private final String baseName;
        private final StroomZipEntry[] entries = new StroomZipEntry[StroomZipFileType.values().length];

        public StroomZipEntryGroup(final String baseName) {
            this.baseName = baseName;
        }

        public void add(final StroomZipEntry entry) {
            final int index = entry.getStroomZipFileType().getIndex();
            final StroomZipEntry existing = entries[index];
            if (existing != null) {
                throw StroomZipNameException.createDuplicateFileNameException(entry.getFullName());
            }
            if (!Objects.equals(baseName, entry.getBaseName())) {
                // Shouldn't happen as we are building the groups
                throw new RuntimeException("Base name mismatch, '" + baseName + "' and '"
                        + entry.getBaseName() + '"');
            }
            entries[index] = entry;
        }

        public Optional<StroomZipEntry> getByType(final StroomZipFileType type) {
            return Optional.ofNullable(entries[type.getIndex()]);
        }

        public String getBaseName() {
            return baseName;
        }

        boolean hasEntries() {
            return Arrays.stream(entries)
                    .anyMatch(Objects::nonNull);
        }

        StroomZipEntryGroup cloneWithNewBaseName(final String newBaseName) {
            Objects.requireNonNull(newBaseName);
            if (Objects.equals(newBaseName, baseName) || !hasEntries()) {
                return this;
            } else {
                if (!this.baseName.startsWith(newBaseName)) {
                    throw new RuntimeException(LogUtil.message("newBaseName '{}' is not a prefix of baseName '{}'",
                            newBaseName, baseName));
                }

                final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup(newBaseName);
                for (final StroomZipEntry oldEntry : this.entries) {
                    if (oldEntry != null) {
                        final StroomZipEntry newEntry = oldEntry.cloneWithNewBaseName(newBaseName);
                        zipEntryGroup.add(newEntry);
                    }
                }
                return zipEntryGroup;
            }
        }

        @Override
        public String toString() {
            return "StroomZipEntryGroup{" +
                    "baseName='" + baseName + '\'' +
                    ", entries=" + Arrays.toString(entries) +
                    '}';
        }
    }
}
