package stroom.search;

import stroom.util.test.data.FlatDataWriterBuilder;
import stroom.util.test.data.TestDataGenerator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class NetworkMonitoringDataGenerator {

    //Date, Time, EventType, Device, UserName, ID, ErrorCode, IPAddress, Server, Message
    //18/12/2007,13:21:48,authorisationFailed,device4,user5,192.168.0.2,E0567,192.168.0.3,server4,Another message that I made up 2

    public static void main(String[] args) {
        LocalDateTime startInc = LocalDateTime.of(2016, 1, 1, 0, 0, 0);
        LocalDateTime endExc = LocalDateTime.of(2018, 1, 1, 0, 0, 0);

        TestDataGenerator.buildDefinition()
                .addFieldDefinition(TestDataGenerator.randomDateTimeField(
                        "Date",
                        startInc,
                        endExc,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .addFieldDefinition(TestDataGenerator.randomDateTimeField(
                        "Time",
                        startInc,
                        endExc,
                        DateTimeFormatter.ofPattern("HH:mm:ss")))
                .addFieldDefinition(TestDataGenerator.randomValueField(
                        "EventType",
                        Arrays.asList(
                                "authenticationFailed",
                                "authorisationFailed",
                                "login")))
                .addFieldDefinition(TestDataGenerator.randomNumberedValueField(
                        "Device",
                        "device%s",
                        100))
                .addFieldDefinition(TestDataGenerator.randomNumberedValueField(
                        "UserName",
                        "user-%s",
                        100))
                .addFieldDefinition(TestDataGenerator.randomIpV4Field("ID"))
                .addFieldDefinition(TestDataGenerator.randomNumberedValueField(
                        "ErrorCode",
                        "E%03d",
                        1000))
                .addFieldDefinition(TestDataGenerator.randomIpV4Field("IPAddress"))
                .addFieldDefinition(TestDataGenerator.randomNumberedValueField(
                        "Server",
                        "server-%s",
                        100))
                .addFieldDefinition(TestDataGenerator.randomClassNamesField(
                        "Message",
                        0,
                        3))
                .setDataWriter(FlatDataWriterBuilder.defaultCsvFormat())
                .rowCount(50)
                .consumedBy(TestDataGenerator.getSystemOutConsumer())
                .generate();
    }
}
