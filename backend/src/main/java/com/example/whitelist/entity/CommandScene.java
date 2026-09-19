package com.example.whitelist.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("command_scene")
public class CommandScene {
    private Long commandId;
    private Long sceneId;
}
