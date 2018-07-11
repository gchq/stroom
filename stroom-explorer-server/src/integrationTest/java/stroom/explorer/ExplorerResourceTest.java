package stroom.explorer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

public class ExplorerResourceTest {

    private static final String TEST_MOVE_OP = "{\"docRefs\":[{\"uuid\":\"72171ccc-b8b4-4a1d-91fb-974bd2bac1c2\",\"type\":\"Feed\",\"name\":\"JOE_HDFS_OUT\"}],\"destinationFolderRef\":{\"uuid\":\"4fa7d92d-cff9-445d-aef5-29e65e77bebc\"},\"permissionInheritance\":\"DESTINATION\"}";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testMoveOp() {
        try {
            final ExplorerResource.MoveOp moveOp = objectMapper.readValue(TEST_MOVE_OP, ExplorerResource.MoveOp.class);

            System.out.println(moveOp);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
