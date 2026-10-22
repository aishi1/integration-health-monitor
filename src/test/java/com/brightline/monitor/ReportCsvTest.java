package com.brightline.monitor;

import com.brightline.monitor.report.ReportCsv;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ReportCsvTest {
    @Test
    void quotesAndNeutralisesFormulas() {
        assertThat(ReportCsv.escape("plain")).isEqualTo("plain");
        assertThat(ReportCsv.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(ReportCsv.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportCsv.escape("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(ReportCsv.escape(null)).isEmpty();
    }
}