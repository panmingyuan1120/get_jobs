package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("boss_option")
public class BossOptionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 选项类型：city, industry, experience, jobType, salary, degree, scale, stage, area
     */
    private String type;

    /**
     * 父级代码（type=area 时为所属城市的 Boss 城市码，如 101210100；其他类型为空）
     */
    private String parentCode;

    /**
     * 选项名称
     */
    private String name;

    /**
     * 选项代码
     */
    private String code;

    /**
     * 显示排序（数值越小越靠前）
     */
    private Integer sortOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
