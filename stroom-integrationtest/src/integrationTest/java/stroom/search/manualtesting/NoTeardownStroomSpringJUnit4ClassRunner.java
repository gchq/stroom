package stroom.search.manualtesting;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import stroom.util.logging.LogExecutionTime;
import stroom.util.task.TaskScopeContextHolder;

/**
 * This a copy of the {@link stroom.util.test.StroomSpringJUnit4ClassRunner} but without the overridden
 * run method as we don't want to delete out temp directory state
 */
public class NoTeardownStroomSpringJUnit4ClassRunner extends SpringJUnit4ClassRunner {

    private final boolean cacheSpringContext = true;

    public NoTeardownStroomSpringJUnit4ClassRunner(final Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    protected TestClass createTestClass(final Class<?> testClass) {
        return super.createTestClass(testClass);
    }

    @Override
    protected Object createTest() throws Exception {
        return super.createTest();
    }

    /**
     * Creates a new {@link TestContextManager} for the supplied test class.
     * <p>
     * Can be overridden by subclasses.
     *
     * @param clazz
     *            the test class to be managed
     */
    @Override
    protected TestContextManager createTestContextManager(final Class<?> clazz) {
        return new TestContextManager(clazz) {
            @Override
            public void beforeTestClass() throws Exception {
                super.beforeTestClass();
            }

            @Override
            public void afterTestClass() throws Exception {
                // If we aren't caching the Spring context them mark it dirty so
                // it is destroyed.
                if (!cacheSpringContext) {
                    final TestContext testContext = getTestContext();
                    testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
                    testContext.setAttribute(DependencyInjectionTestExecutionListener.REINJECT_DEPENDENCIES_ATTRIBUTE,
                            Boolean.TRUE);
                }

                super.afterTestClass();
            }
        };
    }



    @Override
    protected void runChild(final FrameworkMethod method, final RunNotifier notifier) {
        try {
            try {
                TaskScopeContextHolder.addContext();

                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                try {
                    super.runChild(method, notifier);

                } finally {
                }

            } finally {
                TaskScopeContextHolder.removeContext();
            }

            while (TaskScopeContextHolder.contextExists()) {
                notifier.fireTestFailure(
                        new Failure(Description.createTestDescription(getTestClass().getJavaClass(), method.getName()),
                                new RuntimeException("Context stills exists?")));
                TaskScopeContextHolder.removeContext();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
