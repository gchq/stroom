package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoTestModule;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestForwardDestDao {

    @Inject
    private ForwardDestDao forwardDestDao;

    @BeforeEach
    void beforeEach() {
        forwardDestDao.clear();
    }

    @Test
    void testForwardUrlDao() {
        assertThat(forwardDestDao.getForwardDestId("test")).isOne();
        assertThat(forwardDestDao.getAllForwardDests().size()).isOne();
    }
}
