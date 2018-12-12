package stroom.proxy.repo;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

class TestStroomZipFile_RealExample {
    @Test
    void testRealZip1() throws IOException {
        final Path sourceFile = Paths.get("./src/test/resources/stroom/proxy/repo/BlankZip.zip");
        StroomZipFile stroomZipFile = new StroomZipFile(sourceFile);

        ArrayList<String> list = new ArrayList<>(stroomZipFile.getStroomZipNameSet().getBaseNameList());
        Collections.sort(list);

        stroomZipFile.close();
    }
}
