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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class WrappedProperties {
    private final Properties properties = new Properties();

    public void setProperty(final String key, final String value) {
        if (key != null && value != null) {
            properties.setProperty(key, value);
        }
    }

    public String getProperty(final String key) {
        return properties.getProperty(key);
    }

    public void load(final File file) throws IOException {
        properties.load(new BufferedInputStream(new FileInputStream(file)));
    }

    public void save(final File file) throws IOException {
        properties.store(new BufferedOutputStream(new FileOutputStream(file)), null);
    }
}
