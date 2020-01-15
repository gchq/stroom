package stroom.util.io;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;

@Singleton
public class PathConfig extends AbstractConfig {
    private String temp = "/tmp";//System.getProperty("java.io.tmpdir");

    @ReadOnly
    @JsonPropertyDescription("Temp folder to write stuff to. Should only be set per node in application property file")
    public String getTemp() {
        return temp;
    }

    @JsonIgnore
    public Path getTempPath() {
        return Paths.get(temp);
    }

    public void setTemp(final String temp) {
        this.temp = temp;

        // Use the configured path in FileUtil also
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
