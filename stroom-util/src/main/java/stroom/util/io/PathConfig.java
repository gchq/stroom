package stroom.util.io;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;

@Singleton
public class PathConfig implements IsConfig {
    private String temp;

    public PathConfig() {
        temp = FileUtil.getTempDir()
                .resolve("stroom")
                .toAbsolutePath()
                .toString();
    }

    @ReadOnly
    @JsonPropertyDescription("Temp folder to write stuff to. Should only be set per node in application property file")
    public String getTemp() {
        return temp;
    }

    public void setTemp(final String temp) {
        this.temp = temp;

        if (temp != null) {
            final Path tempDir = Paths.get(temp);
            FileUtil.setTempDir(tempDir);
        }
    }

    @Override
    public String toString() {
        return "PathConfig{" +
                "temp='" + temp + '\'' +
                '}';
    }
}
