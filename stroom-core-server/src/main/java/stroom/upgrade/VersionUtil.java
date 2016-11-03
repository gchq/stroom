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

package stroom.upgrade;

import stroom.util.shared.Version;

public class VersionUtil {
    public static Version parseVersion(String buildVersion) {
        if (buildVersion == null) {
            return null;
        }
        try {
            buildVersion = buildVersion.replaceAll("\\D+", " ").trim();
            String[] versionParts = buildVersion.split(" ");
            Version version = new Version();
            if (versionParts.length > 0) {
                version.setMajor(Integer.parseInt(versionParts[0]));
            }
            if (versionParts.length > 1) {
                version.setMinor(Integer.parseInt(versionParts[1]));
            } else {
                version.setMinor(0);
            }
            if (versionParts.length > 2) {
                version.setPatch(Integer.parseInt(versionParts[2]));
            } else {
                version.setPatch(0);
            }
            return version;
        } catch (Exception ex) {
            return null;
        }
    }

}
