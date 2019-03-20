package stroom.core.db.migration._V07_00_00.util.xml;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class _V07_00_00_ParserConfig {
    static boolean secureProcessing = true;

    @JsonPropertyDescription("Instructs the implementation to process XML securely. This may set limits on XML constructs to avoid conditions such as denial of service attacks.")
    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    public void setSecureProcessing(final boolean secureProcessing) {
        this.secureProcessing = secureProcessing;
    }

    @Override
    public String toString() {
        return "ParserConfig{" +
                "secureProcessing=" + secureProcessing +
                '}';
    }
}
