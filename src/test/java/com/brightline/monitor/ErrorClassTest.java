package com.brightline.monitor;

import static com.brightline.monitor.domain.ErrorClass.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.brightline.monitor.domain.ErrorClass;
import org.junit.jupiter.api.Test;

class ErrorClassTest {
    @Test
    void classifiesStatusCodes() {
        assertThat(ErrorClass.classify("success", 200)).isEqualTo(NONE);
        assertThat(ErrorClass.classify("timeout", null)).isEqualTo(TIMEOUT);
        assertThat(ErrorClass.classify("error", 500)).isEqualTo(SERVER);
        assertThat(ErrorClass.classify("error", 503)).isEqualTo(SERVER);
        assertThat(ErrorClass.classify("error", 429)).isEqualTo(THROTTLED);
        assertThat(ErrorClass.classify("error", 401)).isEqualTo(AUTH);
        assertThat(ErrorClass.classify("error", 403)).isEqualTo(AUTH);
        assertThat(ErrorClass.classify("error", 404)).isEqualTo(CLIENT);
        assertThat(ErrorClass.classify("error", null)).isEqualTo(SERVER);   // unknown failure is not hidden
    }
}
