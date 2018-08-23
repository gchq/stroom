package stroom.entity.shared;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRange {

    @Test
    public void contains() {
        assertThat(Range.of(10L, 20L).contains(9)).isFalse();
        assertThat(Range.of(10L, 20L).contains(10)).isTrue();
        assertThat(Range.of(10L, 20L).contains(15)).isTrue();
        assertThat(Range.of(10L, 20L).contains(19)).isTrue();
        assertThat(Range.of(10L, 20L).contains(20)).isFalse();
    }

    @Test
    public void after() {
        assertThat(Range.of(10L, 20L).after(9)).isTrue();
        assertThat(Range.of(10L, 20L).after(10)).isFalse();
        assertThat(Range.of(10L, 20L).after(15)).isFalse();
        assertThat(Range.of(10L, 20L).after(19)).isFalse();
        assertThat(Range.of(10L, 20L).after(20)).isFalse();
    }

    @Test
    public void before() {
        assertThat(Range.of(10L, 20L).before(9)).isFalse();
        assertThat(Range.of(10L, 20L).before(10)).isFalse();
        assertThat(Range.of(10L, 20L).before(15)).isFalse();
        assertThat(Range.of(10L, 20L).before(19)).isFalse();
        assertThat(Range.of(10L, 20L).before(20L)).isTrue();
    }

    @Test
    public void isBounded() {
        assertThat(Range.of(10L, 20L).isBounded()).isTrue();
        assertThat(Range.from(10L).isBounded()).isFalse();
        assertThat(Range.to(20L).isBounded()).isFalse();
        assertThat(new Range<Long>().isBounded()).isFalse();
    }

    @Test
    public void isConstrained() {
        assertThat(Range.of(10L, 20L).isConstrained()).isTrue();
        assertThat(Range.from(10L).isConstrained()).isTrue();
        assertThat(Range.to(20L).isConstrained()).isTrue();
        assertThat(new Range<Long>().isConstrained()).isFalse();
    }
}