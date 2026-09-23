package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.entity.RegexFragment;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.mapper.RegexFragmentMapper;
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
    @Autowired
    private RegexFragmentMapper fragmentMapper;

    @Test
    void searchesExpandedRegexBeforePaginating() {
        insertFragment("INTEGER", "[0-9]+");
        insertCommand("display integer", "display ${INTEGER}", LocalDateTime.now().minusMinutes(2));
        insertCommand("undo integer", "undo ${INTEGER}", LocalDateTime.now().minusMinutes(1));
        insertCommand("interface name", "interface [a-z]+", LocalDateTime.now());

        PageResponse<CommandResponse> firstPage = commandService.page(
                1, 1, null, "[0-9]+", null, null, null,
                null, null, null, null);
        PageResponse<CommandResponse> secondPage = commandService.page(
                2, 1, null, "[0-9]+", null, null, null,
                null, null, null, null);
        PageResponse<CommandResponse> templateReference = commandService.page(
                1, 10, null, "${INTEGER}", null, null, null,
                null, null, null, null);

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.pages()).isEqualTo(2);
        assertThat(firstPage.records()).hasSize(1);
        assertThat(firstPage.records().getFirst().expandedRegex()).contains("[0-9]+");
        assertThat(secondPage.records()).hasSize(1);
        assertThat(templateReference.records()).isEmpty();
    }

    @Test
    void filtersAuditUsersAndSortsBeforePaginating() {
        LocalDateTime now = LocalDateTime.now();
        insertCommand("older", "older", now.minusDays(2), now, "alice", "carol");
        insertCommand("newer", "newer", now, now.minusDays(1), "bob", "dave");

        PageResponse<CommandResponse> createdAscending = commandService.page(
                1, 1, null, null, null, null, null,
                null, null, "createdAt", "asc");
        PageResponse<CommandResponse> createdByBob = commandService.page(
                1, 10, null, null, null, null, null,
                "bob", null, "updatedAt", "desc");
        PageResponse<CommandResponse> updatedByCarol = commandService.page(
                1, 10, null, null, null, null, null,
                null, "carol", null, null);

        assertThat(createdAscending.records()).extracting(CommandResponse::expressionText)
                .containsExactly("older");
        assertThat(createdByBob.records()).extracting(CommandResponse::expressionText)
                .containsExactly("newer");
        assertThat(updatedByCarol.records()).extracting(CommandResponse::expressionText)
                .containsExactly("older");
        assertThat(commandService.auditUsers().creators()).containsExactly("alice", "bob");
        assertThat(commandService.auditUsers().updaters()).containsExactly("carol", "dave");
    }

    private void insertFragment(String name, String pattern) {
        RegexFragment fragment = new RegexFragment();
        fragment.setName(name);
        fragment.setDescription("");
        fragment.setPattern(pattern);
        fragment.setIsCommon(true);
        fragmentMapper.insert(fragment);
    }

    private void insertCommand(String expression, String regexTemplate, LocalDateTime updatedAt) {
        insertCommand(expression, regexTemplate, updatedAt, updatedAt, "system", "system");
    }

    private void insertCommand(
            String expression,
            String regexTemplate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String createdBy,
            String updatedBy
    ) {
        CommandRule command = new CommandRule();
        command.setExpressionHtml("<p>" + expression + "</p>");
        command.setExpressionText(expression);
        command.setDescription("");
        command.setRegexTemplate(regexTemplate);
        command.setMatchStart(true);
        command.setMatchEnd(true);
        command.setCreatedBy(createdBy);
        command.setUpdatedBy(updatedBy);
        command.setCreatedAt(createdAt);
        command.setUpdatedAt(updatedAt);
        commandMapper.insert(command);
    }
}
