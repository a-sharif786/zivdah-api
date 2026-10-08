package com.zivdah.auth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticVersionTest {

    @Test
    void comparesNumericallyNotLexically() {
        assertThat(SemanticVersion.parse("1.10.0")).isGreaterThan(SemanticVersion.parse("1.9.0"));
        assertThat(SemanticVersion.parse("2.0.0")).isGreaterThan(SemanticVersion.parse("1.99.99"));
        assertThat(SemanticVersion.parse("1.0.1")).isGreaterThan(SemanticVersion.parse("1.0.0"));
    }

    @Test
    void normalizesShortFormsPrefixAndBuildMetadata() {
        assertThat(SemanticVersion.parse("2")).isEqualTo(SemanticVersion.parse("2.0.0"));
        assertThat(SemanticVersion.parse("1.1")).hasToString("1.1.0");
        assertThat(SemanticVersion.parse("v1.2.3")).hasToString("1.2.3");
        assertThat(SemanticVersion.parse(" 1.2.3+45 ")).isEqualTo(SemanticVersion.parse("1.2.3"));
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> SemanticVersion.parse("1.2.3.4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse("1.2.x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse("1.0.0-beta")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse("")).isInstanceOf(IllegalArgumentException.class);
    }
}
