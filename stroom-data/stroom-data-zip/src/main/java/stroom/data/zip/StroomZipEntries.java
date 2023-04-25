package stroom.data.zip;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StroomZipEntries {

    private final Map<String, StroomZipEntryGroup> map = new HashMap<>();

    public StroomZipEntry addFile(final String fileName) {
        final StroomZipEntry stroomZipEntry = StroomZipEntry.createFromFileName(fileName);
        map.computeIfAbsent(stroomZipEntry.getBaseName(), StroomZipEntryGroup::new).add(stroomZipEntry);
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
            entries[index] = entry;
        }

        public Optional<StroomZipEntry> getByType(final StroomZipFileType type) {
            return Optional.ofNullable(entries[type.getIndex()]);
        }

        public String getBaseName() {
            return baseName;
        }
    }
}
