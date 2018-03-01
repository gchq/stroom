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

        TestDataFieldDefinition dateField = TestDataFieldDefinition.randomDateTime(
                "Date",
                startInc,
                endExc,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        TestDataFieldDefinition timeField = TestDataFieldDefinition.randomDateTime(
                "Time",
                startInc,
                endExc,
                DateTimeFormatter.ofPattern("HH:mm:ss"));

        TestDataFieldDefinition eventTypeField = TestDataFieldDefinition.randomValueField(
                "EventType",
                Arrays.asList(
                        "authenticationFailed",
                        "authorisationFailed",
                        "login"));

        TestDataFieldDefinition deviceField = TestDataFieldDefinition.randomNumberedValueField(
                "Device",
                "device%s",
                100);

        TestDataFieldDefinition userNameField = TestDataFieldDefinition.randomNumberedValueField(
                "UserName",
                "user-%s",
                100);

        TestDataFieldDefinition idField = TestDataFieldDefinition.randomIpV4Field("ID");

        TestDataFieldDefinition errorCodeField = TestDataFieldDefinition.randomNumberedValueField(
                "ErrorCode",
                "E%03d",
                1000);

        TestDataFieldDefinition ipAddressField = TestDataFieldDefinition.randomIpV4Field("IPAddress");

        TestDataFieldDefinition serverField = TestDataFieldDefinition.randomNumberedValueField(
                "Server",
                "server-%s",
                100);

        TestDataFieldDefinition messageField = TestDataFieldDefinition.randomWords(
                "Message",
                0,
                3);


        TestDataGenerator.buildDefinition()
                .addFieldDefinition(dateField)
                .addFieldDefinition(timeField)
                .addFieldDefinition(eventTypeField)
                .addFieldDefinition(deviceField)
                .addFieldDefinition(userNameField)
                .addFieldDefinition(idField)
                .addFieldDefinition(errorCodeField)
                .addFieldDefinition(ipAddressField)
                .addFieldDefinition(serverField)
                .addFieldDefinition(messageField)
                .outputHeaderRow(true)
                .rowCount(50)
                .delimitedBy(",")
                .consumedBy(TestDataGenerator.systemOutConsumer())
                .generate();
    }


}
