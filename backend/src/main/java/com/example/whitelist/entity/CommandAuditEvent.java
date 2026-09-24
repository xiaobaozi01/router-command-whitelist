package com.example.whitelist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("command_audit_event")
public class CommandAuditEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long commandId;
    private String action;
    private Long actorUserId;
    private String actorUsername;
    private String actorDisplayName;
    private LocalDateTime occurredAt;
    private String changeReason;
    private String changedFields;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String source;
}
