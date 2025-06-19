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

package stroom.util.io;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

/**
 * Utility to sum lines in a file
 */
public class LineSum {

    public static void main(final String[] args) throws IOException {
        final LineNumberReader lineNumberReader = new LineNumberReader(
                new InputStreamReader(System.in, StreamUtil.DEFAULT_CHARSET));

        String line;
        long total = 0;

        while ((line = lineNumberReader.readLine()) != null) {
            total += Long.parseLong(line);
        }

        System.out.println(total);

    }

}
