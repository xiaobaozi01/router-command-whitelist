package com.example.whitelist.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("command_current_view")
public class CommandCurrentView {
    private Long commandId;
    private Long viewId;
}
