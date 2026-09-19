package com.example.whitelist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("command_rule")
public class CommandRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String expressionHtml;
    private String expressionText;
    private String description;
    private String regexTemplate;
    private Long targetViewId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
