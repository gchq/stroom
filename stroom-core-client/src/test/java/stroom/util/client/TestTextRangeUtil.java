package stroom.util.client;

import stroom.explorer.shared.StringMatchLocation;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextRangeUtil {

    @Test
    void test() {
        final String data = """
                <?xml version="1.1" encoding="UTF-8"?>
                <pipeline>
                   <elements>
                      <add>
                         <element>
                            <id>Source</id>
                            <type>Source</type>
                         </element>
                         <element>
                            <id>streamAppender1</id>
                            <type>StreamAppender</type>
                         </element>
                         <element>
                            <id>streamAppender2</id>
                            <type>StreamAppender</type>
                         </element>
                         <element>
                            <id>streamAppender3</id>
                            <type>StreamAppender</type>
                         </element>
                      </add>
                   </elements>
                   <properties>
                      <add>
                         <property>
                            <element>streamAppender1</element>
                            <name>streamType</name>
                            <value>
                               <string>Test Events</string>
                            </value>
                         </property>
                         <property>
                            <element>streamAppender2</element>
                            <name>streamType</name>
                            <value>
                               <string>Test Events</string>
                            </value>
                         </property>
                         <property>
                            <element>streamAppender3</element>
                            <name>streamType</name>
                            <value>
                               <string>Test Events</string>
                            </value>
                         </property>
                      </add>
                   </properties>
                </pipeline>
                """;

        final List<StringMatchLocation> locations = new ArrayList<>();
        locations.add(new StringMatchLocation(725, 4));
        locations.add(new StringMatchLocation(934, 4));
        locations.add(new StringMatchLocation(1143, 4));

        final List<TextRange> textRanges = TextRangeUtil.convertMatchesToRanges(data, locations);

        assertThat(textRanges.size()).isEqualTo(locations.size());
        for (final TextRange textRange : textRanges) {
            assertThat(textRange.getFrom().getLineNo()).isEqualTo(textRange.getTo().getLineNo());
            assertThat(textRange.getFrom().getColNo()).isEqualTo(24);
            assertThat(textRange.getTo().getColNo()).isEqualTo(27);
        }
    }
}
