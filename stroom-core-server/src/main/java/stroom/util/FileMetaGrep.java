/*
 * Copyright 2016 Crown Copyright
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

package stroom.util;

import java.io.File;
import java.io.IOException;

import stroom.streamstore.server.fs.BlockGZIPInputFile;
import stroom.streamstore.server.fs.UncompressedInputStream;
import stroom.streamstore.server.fs.serializable.RANestedInputStream;
import stroom.util.zip.HeaderMap;

public class FileMetaGrep extends AbstractCommandLineTool {
    private String[] repoPathParts = null;
    HeaderMap matchMap;
    private String feedId;

    public void setRepoPath(String repoPath) {
        this.repoPathParts = repoPath.split("/");
    }

    public void setFeedId(String feedId) {
        this.feedId = feedId;
    }

    public FileMetaGrep(String[] args) throws Exception {
        matchMap = new HeaderMap();
        matchMap.loadArgs(args);
        matchMap.remove("repoPath");
        matchMap.remove("feedId");

        doMain(args);
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

        scanDir(new File(path.toString()));
    }

    public void scanDir(File root) {
        String kids[] = root.list();

        if (kids != null) {
            for (String kid : kids) {
                File kidFile = new File(root, kid);
                if (matches(kidFile.getAbsolutePath())) {
                    if (kidFile.isDirectory()) {
                        scanDir(kidFile);
                    } else {
                        scanFile(kidFile);
                    }
                }
            }
        }
    }

    public void scanFile(File file) {
        try {
            String path = file.getAbsolutePath();
            if (feedId != null) {
                if (!file.getName().startsWith(feedId)) {
                    return;
                }
            }

            if (path.endsWith("meta.bgz")) {
                String bdyPath = path.substring(0, path.length() - 4) + ".bdy.dat";
                RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(file),
                        new UncompressedInputStream(new File(bdyPath), true));
                int segment = 0;
                while (nestedInputStream.getNextEntry()) {
                    segment++;

                    HeaderMap headerMap = new HeaderMap();
                    headerMap.read(nestedInputStream, false);
                    nestedInputStream.closeEntry();

                    boolean match = true;

                    for (String matchKey : matchMap.keySet()) {
                        if (!headerMap.containsKey(matchKey)) {
                            // No Good
                            match = false;
                        } else {
                            if (!headerMap.get(matchKey).startsWith(matchMap.get(matchKey))) {
                                // No Good
                                match = false;
                            }
                        }
                    }

                    if (match) {
                        // Found Match
                        System.out.println("Found Match in " + path + " at segment " + segment);
                        System.out.write(headerMap.toByteArray());
                        System.out.println();
                    }

                }
                nestedInputStream.close();
            }
        } catch (IOException ioEx) {
            ioEx.printStackTrace();
        }
    }

    public boolean matches(String path) {
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

    public static void main(String[] args) throws Exception {
        new FileMetaGrep(args);
    }

}
