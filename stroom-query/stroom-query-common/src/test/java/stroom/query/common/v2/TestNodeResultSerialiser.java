package stroom.query.common.v2;

import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestNodeResultSerialiser {

    @Mock
    Coprocessors mockCoprocessors;

    @Mock
    Output mockOutput;

    @Spy
    ErrorConsumer errorConsumer = new ErrorConsumerImpl();

    @Captor
    ArgumentCaptor<String> messageCaptor;

    final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void read() {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final Output output = new Output(outputStream)) {
            output.writeBoolean(true);
            // No need to write the payloads as the coprocessors are mocked and won't read from the input
            output.writeInt(5);
            output.writeString("""
                {"severity": "WARNING", "message": "Truncating string to 10 characters: fuga bland"}""");
            output.writeString("""
                {"severity": "ERROR", "message": "this is an Error"}""");
            output.writeString("""
                {"severity": "WARN", "message": "WARN does not exist."}""");
            output.writeString("""
                {"message": "This is also an error"}""");
            output.writeString("""
                {"message": "There is no severity", "node": "node2"}""");
        }

        final byte[] bytes = outputStream.toByteArray();
        try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
            NodeResultSerialiser.read(input, mockCoprocessors, errorConsumer);
        }

        final List<ErrorMessage> errors = errorConsumer.getErrorMessages();

        assertThat(errors).containsExactlyInAnyOrderElementsOf(List.of(
                new ErrorMessage(Severity.ERROR, "this is an Error"),
                new ErrorMessage(Severity.WARNING, "Truncating string to 10 characters: fuga bland"),
                new ErrorMessage(Severity.ERROR, "{\"severity\": \"WARN\", \"message\": \"WARN does not exist.\"}"),
                new ErrorMessage(Severity.ERROR, "This is also an error"),
                new ErrorMessage(Severity.ERROR, "There is no severity", "node2")
        ));
    }

    @Test
    void write() throws Exception {
        final List<ErrorMessage> errors = List.of(
                new ErrorMessage(Severity.ERROR, "this is an Error"),
                new ErrorMessage(Severity.WARNING, "Truncating string to 10 characters: fuga bland", "node1")
        );

        NodeResultSerialiser.write(mockOutput, true, mockCoprocessors, errors);

        Mockito.verify(mockOutput).writeBoolean(true);
        Mockito.verify(mockOutput).writeInt(2);
        Mockito.verify(mockOutput, Mockito.times(2)).writeString(messageCaptor.capture());

        final List<String> messages = messageCaptor.getAllValues();

        assertThat(objectMapper.readTree(messages.get(0))).isEqualTo(objectMapper.readTree("""
            {"severity": "ERROR", "message": "this is an Error"}"""));
        assertThat(objectMapper.readTree(messages.get(1))).isEqualTo(objectMapper.readTree("""
            {"node":"node1", "severity":"WARNING", "message": "Truncating string to 10 characters: fuga bland"}"""));
    }
}
