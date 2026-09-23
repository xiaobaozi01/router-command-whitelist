package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.RegexFragmentRequest;
import com.example.whitelist.dto.RegexFragmentResponse;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.entity.RegexFragment;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.mapper.RegexFragmentMapper;
import com.example.whitelist.util.AuditUtils;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RegexFragmentService {
    private final RegexFragmentMapper fragmentMapper;
    private final CommandRuleMapper commandMapper;
    private final RegexEngineService regexEngineService;

    public RegexFragmentService(
            RegexFragmentMapper fragmentMapper,
            CommandRuleMapper commandMapper,
            RegexEngineService regexEngineService
    ) {
        this.fragmentMapper = fragmentMapper;
        this.commandMapper = commandMapper;
        this.regexEngineService = regexEngineService;
    }

    public PageResponse<RegexFragmentResponse> page(long current, long size, String keyword) {
        LambdaQueryWrapper<RegexFragment> query = new LambdaQueryWrapper<RegexFragment>()
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(RegexFragment::getName, keyword)
                        .or().like(RegexFragment::getDescription, keyword))
                .orderByDesc(RegexFragment::getIsCommon)
                .orderByAsc(RegexFragment::getName);
        Page<RegexFragment> page = fragmentMapper.selectPage(Page.of(current, size), query);
        List<RegexFragmentResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(page, records);
    }

    public List<RegexFragmentResponse> options() {
        return fragmentMapper.selectList(new LambdaQueryWrapper<RegexFragment>()
                        .orderByDesc(RegexFragment::getIsCommon).orderByAsc(RegexFragment::getName))
                .stream().map(this::toResponse).toList();
    }

    public RegexFragmentResponse create(RegexFragmentRequest request) {
        regexEngineService.validateFragmentPattern(request.pattern());
        RegexFragment fragment = new RegexFragment();
        apply(fragment, request);
        fragment.setCreatedBy(AuditUtils.currentUsername());
        fragment.setUpdatedBy(fragment.getCreatedBy());
        fragment.setCreatedAt(LocalDateTime.now());
        fragment.setUpdatedAt(fragment.getCreatedAt());
        fragmentMapper.insert(fragment);
        return toResponse(fragment);
    }

    public RegexFragmentResponse update(Long id, RegexFragmentRequest request) {
        RegexFragment fragment = requireFragment(id);
        regexEngineService.validateFragmentPattern(request.pattern());
        if (!fragment.getName().equals(request.name().trim()) && referenceCount(fragment.getName()) > 0) {
            throw new BusinessException(409, "该片段已被命令引用，不能修改片段名称");
        }
        apply(fragment, request);
        fragment.setUpdatedBy(AuditUtils.currentUsername());
        fragment.setUpdatedAt(LocalDateTime.now());
        fragmentMapper.updateById(fragment);
        return toResponse(fragment);
    }

    public void delete(Long id) {
        RegexFragment fragment = requireFragment(id);
        if (referenceCount(fragment.getName()) > 0) {
            throw new BusinessException(409, "该正则片段已被命令引用，不能删除");
        }
        fragmentMapper.deleteById(id);
    }

    private RegexFragment requireFragment(Long id) {
        RegexFragment fragment = fragmentMapper.selectById(id);
        if (fragment == null) {
            throw new BusinessException(404, "正则片段不存在");
        }
        return fragment;
    }

    private void apply(RegexFragment fragment, RegexFragmentRequest request) {
        fragment.setName(request.name().trim());
        fragment.setDescription(request.description().trim());
        fragment.setPattern(request.pattern());
        fragment.setIsCommon(request.common());
    }

    private RegexFragmentResponse toResponse(RegexFragment fragment) {
        return new RegexFragmentResponse(
                fragment.getId(), fragment.getName(), fragment.getDescription(), fragment.getPattern(),
                Boolean.TRUE.equals(fragment.getIsCommon()), referenceCount(fragment.getName()),
                fragment.getCreatedBy(), fragment.getUpdatedBy(),
                fragment.getCreatedAt(), fragment.getUpdatedAt());
    }

    private long referenceCount(String name) {
        String reference = "${" + name + "}";
        return commandMapper.selectList(new LambdaQueryWrapper<CommandRule>()
                        .select(CommandRule::getId, CommandRule::getRegexTemplate))
                .stream().filter(command -> command.getRegexTemplate().contains(reference)).count();
    }
}
