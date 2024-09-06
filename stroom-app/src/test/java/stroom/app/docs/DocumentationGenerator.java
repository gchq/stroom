package stroom.app.docs;

import io.github.classgraph.ScanResult;

public interface DocumentationGenerator {

    void generateAll(ScanResult scanResult);
}
