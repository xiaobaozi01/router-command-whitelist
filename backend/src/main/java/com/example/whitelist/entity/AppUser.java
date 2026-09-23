package com.example.whitelist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("app_user")
public class AppUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String displayName;
    private String passwordHash;
    @TableField("role_name")
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
