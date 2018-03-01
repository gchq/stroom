package stroom.search;

import stroom.test.TestDataFieldDefinition;
import stroom.test.TestDataGenerator;

import java.time.LocalDate;
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
                .addFieldDefinition(TestDataFieldDefinition.randomDateTime(
                        "Date",
                        startInc,
                        endExc,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .addFieldDefinition(TestDataFieldDefinition.randomDateTime(
                        "Time",
                        startInc,
                        endExc,
                        DateTimeFormatter.ofPattern("HH:mm:ss")))
                .addFieldDefinition(TestDataFieldDefinition.randomValueField(
                        "EventType",
                        Arrays.asList(
                                "authenticationFailed",
                                "authorisationFailed",
                                "login")))
                .addFieldDefinition(TestDataFieldDefinition.randomNumberedValueField(
                        "Device",
                        "device%s",
                        100))
                .addFieldDefinition(TestDataFieldDefinition.randomNumberedValueField(
                        "UserName",
                        "user-%s",
                        100))
                .addFieldDefinition(TestDataFieldDefinition.randomIpV4Field("ID"))
                .addFieldDefinition(TestDataFieldDefinition.randomNumberedValueField(
                        "ErrorCode",
                        "E%03d",
                        1000))
                .addFieldDefinition(TestDataFieldDefinition.randomIpV4Field("IPAddress"))
                .addFieldDefinition(TestDataFieldDefinition.randomNumberedValueField(
                        "Server",
                        "server-%s",
                        100))
                .addFieldDefinition(TestDataFieldDefinition.randomClassNames(
                        "Message",
                        0,
                        3))
                .outputHeaderRow(true)
                .rowCount(50)
                .delimitedBy(",")
                .consumedBy(TestDataGenerator.systemOutConsumer())
                .generate();
    }


}
