/*
 * Copyright 2016-2024 Crown Copyright
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
import stroom.util.shared.string.CIKey;

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
    private final Map<CIKey, String> matchMap;
    private String[] repoPathParts = null;
    private String feedId;

    private FileMetaGrep(String[] args) {
        matchMap = ArgsUtil.parse(args);
        matchMap.remove(CIKey.of("repoPath"));
        matchMap.remove(CIKey.of("feedId"));

        doMain(args);
    }

    public static void main(String[] args) {
        new FileMetaGrep(args);
    }

    public void setRepoPath(String repoPath) {
        this.repoPathParts = repoPath.split("/");
    }

    public void setFeedId(String feedId) {
        this.feedId = feedId;
    }

    @Override
    public void run() {
        StringBuilder path = new StringBuilder();

        for (String part : repoPathParts) {
            if (part.contains("*")) {
                break;
            }
            path.append("/");
            path.append(part);
        }

        scanDir(Paths.get(path.toString()));
    }

    private void scanDir(Path path) {
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

    private void scanFile(Path file) {
        try {
            String path = file.toAbsolutePath().normalize().toString();
            if (feedId != null) {
                if (!file.getFileName().toString().startsWith(feedId)) {
                    return;
                }
            }

            if (path.endsWith("meta.bgz")) {
                String bdyPath = path.substring(0, path.length() - 4) + ".bdy.dat";
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

                            AttributeMap attributeMap = new AttributeMap();
                            AttributeMapUtil.read(segmentInputStream, attributeMap);

                            boolean match = true;

                            for (CIKey matchKey : matchMap.keySet()) {
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
        } catch (IOException ioEx) {
            ioEx.printStackTrace();
        }
    }

    private boolean matches(String path) {
        String[] pathParts = path.split("/");

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
