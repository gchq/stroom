package stroom.importexport.impl;

import stroom.importexport.api.ImportExportSpec;
import stroom.importexport.api.ImportExportSpec.ImportExportCaller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the method to adjust legacy content packs when imported by a GitRepo.
 */
public class TestCollapseRoot {

    @Disabled("Broken at the moment - needs better export structures")
    @Test
    void testCollapseRootOneLevel() {
        final String rootPath = "XML Schemas/core";
        final String path = "XML Schemas/analytic-output v1.0";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("analytic-output v1.0");
    }

    @Test
    void testCollapseRootTwoLevels() {
        final String rootPath = "XML Schemas/event-logging";
        final String path = "XML Schemas/event-logging/event-logging v3.0.0";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("event-logging v3.0.0");
    }

    @Test
    void testMovedRoot() {
        final String rootPath = "Content Packs/XML Schemas/event-logging";
        final String path = "XML Schemas/event-logging/event-logging v3.0.0";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("event-logging v3.0.0");
    }

    @Test
    void testABC() {
        final String rootPath = "A/B/C";
        final String path = "C/D/E";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("D/E");
    }

    @Test
    void testABCD() {
        final String rootPath = "A/B/C/D";
        final String path = "C/D/E";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("E");
    }

    @Test
    void testNoOverlap() {
        final String rootPath = "A/B/C/D";
        final String path = "E/F/G";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("E/F/G");
    }

    @Test
    void testEmptyImport() {
        final String rootPath = "A/B/C/D";
        final String path = "";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("");
    }

    @Test
    void testEmptyElements() {
        final String rootPath = "///";
        final String path = "//";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("//");
    }

    @Test
    void testInternalStatisticsSql() {
        final String rootPath = "Internal Statistics/SQL";
        final String path = "Internal Statistics/SQL/Benchmark";
        final ImportExportCaller reader = ImportExportCaller.GITREPO;
        final ImportExportSpec spec = ImportExportSpec.buildBackCompatible();

        final String collapsed = ImportExportSerializerImpl.collapseRootIfNecessary(rootPath, path, reader, spec);
        assertThat(collapsed).isEqualTo("Benchmark");
    }

}
