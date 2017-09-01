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

import stroom.util.io.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class BackupConfig {
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

        try (LineNumberReader inputStreamReader = new LineNumberReader(
                new InputStreamReader(System.in, StreamUtil.DEFAULT_CHARSET));
             PrintWriter printWriter = new PrintWriter(
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
                        final File sourceDir = new File(source);
                        for (final String child : sourceDir.list()) {
                            for (final String backupPath : backUpPaths) {
                                final File sourcePath = new File(source + child + "/./" + backupPath);
                                if (sourcePath.isDirectory()) {
                                    printWriter.println(
                                            source + child + "/./" + backupPath + "/\t" + target + child + "/");
                                }
                            }
                        }
                    } else {
                        printWriter.println(source + "./\t" + target);
                    }
                }
            }
        }
    }
}
