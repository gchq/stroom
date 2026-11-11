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

package stroom.externaldatasource.impl;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;

/// Makes Amazon Athena clients with the AWS SDK.
///
/// A static access key from a Credential is used if the document names one. Otherwise the default
/// credentials chain gives Stroom's own AWS identity: environment variables, system properties, web
/// identity, the shared profile file or the instance or container role.
class AthenaClientFactoryImpl implements AthenaClientFactory {

    @Override
    public AthenaClient create(final ResolvedAthenaConnection connection) {
        return AthenaClient.builder()
                .region(Region.of(connection.region()))
                .credentialsProvider(credentialsProvider(connection))
                // Several HTTP clients are on Stroom's class path, so name the one to use.
                .httpClientBuilder(Apache5HttpClient.builder())
                .build();
    }

    private static AwsCredentialsProvider credentialsProvider(final ResolvedAthenaConnection connection) {
        if (connection.awsKey() == null) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                connection.awsKey().getAccessKeyId(),
                connection.awsKey().getSecretAccessKey()));
    }
}
