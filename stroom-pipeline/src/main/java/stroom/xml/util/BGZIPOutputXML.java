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

package stroom.xml.util;

import stroom.entity.server.util.XMLUtil;
import stroom.streamstore.server.fs.BlockGZIPInputStream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BGZIPOutputXML {
    public static void main(String[] args) throws Exception {
        String fileName = args[1];
        InputStream inputStream = new BlockGZIPInputStream(Files.newInputStream(Paths.get(fileName)));
        XMLUtil.prettyPrintXML(inputStream, System.out);
    }
}
