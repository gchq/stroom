/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.impl;

import stroom.credentials.shared.AwsKeySecret;

/// Everything needed to run a query on Amazon Athena, with defaults applied and the credential
/// looked up.
///
/// @param region             The AWS region, such as `eu-west-2`.
/// @param workGroup          The Athena workgroup.
/// @param catalog            The data catalog.
/// @param database           The database queries run in.
/// @param outputLocation     The S3 URI results are written to, or `null` for the workgroup's.
/// @param resultReuseMinutes How old a previous result may be and still be reused, or 0 for never.
/// @param awsKey             A static AWS access key, or `null` to use Stroom's own AWS identity
///                           from the default credentials chain.
public record ResolvedAthenaConnection(String region,
                                       String workGroup,
                                       String catalog,
                                       String database,
                                       String outputLocation,
                                       int resultReuseMinutes,
                                       AwsKeySecret awsKey) {

    // The secret access key is left out.
    @Override
    public String toString() {
        return "ResolvedAthenaConnection{" +
               "region='" + region + '\'' +
               ", workGroup='" + workGroup + '\'' +
               ", catalog='" + catalog + '\'' +
               ", database='" + database + '\'' +
               ", outputLocation='" + outputLocation + '\'' +
               ", resultReuseMinutes=" + resultReuseMinutes +
               ", awsKey=" + awsKey +
               '}';
    }
}
