package stroom.config.app;

import io.dropwizard.configuration.FileConfigurationSourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what happens to a config key that has been renamed.
 *
 * <h3>Why these go through the config file rather than through {@code AppConfig}</h3>
 * <p>The test this replaces asserted a {@code @JsonAlias} on {@code AppConfig} directly, and said
 * so: <em>"which is why these tests assert the alias directly rather than going through a config
 * file"</em>. It passed for as long as the alias existed — and the alias never worked. Reading
 * {@code AppConfig} straight from JSON skips
 * {@link StroomConfigurationSourceProvider#open(String)}, which merges the compiled defaults into
 * the operator's YAML under the <b>new</b> name before Dropwizard parses it. Since an alias is only
 * another spelling of the same property, last-one-wins gave the injected default the win and the
 * operator's value was silently discarded on every boot.</p>
 *
 * <p>So the tests here start from a <b>file</b> and assert the <b>outcome</b>. A test that pins the
 * annotation cannot see a defect that lives in a step running before the annotation is
 * consulted.</p>
 */
class TestRenamedConfigKeys {

    @TempDir
    Path tempDir;

    @Test
    void testOldAssetKeyIsRejectedAndNamesItsReplacement() throws IOException {
        final Path file = write("""
                appConfig:
                  visualisationAsset:
                    maxUploadSize: "1M"
                """);

        assertThatThrownBy(() -> open(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("appConfig.visualisationAsset")
                .hasMessageContaining("appConfig.documentAsset")
                .hasMessageContaining("Rename");
    }

    @Test
    void testOldAssetDbKeyIsRejectedAndNamesItsReplacement() throws IOException {
        final Path file = write("""
                appConfig:
                  visualisationAssetDb:
                    connection:
                      jdbcDriverUrl: "jdbc:mysql://legacy:3306/stroom"
                """);

        assertThatThrownBy(() -> open(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("appConfig.visualisationAssetDb")
                .hasMessageContaining("appConfig.documentAssetDb");
    }

    /** Both wrong is one error listing both, not the first one found. */
    @Test
    void testBothOldKeysAreReportedTogether() throws IOException {
        final Path file = write("""
                appConfig:
                  visualisationAsset:
                    maxUploadSize: "1M"
                  visualisationAssetDb:
                    connection: {}
                """);

        assertThatThrownBy(() -> open(file))
                .hasMessageContaining("appConfig.visualisationAsset'")
                .hasMessageContaining("appConfig.visualisationAssetDb'");
    }

    /**
     * The current name opens, and <b>the value survives to the running config</b>.
     *
     * <p>This is the assertion whose absence let the alias defect hide for a fortnight: the old
     * test proved a key was <em>accepted</em>, never that its value <em>arrived</em>. Those look
     * identical from outside and differ by everything.</p>
     */
    @Test
    void testCurrentNameApplies() throws IOException {
        final Path file = write("""
                appConfig:
                  documentAsset:
                    maxUploadSize: "1M"
                """);

        final AppConfig appConfig = StroomYamlUtil.readAppConfig(file);

        assertThat(appConfig.getDocumentAsset().getMaxUploadSize().getBytes())
                .isEqualTo(1024L * 1024L);
    }

    /** And with nothing set, the compiled default applies rather than nothing at all. */
    @Test
    void testDefaultAppliesWhenUnset() throws IOException {
        final Path file = write("""
                appConfig:
                  node:
                    name: "test"
                """);

        assertThat(StroomYamlUtil.readAppConfig(file).getDocumentAsset().getMaxUploadSize())
                .isNotNull();
    }

    private Path write(final String yaml) throws IOException {
        final Path file = tempDir.resolve("config.yml");
        Files.writeString(file, yaml);
        return file;
    }

    private void open(final Path file) throws IOException {
        try (final InputStream in = StroomYamlUtil
                .createConfigurationSourceProvider(new FileConfigurationSourceProvider(), false)
                .open(file.toAbsolutePath().toString())) {
            in.readAllBytes();
        }
    }
}
