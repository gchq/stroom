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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

public class Log4JGrep {
    public static void main(String[] args) throws IOException {
        new Log4JGrep().doMain(System.in, args);

    }

    public void doMain(InputStream is, String[] args) throws IOException {
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(is));
        int startPos = Integer.parseInt(args[0]);

        String line;

        StringBuilder builder = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            String upperLine = line.toUpperCase();
            builder.setLength(0);
            builder.append(line.substring(0, startPos));

            for (int i = 1; i < args.length; i++) {
                builder.append(",");
                String upperMatch = args[i].toUpperCase();
                int pos = upperLine.indexOf(upperMatch);
                if (pos > 0) {
                    for (int lp = pos; lp < line.length(); lp++) {
                        char c = line.charAt(lp);
                        if (c == ',' || c == ' ') {
                            break;
                        }
                        builder.append(c);
                    }
                }
            }
            System.out.println(builder.toString());
        }

    }
}
