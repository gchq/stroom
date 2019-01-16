package stroom.util.xml;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class ParserConfig implements IsConfig {
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
