package com.getjobs.worker.yupao;

import lombok.Data;

import java.util.List;

/**
 * 鱼泡直聘 Worker 配置
 * 配置加载由 YupaoService 负责
 */
@Data
public class YupaoConfig {

    /** 默认打招呼语 */
    public static final String DEFAULT_SAY_HI =
            "您好，我对这个岗位很感兴趣，有相关经验，方便沟通一下工作内容和薪资吗？";

    /** 搜索关键词列表 */
    private List<String> keywords;

    /** 城市名称（如 杭州） */
    private String city;

    /** 区县名称（可选，如 余杭区） */
    private String area;

    /** 每页遍历后的等待时间（秒） */
    private Integer waitTime;

    /** 最多翻页数 */
    private Integer maxPage;

    /** 打招呼语 */
    private String sayHi;

    /** 目标薪资下限（元/月，null 不过滤） */
    private Integer expectedSalaryMin;

    /** 目标薪资上限（元/月） */
    private Integer expectedSalaryMax;
}
