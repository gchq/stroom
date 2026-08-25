/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.pipeline.stress;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Decides when the stress harness injects a fault, and counts what it injected.
 * <p>
 * <strong>On determinism.</strong> Each thread draws from its own {@link Random}
 * seeded from the policy seed and the thread's index, so a given thread sees the
 * same sequence of draws on every run. The <em>interleaving</em> of threads is
 * not reproducible and this class does not pretend otherwise; what the seed buys
 * is a stable fault rate and a stable per-thread sequence, which is enough to
 * make a failing run worth re-running with the seed printed in the assertion
 * message.
 * </p>
 * <p>
 * <strong>On counting.</strong> A stress run that injects no faults passes
 * trivially. Every scenario therefore asserts on {@link #getInjectedCount} as
 * well as on its invariants, so a policy that silently stops firing - a rate
 * typo, a decorator that was never wired in - fails loudly instead of going
 * green.
 * </p>
 */
public final class FaultPolicy {

    /**
     * A policy that never injects anything, for baseline runs.
     */
    public static final FaultPolicy NONE = builder().build();

    private static final int MAX_TRACKED_THREADS = 256;

    private final long seed;
    private final double failureRate;
    private final double delayRate;
    private final int maxDelayMillis;
    private final Set<FaultPoint> enabledPoints;

    private final Map<FaultPoint, AtomicLong> injectedCounts;
    private final AtomicLong delayCount = new AtomicLong();

    /**
     * Whether injection is currently live. Scenarios turn this off to end the
     * storm and let the pipeline drain, the way a real outage ends. Without it
     * a scenario can only ever assert what is true mid-failure, which is a much
     * weaker statement than "and then it recovered completely".
     */
    private final AtomicBoolean injecting = new AtomicBoolean(true);

    /**
     * Per-thread PRNGs, indexed by a small dense thread index rather than by
     * {@link Thread} identity so that the sequence a thread sees depends only on
     * the order in which threads first reached the policy.
     */
    private final AtomicLongArray threadSeedsIssued = new AtomicLongArray(1);
    private final ThreadLocal<Random> threadRandom;

    private FaultPolicy(final Builder builder) {
        this.seed = builder.seed;
        this.failureRate = builder.failureRate;
        this.delayRate = builder.delayRate;
        this.maxDelayMillis = builder.maxDelayMillis;
        this.enabledPoints = builder.enabledPoints.isEmpty()
                ? EnumSet.noneOf(FaultPoint.class)
                : EnumSet.copyOf(builder.enabledPoints);

        this.injectedCounts = new EnumMap<>(FaultPoint.class);
        for (final FaultPoint point : FaultPoint.values()) {
            this.injectedCounts.put(point, new AtomicLong());
        }

        this.threadRandom = ThreadLocal.withInitial(() -> {
            final long index = threadSeedsIssued.getAndIncrement(0) % MAX_TRACKED_THREADS;
            return new Random(seed * 31L + index);
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getSeed() {
        return seed;
    }

    public Set<FaultPoint> getEnabledPoints() {
        return Set.copyOf(enabledPoints);
    }

    public boolean isEnabled(final FaultPoint faultPoint) {
        return enabledPoints.contains(faultPoint);
    }

    /**
     * Throw an {@link InjectedFaultException} at this point with the configured
     * probability, otherwise return normally.
     *
     * @param faultPoint The point being intercepted.
     * @throws InjectedFaultException If this draw injects a fault.
     */
    public void maybeFail(final FaultPoint faultPoint) throws InjectedFaultException {
        if (!injecting.get() || !enabledPoints.contains(faultPoint)) {
            return;
        }
        if (threadRandom.get().nextDouble() < failureRate) {
            injectedCounts.get(faultPoint).incrementAndGet();
            throw new InjectedFaultException(faultPoint);
        }
    }

    /**
     * Sleep for a short random interval with the configured probability.
     * <p>
     * Delays matter as much as failures. A stage that is merely slow rather than
     * broken is what surfaces lock-holding bugs, queue-depth blow-ups and
     * backoff mistakes, and none of those need an exception to reproduce.
     * </p>
     */
    public void maybeDelay() {
        if (!injecting.get() || delayRate <= 0.0d || maxDelayMillis <= 0) {
            return;
        }
        final Random random = threadRandom.get();
        if (random.nextDouble() < delayRate) {
            delayCount.incrementAndGet();
            try {
                Thread.sleep(random.nextInt(maxDelayMillis) + 1L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * End the storm. Subsequent operations pass straight through, so the
     * pipeline can be given a fair chance to drain and be asserted on.
     */
    public void quiesce() {
        injecting.set(false);
    }

    public boolean isInjecting() {
        return injecting.get();
    }

    public long getInjectedCount(final FaultPoint faultPoint) {
        return injectedCounts.get(faultPoint).get();
    }

    public long getInjectedCount() {
        long total = 0;
        for (final AtomicLong count : injectedCounts.values()) {
            total += count.get();
        }
        return total;
    }

    public long getDelayCount() {
        return delayCount.get();
    }

    /**
     * @return A one-line summary suitable for an assertion message, including the
     * seed needed to re-run.
     */
    public String describe() {
        final StringBuilder sb = new StringBuilder()
                .append("seed=").append(seed)
                .append(", failureRate=").append(failureRate)
                .append(", delays=").append(delayCount.get())
                .append(", injected={");

        boolean first = true;
        for (final Map.Entry<FaultPoint, AtomicLong> entry : injectedCounts.entrySet()) {
            final long count = entry.getValue().get();
            if (count > 0) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append('=').append(count);
                first = false;
            }
        }
        return sb.append('}').toString();
    }

    // -------------------------------------------------------------------------

    public static final class Builder {

        private long seed = 20260824L;
        private double failureRate;
        private double delayRate;
        private int maxDelayMillis = 5;
        private final Set<FaultPoint> enabledPoints = EnumSet.noneOf(FaultPoint.class);

        private Builder() {
        }

        public Builder seed(final long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * @param failureRate Probability in [0, 1] that an enabled fault point fires.
         */
        public Builder failureRate(final double failureRate) {
            if (failureRate < 0.0d || failureRate > 1.0d) {
                throw new IllegalArgumentException("failureRate must be in [0, 1], got " + failureRate);
            }
            this.failureRate = failureRate;
            return this;
        }

        public Builder delays(final double delayRate, final int maxDelayMillis) {
            if (delayRate < 0.0d || delayRate > 1.0d) {
                throw new IllegalArgumentException("delayRate must be in [0, 1], got " + delayRate);
            }
            this.delayRate = delayRate;
            this.maxDelayMillis = maxDelayMillis;
            return this;
        }

        public Builder inject(final FaultPoint... faultPoints) {
            for (final FaultPoint faultPoint : faultPoints) {
                enabledPoints.add(faultPoint);
            }
            return this;
        }

        public Builder injectAll() {
            enabledPoints.addAll(EnumSet.allOf(FaultPoint.class));
            return this;
        }

        public FaultPolicy build() {
            return new FaultPolicy(this);
        }
    }
}
