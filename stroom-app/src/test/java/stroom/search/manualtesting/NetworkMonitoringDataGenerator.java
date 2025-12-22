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

package stroom.search.manualtesting;


import stroom.test.common.data.DataGenerator;
import stroom.test.common.data.FlatDataWriterBuilder;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

@SuppressWarnings("checkstyle:LineLength")
public class NetworkMonitoringDataGenerator {

    //Date, Time, EventType, Device, UserName, ID, ErrorCode, IPAddress, Server, Message
    //18/12/2007,13:21:48,authorisationFailed,device4,user5,192.168.0.2,E0567,192.168.0.3,server4,Another message that I made up 2

    public static void generate(final int rowCount, final Path filePath) {

        final LocalDateTime startInc = LocalDateTime.of(
                2016, 1, 1, 0, 0, 0);
        final LocalDateTime endExc = LocalDateTime.of(
                2018, 1, 1, 0, 0, 0);

        DataGenerator.buildDefinition()
                .addFieldDefinition(DataGenerator.randomDateTimeField(
                        "Date",
                        startInc,
                        endExc,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)))
                .addFieldDefinition(DataGenerator.randomDateTimeField(
                        "Time",
                        startInc,
                        endExc,
                        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)))
                .addFieldDefinition(DataGenerator.randomValueField(
                        "EventType",
                        Arrays.asList("authenticationFailed", "authorisationFailed")))
                .addFieldDefinition(DataGenerator.randomNumberedValueField(
                        "Device",
                        "device%s",
                        20))
                .addFieldDefinition(DataGenerator.sequentiallyNumberedValueField(
                        "UserName",
                        "user-%s",
                        0,
                        20))
                .addFieldDefinition(DataGenerator.randomIpV4Field("ID"))
                .addFieldDefinition(DataGenerator.randomNumberedValueField(
                        "ErrorCode",
                        "E%03d",
                        1000))
                .addFieldDefinition(DataGenerator.randomIpV4Field("IPAddress"))
                .addFieldDefinition(DataGenerator.randomNumberedValueField(
                        "Server",
                        "server-%s",
                        20))
                .addFieldDefinition(DataGenerator.randomClassNamesField(
                        "Message",
                        0,
                        5))
                .setDataWriter(FlatDataWriterBuilder.defaultCsvFormat())
                .rowCount(rowCount)
                .consumedBy(DataGenerator.getFileOutputConsumer(filePath))
                .generate();
    }
}
