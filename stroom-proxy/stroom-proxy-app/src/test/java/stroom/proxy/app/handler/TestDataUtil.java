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

package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.FeedKey.FeedKeyInterner;
import stroom.test.common.data.DataGenerator;
import stroom.test.common.data.FlatDataWriterBuilder;
import stroom.util.exception.ThrowingFunction;
import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;
import stroom.util.zip.ZipUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestDataUtil {

    public static Path writeZip(final FeedKey... feedKeys) throws IOException {
        final Path tempDir = Files.createTempDirectory("temp");
        final FileGroup fileGroup = new FileGroup(tempDir);
        writeZip(fileGroup,
                1,
                1,
                new AttributeMap(),
                NullSafe.stream(feedKeys).collect(Collectors.toSet()),
                null);
        return fileGroup.getZip();
    }

    public static void writeZip(final FileGroup fileGroup,
                                final int entryCount,
                                final Set<FeedKey> feedKeys) throws IOException {
        writeZip(fileGroup, 1, entryCount, new AttributeMap(), feedKeys, null);
    }

    public static void writeZip(final FileGroup fileGroup,
                                final int entryCount,
                                final AttributeMap attributeMap,
                                final Set<FeedKey> feedKeys,
                                final Set<FeedKey> allowedFeedKeys) throws IOException {
        writeZip(fileGroup, 1, entryCount, attributeMap, feedKeys, allowedFeedKeys);
    }

    public static void writeZip(final FileGroup fileGroup,
                                final int dataLines,
                                final int entryCount,
                                final AttributeMap attributeMap,
                                final Set<FeedKey> feedKeys,
                                final Set<FeedKey> allowedFeedKeys) throws IOException {
        final byte[] buffer = LocalByteBuffer.get();
        try (final Writer entryWriter = Files.newBufferedWriter(fileGroup.getEntries())) {
            try (final ZipWriter zipWriter = new ZipWriter(fileGroup.getZip(), buffer)) {
                int count = 1;
                for (int i = 0; i < entryCount; i++) {
                    for (final FeedKey feedKey : feedKeys) {
                        final String baseName = NumericFileNameUtil.create(count++);
                        final AttributeMap attributeMap2 = AttributeMapUtil.cloneAllowable(attributeMap);
                        AttributeMapUtil.addFeedAndType(attributeMap2, feedKey.feed(), feedKey.type());
                        final byte[] metaBytes;
                        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            AttributeMapUtil.write(attributeMap2, baos);
                            baos.flush();
                            metaBytes = baos.toByteArray();
                        }

                        final String metaEntryName = baseName + StroomZipFileType.META.getDotExtension();
                        zipWriter.writeStream(metaEntryName, new ByteArrayInputStream(metaBytes));

                        final byte[] dataBytes;
                        if (dataLines == 1) {
                            dataBytes = "test".getBytes(StandardCharsets.UTF_8);
                        } else {
                            dataBytes = generateData(dataLines).getBytes(StandardCharsets.UTF_8);
                        }
                        final String dataEntryName = baseName + StroomZipFileType.DATA.getDotExtension();
                        zipWriter.writeStream(dataEntryName, new ByteArrayInputStream(dataBytes));

                        if (allowedFeedKeys == null || allowedFeedKeys.contains(feedKey)) {
                            final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey);
                            zipEntryGroup.setMetaEntry(new Entry(metaEntryName, metaBytes.length));
                            zipEntryGroup.setDataEntry(new Entry(dataEntryName, dataBytes.length));

                            zipEntryGroup.write(entryWriter);
                        }
                    }
                }
            }
        }
    }

    private static String generateData(final int lines) {
        final long randomSeed = 234230890234L;
        final LocalDateTime startDate = LocalDateTime.of(
                2025, 4, 24, 10, 54, 32, 0);
        final StringBuilder stringBuilder = new StringBuilder();
        DataGenerator.buildDefinition()
                .addFieldDefinition(DataGenerator.randomDateTimeField(
                        "dateTime",
                        startDate,
                        startDate.plusDays(28),
                        DateTimeFormatter.ISO_DATE_TIME
                ))
                .addFieldDefinition(DataGenerator.randomIpV4Field(
                        "machineIp"))
                .addFieldDefinition(DataGenerator.fakerField(
                        "machineMacAddress",
                        faker -> faker.internet().macAddress()
                ))
                .addFieldDefinition(DataGenerator.fakerField(
                        "firstName",
                        faker -> faker.name().firstName()))
                .addFieldDefinition(DataGenerator.fakerField(
                        "surname",
                        faker -> faker.name().lastName()))
                .setDataWriter(FlatDataWriterBuilder.builder()
                        .delimitedBy(",")
                        .enclosedBy("\"")
                        .outputHeaderRow(true)
                        .build())
                .consumedBy(stringStream ->
                        stringStream.forEach(line ->
                                stringBuilder.append(line).append("n")))
                .rowCount(lines)
                .withRandomSeed(randomSeed)
                .generate();

        return stringBuilder.toString();
    }

    public static void writeFileGroup(final FileGroup fileGroup,
                                      final int dataLines,
                                      final int entryCount,
                                      final FeedKey feedKey) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
        AttributeMapUtil.write(attributeMap, fileGroup.getMeta());
        writeZip(fileGroup, dataLines, entryCount, new AttributeMap(), Set.of(feedKey), null);
    }

    public static Path getZipFile(final Path parentDir) {
        final List<Path> zipPaths = FileUtil.listChildPaths(parentDir, path ->
                Files.isRegularFile(path)
                && path.getFileName().toString().endsWith(".zip"));
        if (zipPaths.isEmpty()) {
            return null;
        } else if (zipPaths.size() > 1) {
            throw new RuntimeException("Found >1 entries files in " + parentDir);
        } else {
            return zipPaths.getFirst();
        }
    }

    /**
     * Get the {@link AttributeMap} for the single .meta file in parentDir, or null
     * if not found
     */
    public static AttributeMap getMeta(final Path parentDir) {
        final List<Path> metaPaths = FileUtil.listChildPaths(parentDir, path ->
                Files.isRegularFile(path)
                && StroomZipFileType.META.hasExtension(path));
        if (metaPaths.isEmpty()) {
            return null;
        } else if (metaPaths.size() > 1) {
            throw new RuntimeException("Found >1 meta files in " + parentDir);
        } else {
            final AttributeMap attributeMap = new AttributeMap();
            try {
                AttributeMapUtil.read(metaPaths.getFirst(), attributeMap);
                return attributeMap;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static List<ZipEntryGroup> getEntries(final Path parentDir) {
        final List<Path> entriesPaths = FileUtil.listChildPaths(parentDir, path ->
                Files.isRegularFile(path)
                && path.getFileName().toString().endsWith("." + FileGroup.ENTRIES_EXTENSION));
        if (entriesPaths.isEmpty()) {
            return null;
        } else if (entriesPaths.size() > 1) {
            throw new RuntimeException("Found >1 entries files in " + parentDir);
        } else {
            final Path path = entriesPaths.getFirst();
            try {
                final FeedKeyInterner feedKeyInterner = FeedKey.createInterner();
                try (final Stream<String> stream = Files.lines(path)) {
                    return stream.map(ThrowingFunction.unchecked(line ->
                                    ZipEntryGroup.read(line, feedKeyInterner)))
                            .toList();
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }


    // --------------------------------------------------------------------------------


    public static class ProxyZipSnapshot {

        private final List<Path> paths;
        private final List<ItemGroup> itemGroups;

        private ProxyZipSnapshot(final List<Path> paths,
                                 final List<ItemGroup> itemGroups) {
            this.paths = paths;
            this.itemGroups = itemGroups;
        }

        public List<ItemGroup> getItemGroups() {
            return itemGroups;
        }

        public List<Path> getPaths() {
            return paths;
        }

        /**
         * Create a {@link ProxyZipSnapshot} of the supplied zip file path.
         * This assumes the zip is a proxy zip containing entries with extensions .dat/.meta/.mf/.ctx
         */
        public static ProxyZipSnapshot of(final Path zipFilePath) {
            final List<Path> paths = new ArrayList<>();
            final Map<Path, Item<String>> basePathToDataMap = new HashMap<>();
            final Map<Path, Item<String>> basePathToContextMap = new HashMap<>();
            final Map<Path, Item<String>> basePathToManifestMap = new HashMap<>();
            final Map<Path, Item<AttributeMap>> basePathToMetaMap = new HashMap<>();

            ZipUtil.forEachEntry(zipFilePath, (zipFile, entry) -> {
                final Path path = Path.of(entry.getName());
                final FileName fileName = FileName.parse(entry.getName());
                final Path basePath = Path.of(fileName.getBaseName());
                final String ext = fileName.getExtension();

                if (StroomZipFileType.CONTEXT.hasExtension(path)) {
                    final Item<String> prevVal = basePathToContextMap.put(
                            basePath,
                            new Item<>(path, ZipUtil.getEntryContent(zipFile, entry)));
                    if (prevVal != null) {
                        throw new RuntimeException("Duplicate context entry for basePath " + basePath);
                    }
                } else if (StroomZipFileType.MANIFEST.hasExtension(path)) {
                    final Item<String> prevVal = basePathToManifestMap.put(
                            basePath,
                            new Item<>(path, ZipUtil.getEntryContent(zipFile, entry)));
                    if (prevVal != null) {
                        throw new RuntimeException("Duplicate manifest entry for basePath " + basePath);
                    }
                } else if (StroomZipFileType.META.hasExtension(path)) {
                    try {
                        try (final InputStream inputStream = zipFile.getInputStream(entry)) {
                            final AttributeMap attributeMap = new AttributeMap();
                            AttributeMapUtil.read(inputStream, attributeMap);
                            final Item<AttributeMap> prevVal = basePathToMetaMap.put(basePath,
                                    new Item<>(path, attributeMap));
                            if (prevVal != null) {
                                throw new RuntimeException("Duplicate meta entry for basePath " + basePath);
                            }
                        }
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else if (StroomZipFileType.DATA.hasExtension(path)
                           || !path.getFileName().toString().contains(".")) {
                    // Also allow entries with no extension
                    final Item<String> prevVal = basePathToDataMap.put(
                            basePath,
                            new Item<>(path, ZipUtil.getEntryContent(zipFile, entry)));
                    if (prevVal != null) {
                        throw new RuntimeException("Duplicate data entry for basePath " + basePath);
                    }
                } else {
                    throw new RuntimeException("Unexpected entry: " + path);
                }
                paths.add(Path.of(entry.getName()));
            });

            final List<Path> basePaths = Stream.of(
                            basePathToDataMap,
                            basePathToContextMap,
                            basePathToManifestMap,
                            basePathToMetaMap)
                    .map(Map::keySet)
                    .flatMap(Set::stream)
                    .distinct()
                    .sorted()
                    .toList();

            final List<ItemGroup> itemGroups = basePaths.stream()
                    .map(basePath ->
                            new ItemGroup(
                                    basePath,
                                    basePathToDataMap.get(basePath),
                                    basePathToContextMap.get(basePath),
                                    basePathToMetaMap.get(basePath),
                                    basePathToManifestMap.get(basePath)))
                    .toList();

            return new ProxyZipSnapshot(paths, itemGroups);
        }

        @Override
        public String toString() {
            return itemGroups.stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining("\n"));
        }
    }


    // --------------------------------------------------------------------------------


    public record ItemGroup(Path basePath,
                            Item<String> data,
                            Item<String> context,
                            Item<AttributeMap> meta,
                            Item<String> manifest) {

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb
                    .append("base name: ")
                    .append(basePath);
            if (meta != null) {
                sb
                        .append("\n")
                        .append(meta.path)
                        .append(" (Meta)")
                        .append("\n")
                        .append(meta.content.entrySet()
                                .stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
            }
            if (data != null) {
                sb
                        .append("\n")
                        .append(data.path)
                        .append(" (Data)")
                        .append("\nvvvvvvvvvvvvvvvvv")
                        .append("\n")
                        .append(data.content)
                        .append("\n^^^^^^^^^^^^^^^^^");
            }
            if (context != null) {
                sb
                        .append("\n")
                        .append(context.path)
                        .append(" (Context)")
                        .append("\nvvvvvvvvvvvvvvvvv")
                        .append("\n")
                        .append(context.content)
                        .append("\n^^^^^^^^^^^^^^^^^");
            }
            if (manifest != null) {
                sb
                        .append("\n")
                        .append(manifest.path)
                        .append(" (Manifest)")
                        .append("\nvvvvvvvvvvvvvvvvv")
                        .append("\n")
                        .append(manifest.content)
                        .append("\n^^^^^^^^^^^^^^^^^");
            }
            return sb.toString();
        }
    }


    // --------------------------------------------------------------------------------


    public record Item<T>(Path path, T content) {

    }
}
