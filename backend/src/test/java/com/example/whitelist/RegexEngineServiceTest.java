package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.dto.RegexPreviewResponse;
import com.example.whitelist.entity.RegexFragment;
import com.example.whitelist.mapper.RegexFragmentMapper;
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
    @Autowired
    private RegexFragmentMapper fragmentMapper;

    @Test
    void expandsFragmentAndTestsEveryLineWithFullMatch() {
        RegexFragment fragment = new RegexFragment();
        fragment.setName("IPV4");
        fragment.setDescription("");
        fragment.setPattern("(?:(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])[.]){3}"
                + "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])");
        fragment.setIsCommon(true);
        fragmentMapper.insert(fragment);

        RegexPreviewResponse response = regexEngineService.preview(
                "display ip ${IPV4}",
                true,
                true,
                "display ip 192.168.1.1\nprefix display ip 192.168.1.1\ndisplay ip 999.1.1.1"
        );

        assertThat(response.valid()).isTrue();
        assertThat(response.expandedRegex()).startsWith("^(?:").endsWith(")$");
        assertThat(response.results()).extracting(RegexPreviewResponse.TestLineResult::matched)
                .containsExactly(true, false, false);
    }

    @Test
    void reportsUnknownFragment() {
        RegexPreviewResponse response = regexEngineService.preview(
                "display ${UNKNOWN}", true, true, "display anything");
        assertThat(response.valid()).isFalse();
        assertThat(response.error()).contains("不存在");
    }

    @Test
    void honorsEveryBoundaryCombination() {
        String lines = "display\ndisplay suffix\nprefix display\nprefix display suffix";

        assertMatches(regexEngineService.preview("display", true, true, lines), true, false, false, false);
        assertMatches(regexEngineService.preview("display", true, false, lines), true, true, false, false);
        assertMatches(regexEngineService.preview("display", false, true, lines), true, false, true, false);
        assertMatches(regexEngineService.preview("display", false, false, lines), true, true, true, true);
    }

    private void assertMatches(RegexPreviewResponse response, Boolean... expected) {
        assertThat(response.valid()).isTrue();
        assertThat(response.results()).extracting(RegexPreviewResponse.TestLineResult::matched)
                .containsExactly(expected);
    }
}
