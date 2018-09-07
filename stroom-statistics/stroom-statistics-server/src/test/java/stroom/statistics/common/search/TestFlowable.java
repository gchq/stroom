package stroom.statistics.common.search;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TestFlowable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFlowable.class);

    @Test
    public void testFlowable() {

        AtomicInteger counter = new AtomicInteger();
        Flowable
                .generate(
                        () -> {
                            LOGGER.debug("Init state");
                            return counter;
                        },
                        (i, emitter) -> {
                            int j = i.incrementAndGet();
                            if (j <= 10) {
                                LOGGER.debug("emit");
                                emitter.onNext(j);
                            } else {
                                LOGGER.debug("complete");
                                emitter.onComplete();
                            }

                        })
                .map(i -> {
                    LOGGER.debug("mapping");
                    return "xxx" + i;
                })
                .subscribe(
                        data -> {
                            LOGGER.debug("onNext called: {}", data);
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });

    }

    @Ignore //manual test
    @Test
    public void testFlowableWithUsing() {

        Flowable<Integer> flowableInt = Flowable
                .using(
                        () -> {
                            LOGGER.debug("Init resource");
                            return new AtomicInteger();
                        },
                        atomicInt -> {
                            LOGGER.debug("Converting resource to flowable");
                            return Flowable.generate(
                                    () -> {
                                        LOGGER.debug("Init state");
                                        return atomicInt;
                                    },
                                    (i, emitter) -> {
                                        int j = i.incrementAndGet();
                                        if (j <= 10) {
                                            LOGGER.debug("emit");
                                            emitter.onNext(j);
                                        } else {
                                            LOGGER.debug("complete");
                                            emitter.onComplete();
                                        }

                                    });
                        },
                        atomicInt -> {
                            LOGGER.debug("Close resource");
                        });

        LOGGER.debug("About to subscribe");

        flowableInt
                .map(i -> {
                    LOGGER.debug("mapping");
                    return "xxx" + i;
                })
                .subscribe(
                        data -> {
                            LOGGER.debug("onNext called: {}", data);
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });

    }

    @Ignore //manual test
    @Test
    public void testFlowableWithDispose() throws InterruptedException {

        final AtomicInteger counter = new AtomicInteger();
        final Disposable disposable = Flowable
                .generate(
                        () -> {
                            LOGGER.debug("Generator init");
                            return counter;
                        },
                        (i, emitter) -> {
                            int j = i.incrementAndGet();
                            Thread.sleep(500);
                            if (j <= 20) {
                                LOGGER.debug("emitting {}", j);
                                emitter.onNext(j);
                            } else {
                                LOGGER.debug("Generator complete");
                                emitter.onComplete();
                            }
                        })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .map(i -> {
                    String str = "xxx" + i;
                    LOGGER.debug("mapping {} to {}", i, str);
                    return str;
                })
                .subscribe(
                        str -> {
                            LOGGER.debug("onNext called with: {}", str);
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });

        LOGGER.debug("Subscribed");
        Thread.sleep(2250);
        LOGGER.debug("calling dispose");
        disposable.dispose();
        LOGGER.debug("disposed");
    }

    @Ignore //manual test
    @Test
    public void testFlowableWithUsingAndDispose() throws InterruptedException {

        Flowable<Integer> flowableInt = Flowable
                .using(
                        () -> {
                            LOGGER.debug("Init resource");
                            //atomic ref acts like a resource for the life of the flow
                            return new AtomicReference<>(new AtomicInteger());
                        },
                        atomicRef -> {
                            LOGGER.debug("Converting resource to flowable");
                            return Flowable.generate(
                                    () -> {
                                        LOGGER.debug("Init state");
                                        return atomicRef;
                                    },
                                    (i, emitter) -> {
                                        int j = i.get().incrementAndGet();
                                        Thread.sleep(500);
                                        if (j <= 20) {
                                            LOGGER.debug("emitting {}", j);
                                            emitter.onNext(j);
                                        } else {
                                            LOGGER.debug("complete");
                                            emitter.onComplete();
                                        }

                                    });
                        },
                        atomicRef -> {
                            LOGGER.debug("Close resource");
                            atomicRef.set(null);
                        });

        LOGGER.debug("About to subscribe");

        final Disposable disposable = flowableInt
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .map(i -> {
                    String str = "xxx" + i;
                    LOGGER.debug("mapping {} to {}", i, str);
                    return str;
                })
                .subscribe(
                        str -> {
                            LOGGER.debug("onNext called with: {}", str);
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });

        LOGGER.debug("Subscribed");
        Thread.sleep(2250);
        LOGGER.debug("calling dispose");
        disposable.dispose();
        LOGGER.debug("disposed");
    }

    @Ignore //manual test
    @Test
    public void testFlowableWithWindow() {

        AtomicInteger counter = new AtomicInteger();
        Flowable
                .generate(
                        () -> {
                            LOGGER.debug("Init state");
                            return counter;
                        },
                        (i, emitter) -> {
                            int j = i.incrementAndGet();
                            if (j <= 20) {
                                LOGGER.debug("sleeping");
                                Thread.sleep(500);
                                LOGGER.debug("emitting {}", j);
                                emitter.onNext(j);
                            } else {
                                LOGGER.debug("complete");
                                emitter.onComplete();
                            }

                        })
                .map(i -> {
                    LOGGER.debug("mapping");
                    return "xxx" + i;
                })
                .window(1, TimeUnit.SECONDS, Schedulers.single())
                .subscribe(
                        windowedFlowable -> {
                            LOGGER.debug("onNext called for windowedFlowable");
                            windowedFlowable.subscribe(
                                    data -> {
                                        LOGGER.debug("onNext called for inner flowable {}", data);
                                    },
                                    throwable -> {
                                        LOGGER.debug("onError called for inner flowable");
                                    },
                                    () -> {
                                        LOGGER.debug("onComplete called for inner flowable");
                                    }
                            );
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });

    }

    @Ignore //manual test
    @Test
    public void testFlowableWithWindowImediateComplete() {

        AtomicInteger counter = new AtomicInteger();
        Flowable
                .generate(
                        () -> {
                            LOGGER.debug("Init state");
                            return counter;
                        },
                        (i, emitter) -> {
                            //immediate complete
                            emitter.onComplete();
                        })
                .map(i -> {
                    LOGGER.debug("mapping");
                    return "xxx" + i;
                })
                .window(1, TimeUnit.SECONDS, Schedulers.single())
                .subscribe(
                        windowedFlowable -> {
                            LOGGER.debug("onNext called for windowedFlowable");
                            windowedFlowable.subscribe(
                                    data -> {
                                        LOGGER.debug("onNext called for inner flowable {}", data);
                                    },
                                    throwable -> {
                                        LOGGER.debug("onError called for inner flowable");
                                    },
                                    () -> {
                                        LOGGER.debug("onComplete called for inner flowable");
                                    }
                            );
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });
    }
}
