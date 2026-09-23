package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.dto.RegexPreviewResponse;
import com.example.whitelist.entity.RegexFragment;
import com.example.whitelist.mapper.RegexFragmentMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Service;

@Service
public class RegexEngineService {
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");
    private final RegexFragmentMapper fragmentMapper;

    public RegexEngineService(RegexFragmentMapper fragmentMapper) {
        this.fragmentMapper = fragmentMapper;
    }

    public void validateFragmentPattern(String pattern) {
        if (pattern.contains("${")) {
            throw new BusinessException(400, "正则片段不允许引用其他片段");
        }
        compile(pattern, "正则片段语法错误：");
    }

    public String expandAndValidate(String template) {
        return expandAndValidate(template, loadFragments());
    }

    public String expandAndValidate(String template, boolean matchStart, boolean matchEnd) {
        return expandAndValidate(template, matchStart, matchEnd, loadFragments());
    }

    public RegexExpander createExpander() {
        Map<String, String> fragments = loadFragments();
        return (template, matchStart, matchEnd) -> expandAndValidate(
                template, matchStart, matchEnd, fragments);
    }

    private Map<String, String> loadFragments() {
        Map<String, String> fragments = new LinkedHashMap<>();
        for (RegexFragment fragment : fragmentMapper.selectList(Wrappers.emptyWrapper())) {
            fragments.put(fragment.getName(), fragment.getPattern());
        }
        return fragments;
    }

    private String expandAndValidate(String template, Map<String, String> fragments) {
        Matcher matcher = REFERENCE_PATTERN.matcher(template);
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String fragment = fragments.get(name);
            if (fragment == null) {
                throw new BusinessException(400, "引用的正则片段不存在：${" + name + "}");
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement("(?:" + fragment + ")"));
        }
        matcher.appendTail(expanded);
        if (expanded.indexOf("${") >= 0) {
            throw new BusinessException(400, "正则片段引用格式错误，名称必须使用大写字母、数字和下划线");
        }
        compile(expanded.toString(), "正则表达式语法错误：");
        return expanded.toString();
    }

    private String expandAndValidate(
            String template,
            boolean matchStart,
            boolean matchEnd,
            Map<String, String> fragments
    ) {
        String expanded = expandAndValidate(template, fragments);
        String finalRegex = applyBoundaries(expanded, matchStart, matchEnd);
        compile(finalRegex, "正则表达式语法错误：");
        return finalRegex;
    }

    public RegexPreviewResponse preview(
            String template,
            boolean matchStart,
            boolean matchEnd,
            String testText
    ) {
        try {
            String expanded = expandAndValidate(template, matchStart, matchEnd);
            Pattern pattern = Pattern.compile(expanded);
            List<RegexPreviewResponse.TestLineResult> results = new ArrayList<>();
            if (testText != null && !testText.isEmpty()) {
                String[] lines = testText.split("\\R", -1);
                for (int index = 0; index < lines.length; index++) {
                    results.add(new RegexPreviewResponse.TestLineResult(
                            index + 1,
                            lines[index],
                            pattern.matcher(lines[index]).find()
                    ));
                }
            }
            return new RegexPreviewResponse(true, expanded, null, results);
        } catch (BusinessException exception) {
            return new RegexPreviewResponse(false, null, exception.getMessage(), List.of());
        }
    }

    private String applyBoundaries(String regex, boolean matchStart, boolean matchEnd) {
        if (!matchStart && !matchEnd) {
            return regex;
        }
        return (matchStart ? "^" : "") + "(?:" + regex + ")" + (matchEnd ? "$" : "");
    }

    private Pattern compile(String pattern, String prefix) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException exception) {
            throw new BusinessException(400, prefix + exception.getDescription());
        }
    }

    @FunctionalInterface
    public interface RegexExpander {
        String expandAndValidate(String template, boolean matchStart, boolean matchEnd);
    }
}
