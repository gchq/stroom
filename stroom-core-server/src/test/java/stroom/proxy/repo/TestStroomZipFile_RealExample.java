package stroom.proxy.repo;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class TestStroomZipFile_RealExample {
    @Test
    public void testRealZip1() throws IOException {
        File sourceFile = new File("./src/test/resources/stroom/proxy/repo/BlankZip.zip");
        StroomZipFile stroomZipFile = new StroomZipFile(sourceFile.toPath());

        ArrayList<String> list = new ArrayList<>(stroomZipFile.getStroomZipNameSet().getBaseNameList());
        Collections.sort(list);

        stroomZipFile.close();
    }
}
