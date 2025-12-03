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

package stroom.core.tools;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class BackupConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupConfig.class);

    public static void main(final String[] args) throws IOException {
        Integer backUpDays = null;
        if (args.length > 0) {
            backUpDays = Integer.parseInt(args[0]);
        }
        final ArrayList<String> backUpPaths = new ArrayList<>();
        if (backUpDays != null) {
            final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
            final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            for (int i = 0; i < backUpDays; i++) {
                backUpPaths.add(simpleDateFormat.format(calendar.getTime()));
                calendar.add(Calendar.DATE, -1);
            }
        }

        try (final LineNumberReader inputStreamReader = new LineNumberReader(
                new InputStreamReader(System.in, StreamUtil.DEFAULT_CHARSET));
                final PrintWriter printWriter = new PrintWriter(
                        new OutputStreamWriter(System.out, StreamUtil.DEFAULT_CHARSET))) {
            String line = null;
            while ((line = inputStreamReader.readLine()) != null) {
                final String[] parts = line.split("\\s+");

                if (parts.length != 2) {
                    printWriter.println(line);
                } else {
                    final String source = parts[0];
                    final String target = parts[1];

                    if (backUpPaths.size() > 0 && source.endsWith("/store/")) {
                        final Path sourceDir = Paths.get(source);
                        final Path targetDir = Paths.get(target);

                        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
                            stream.forEach(file -> {
                                try {
                                    for (final String backupPath : backUpPaths) {
                                        final Path sourcePath = file.resolve(backupPath);
                                        if (Files.isDirectory(sourcePath)) {
                                            final Path targetPath = targetDir.resolve(backupPath);
                                            printWriter.println(
                                                    FileUtil.getCanonicalPath(sourcePath) +
                                                            "/\t" +
                                                            FileUtil.getCanonicalPath(targetPath) +
                                                            "/");
                                        }
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e.getMessage(), e);
                                }
                            });
                        } catch (final IOException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    } else {
                        printWriter.println(source + "./\t" + target);
                    }
                }
            }
        }
    }
}
