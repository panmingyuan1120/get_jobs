package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 鱼泡直聘配置（www.yupao.com/web/）
 * 鱼泡是面向蓝领（建筑/工厂/物流/生活服务）的招聘平台，老板直招模式。
 */
@Data
@TableName("yupao_config")
public class YupaoConfigEntity {
    @TableId(type = IdType.AUTO)
    /** 主键ID */
    private Long id;

    /** 搜索关键词（JSON 字符串列表，如 ["电工","焊工"]） */
    private String keywords;

    /** 城市（名称，如 杭州） */
    private String city;

    /** 区县（可选，名称，如 余杭区） */
    private String area;

    /** 每页遍历后的等待时间（秒） */
    private Integer waitTime;

    /** 最多翻页数 */
    private Integer maxPage;

    /** 打招呼语（发送给老板的第一句话） */
    private String sayHi;

    /** 目标薪资下限（元/月，用于过滤岗位，空则不过滤） */
    private Integer expectedSalaryMin;

    /** 目标薪资上限（元/月） */
    private Integer expectedSalaryMax;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
