package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.dto.RegexPreviewResponse;
import com.example.whitelist.service.RegexEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RegexEngineServiceTest {
    @Autowired
    private RegexEngineService regexEngineService;

    @Test
    void expandsFragmentAndTestsEveryLineWithFullMatch() {
        RegexPreviewResponse response = regexEngineService.preview(
                "display ip ${IPV4}",
                "display ip 192.168.1.1\nprefix display ip 192.168.1.1\ndisplay ip 999.1.1.1"
        );

        assertThat(response.valid()).isTrue();
        assertThat(response.results()).extracting(RegexPreviewResponse.TestLineResult::matched)
                .containsExactly(true, false, false);
    }

    @Test
    void reportsUnknownFragment() {
        RegexPreviewResponse response = regexEngineService.preview("display ${UNKNOWN}", "display anything");
        assertThat(response.valid()).isFalse();
        assertThat(response.error()).contains("不存在");
    }
}
