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

package stroom.data.store.impl.fs;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.AbstractCommandLineTool;
import stroom.util.ArgsUtil;
import stroom.util.io.AbstractFileVisitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Map;

class FileMetaGrep extends AbstractCommandLineTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileMetaGrep.class);
    private final Map<String, String> matchMap;
    private String[] repoPathParts = null;
    private String feedId;

    private FileMetaGrep(final String[] args) {
        matchMap = ArgsUtil.parse(args);
        matchMap.remove("repoPath");
        matchMap.remove("feedId");

        doMain(args);
    }

    public static void main(final String[] args) {
        new FileMetaGrep(args);
    }

    public void setRepoPath(final String repoPath) {
        this.repoPathParts = repoPath.split("/");
    }

    public void setFeedId(final String feedId) {
        this.feedId = feedId;
    }

    @Override
    public void run() {
        final StringBuilder path = new StringBuilder();

        for (final String part : repoPathParts) {
            if (part.contains("*")) {
                break;
            }
            path.append("/");
            path.append(part);
        }

        scanDir(Paths.get(path.toString()));
    }

    private void scanDir(final Path path) {
        try {
            Files.walkFileTree(path,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                if (matches(file.toAbsolutePath().normalize().toString())) {
                                    scanFile(file);
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.debug(e.getMessage(), e);
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private void scanFile(final Path file) {
        try {
            final String path = file.toAbsolutePath().normalize().toString();
            if (feedId != null) {
                if (!file.getFileName().toString().startsWith(feedId)) {
                    return;
                }
            }

            if (path.endsWith("meta.bgz")) {
                final String bdyPath = path.substring(0, path.length() - 4) + ".bdy.dat";
                int segment = 0;
                boolean done = false;
                while (!done) {
                    try (final RASegmentInputStream segmentInputStream = new RASegmentInputStream(
                            new BlockGZIPInputFile(file),
                            new UncompressedInputStream(Paths.get(bdyPath), true))) {

                        if (segmentInputStream.count() <= segment - 1) {
                            done = true;
                        } else {
                            segmentInputStream.include(segment);

                            final AttributeMap attributeMap = new AttributeMap();
                            AttributeMapUtil.read(segmentInputStream, attributeMap);

                            boolean match = true;

                            for (final String matchKey : matchMap.keySet()) {
                                if (!attributeMap.containsKey(matchKey)) {
                                    // No Good
                                    match = false;
                                } else {
                                    if (!attributeMap.get(matchKey).startsWith(matchMap.get(matchKey))) {
                                        // No Good
                                        match = false;
                                    }
                                }
                            }

                            if (match) {
                                // Found Match
                                System.out.println("Found Match in " + path + " at segment " + segment);
                                System.out.write(AttributeMapUtil.toByteArray(attributeMap));
                                System.out.println();
                            }

                            segment++;
                        }
                    }
                }
            }
        } catch (final IOException ioEx) {
            ioEx.printStackTrace();
        }
    }

    private boolean matches(final String path) {
        final String[] pathParts = path.split("/");

        for (int i = 0; (i < pathParts.length) && (i < repoPathParts.length); i++) {
            for (int c = 0; c < pathParts[i].length(); c++) {
                if (repoPathParts[i].charAt(c) == '*') {
                    break;
                }
                if (repoPathParts[i].charAt(c) != pathParts[i].charAt(c)) {
                    return false;
                }
            }
        }

        return true;
    }

}
