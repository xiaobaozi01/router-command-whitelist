package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.service.CommandService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:commandsearch;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@Transactional
class CommandServiceSearchTest {
    @Autowired
    private CommandService commandService;

    @Autowired
    private CommandRuleMapper commandMapper;

    @Test
    void searchesExpandedRegexBeforePaginating() {
        insertCommand("display integer", "display ${INTEGER}", LocalDateTime.now().minusMinutes(2));
        insertCommand("undo integer", "undo ${INTEGER}", LocalDateTime.now().minusMinutes(1));
        insertCommand("interface name", "interface [a-z]+", LocalDateTime.now());

        PageResponse<CommandResponse> firstPage = commandService.page(
                1, 1, null, "[0-9]+", null, null, null);
        PageResponse<CommandResponse> secondPage = commandService.page(
                2, 1, null, "[0-9]+", null, null, null);
        PageResponse<CommandResponse> templateReference = commandService.page(
                1, 10, null, "${INTEGER}", null, null, null);

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.pages()).isEqualTo(2);
        assertThat(firstPage.records()).hasSize(1);
        assertThat(firstPage.records().getFirst().expandedRegex()).contains("[0-9]+");
        assertThat(secondPage.records()).hasSize(1);
        assertThat(templateReference.records()).isEmpty();
    }

    private void insertCommand(String expression, String regexTemplate, LocalDateTime updatedAt) {
        CommandRule command = new CommandRule();
        command.setExpressionHtml("<p>" + expression + "</p>");
        command.setExpressionText(expression);
        command.setDescription("");
        command.setRegexTemplate(regexTemplate);
        command.setMatchStart(true);
        command.setMatchEnd(true);
        command.setCreatedAt(updatedAt);
        command.setUpdatedAt(updatedAt);
        commandMapper.insert(command);
    }
}
