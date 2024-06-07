package io.avaje.spi.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilsTest {

    @Test
    void replaceDollar() {
        assertThat(Utils.replaceDollar("a$B")).isEqualTo("a.B");
    }

    @Test
    void replaceDollar_when_leftAsIs() {
        assertThat(Utils.replaceDollar("A$B")).isEqualTo("A$B");
        assertThat(Utils.replaceDollar("A$b")).isEqualTo("A$b");
    }
}