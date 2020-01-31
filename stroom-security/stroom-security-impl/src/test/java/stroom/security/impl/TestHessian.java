package stroom.security.impl;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import org.junit.jupiter.api.Test;
import stroom.security.api.UserIdentity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestHessian {
    @Test
    void testUserIdentity() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hessian2Output out = new Hessian2Output(baos);

        out.writeObject(ProcessingUserIdentity.INSTANCE);
        out.close();

        Hessian2Input in = new Hessian2Input(new ByteArrayInputStream(baos.toByteArray()));
        final Object o = in.readObject(UserIdentity.class);
        in.close();

        assertThat(ProcessingUserIdentity.INSTANCE).isEqualTo(o);
    }
}
