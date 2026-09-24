package com.example.whitelist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("command_approval_request")
public class CommandApprovalRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestType;
    private String status;
    private Long targetCommandId;
    private Long targetCommandVersion;
    private String beforeSnapshot;
    private String proposedSnapshot;
    private String changeReason;
    private Long submitterUserId;
    private String submitterUsername;
    private String submitterDisplayName;
    private LocalDateTime submittedAt;
    private String reviewerUsername;
    private String reviewerDisplayName;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private Long generatedCommandId;
    @Version
    private Long version;
}
