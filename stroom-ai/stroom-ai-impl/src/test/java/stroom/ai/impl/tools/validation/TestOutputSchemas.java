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

package stroom.ai.impl.tools.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestOutputSchemas {

    private static final String RECORDS_ID = "file://records-v2.0.xsd";
    private static final String REFERENCE_ID = "file://reference-data-v2.0.1.xsd";
    private static final String KAFKA_ID = "file://kafka-records-v1.1.xsd";

    private final HeldSchemasWorld world = new HeldSchemasWorld()
            .hold(RECORDS_ID, "records:2", HeldSchemasWorld.resource("records-v2.0.xsd"), "RECORDS")
            .hold(REFERENCE_ID, "reference-data:2", HeldSchemasWorld.resource("reference-data-v2.0.1.xsd"),
                    "REFERENCE_DATA")
            .hold(KAFKA_ID, "kafka-records:1", HeldSchemasWorld.resource("kafka-records-v1.1.xsd"),
                    "KAFKA_RECORDS");
    private final OutputSchemas outputSchemas = new OutputSchemas(world.heldSchemas());

    @Test
    void validRecordsPass() {
        assertThat(outputSchemas.check("""
                <records xmlns="records:2" version="2.0">
                  <record><data name="UserId" value="alice"/></record>
                </records>""")).isEmpty();
    }

    @Test
    void recordsInTheWrongShapeFailTheRecordsSchema() {
        // As the hand-written Data Splitter did, an indexing XSLT naming its fields with elements the
        // IndexingFilter doesn't read: it would index no fields, with no error.
        final Optional<OutputSchemas.Failed> failed = outputSchemas.check("""
                <records xmlns="records:2" version="2.0">
                  <record><entry name="UserId">alice</entry></record>
                </records>""");

        assertThat(failed).hasValueSatisfying(it -> {
            assertThat(it.namespace()).isEqualTo("records:2");
            assertThat(it.systemId()).isEqualTo(RECORDS_ID);
            assertThat(it.errors().getFirst().message()).contains("entry");
            assertThat(it.errors().getFirst().line()).isEqualTo(2);
        });
    }

    @Test
    void referenceDataWithoutAValueFailsItsSchema() {
        assertThat(outputSchemas.check("""
                <referenceData xmlns="reference-data:2" version="2.0.1">
                  <reference><map>USER</map><key>alice</key><value>Alice</value></reference>
                </referenceData>""")).isEmpty();

        assertThat(outputSchemas.check("""
                <referenceData xmlns="reference-data:2" version="2.0.1">
                  <reference><map>USER</map><key>alice</key></reference>
                </referenceData>"""))
                .hasValueSatisfying(it -> assertThat(it.systemId()).isEqualTo(REFERENCE_ID));
    }

    @Test
    void aKafkaRecordWithoutItsTopicFailsItsSchema() {
        assertThat(outputSchemas.check("""
                <kafkaRecords xmlns="kafka-records:1">
                  <kafkaRecord topic="events"><value>CEF:0|Acme</value></kafkaRecord>
                </kafkaRecords>""")).isEmpty();

        assertThat(outputSchemas.check("""
                <kafkaRecords xmlns="kafka-records:1">
                  <kafkaRecord><value>CEF:0|Acme</value></kafkaRecord>
                </kafkaRecords>"""))
                .hasValueSatisfying(it -> assertThat(it.errors().getFirst().message()).contains("topic"));
    }

    @Test
    void otherNamespacesAreNotChecked() {
        // Events are check_events', and JSON is checked by the schema filter of every Elasticsearch template.
        assertThat(outputSchemas.check("<Events xmlns=\"event-logging:3\"><Junk/></Events>")).isEmpty();
        assertThat(outputSchemas.check("<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><junk/></array>"))
                .isEmpty();
        assertThat(outputSchemas.check("<records><entry/></records>")).isEmpty();
    }

    @Test
    void outputThatIsNotWellFormedOrEmptyIsLeftToStepping() {
        assertThat(outputSchemas.check("<records xmlns=\"records:2\"><record>")).isEmpty();
        assertThat(outputSchemas.check("")).isEmpty();
        assertThat(outputSchemas.check(null)).isEmpty();
    }

    @Test
    void aNamespaceStroomHoldsNoSchemaForIsNotChecked() {
        final OutputSchemas none = new OutputSchemas(new HeldSchemasWorld().heldSchemas());

        assertThat(none.check("<records xmlns=\"records:2\"><entry/></records>")).isEmpty();
    }

    @Test
    void theSchemaTheOutputNamesIsUsedWhenHeldForItsNamespaceElseTheNewest() {
        final String newer = "file://records-v2.1.xsd";
        final OutputSchemas two = new OutputSchemas(new HeldSchemasWorld()
                .hold(RECORDS_ID, "records:2", HeldSchemasWorld.resource("records-v2.0.xsd"))
                .hold(newer, "records:2", HeldSchemasWorld.resource("records-v2.0.xsd"))
                .heldSchemas());
        final String invalid = "<records xmlns=\"records:2\"%s><entry/></records>";
        final String named = " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                             + "xsi:schemaLocation=\"records:2 " + RECORDS_ID + "\"";

        assertThat(two.check(invalid.formatted(named)).map(OutputSchemas.Failed::systemId)).contains(RECORDS_ID);
        assertThat(two.check(invalid.formatted("")).map(OutputSchemas.Failed::systemId)).contains(newer);
    }

    @Test
    void systemIdsAreOrderedByTheirNumbers() {
        assertThat(HeldSchemas.newestSystemIdsFirst(List.of("file://records-v2.0.xsd", "file://records-v10.0.xsd",
                "file://records-v2.1.xsd", "file://records-v2.0.xsd")))
                .containsExactly("file://records-v10.0.xsd", "file://records-v2.1.xsd", "file://records-v2.0.xsd");
    }

    @Test
    void theRootNamespaceIsReadFromTheFirstElement() {
        assertThat(OutputSchemas.rootNamespace("<?xml version=\"1.0\"?><!-- c --><r:records xmlns:r=\"records:2\"/>"))
                .isEqualTo("records:2");
        assertThat(OutputSchemas.rootNamespace("plain text")).isNull();
    }
}
