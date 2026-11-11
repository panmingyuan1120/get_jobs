package com.getjobs.worker.yupao;

import java.util.Map;

/**
 * 鱼泡直聘网页元素定位器
 *
 * ✅ 2026-11-11 已用真实登录态浏览器校准（www.yupao.com 主站，非 /web/ SPA）。
 * 关键结论：
 * 1. 搜索结果页 URL：https://www.yupao.com/topic/{城市码}c0/?keywords={urlencoded关键词}
 *    城市码 = 鱼泡 area id（全国=a1，杭州=a383，见 CITY_CODES）
 * 2. 岗位卡片 = 搜索结果页里 href 以 .html 结尾的 a 标签（/zhaogong/{id}/{bizId}.html），
 *    点击会开新标签页，Worker 直接 goto 该 href 即可
 * 3. 岗位详情页操作区 = #JobDetailBaseInfoBox 下的 span.adcjb（「发送简历」「免费聊」）
 *    未沟通时是「免费聊」，已沟通后变「继续聊」；付费沟通显示「聊一聊」+价格
 * 4. 点击「免费聊」会打开居中聊天弹窗，且鱼泡自动发送一条默认招呼语
 * 5. 聊天编辑器是语义化类名（未 hash）：div.fb-editor（contenteditable）+ button.btn-send
 *
 * 注意：站点 class 多为构建哈希（如 adcjb），换版本可能失效；文本兜底策略已内置。
 */
public class Locators {
    // ---------- 搜索 ----------
    /** 岗位卡片：搜索结果页里指向岗位详情页的链接（href 以 .html 结尾） */
    public static final String JOB_CARDS = "a[href^='/zhaogong/'][href$='.html']";

    /** 卡片内薪资文本（如 5000-10000元/月） */
    public static final String JOB_SALARY_TEXT = "span.acdba, span:has-text('元/月'), span:has-text('元/天')";

    /** 卡片内职位名 */
    public static final String JOB_TITLE_TEXT = "div.acdaf span";

    /** 卡片内公司名（卡片底部 a 链接） */
    public static final String JOB_COMPANY_TEXT = "div.acdbd a";

    // ---------- 岗位详情页 ----------
    /** 详情页岗位信息操作区 */
    public static final String DETAIL_BASE_INFO_BOX = "#JobDetailBaseInfoBox";

    /** 操作按钮（span.adcjb）：文案为「发送简历」「免费聊」/「继续聊」/「聊一聊」 */
    public static final String DETAIL_ACTION_BUTTONS = "#JobDetailBaseInfoBox span.adcjb, #celling-fix-top span.adcjb";

    /** 免费聊按钮（文本兜底） */
    public static final String FREE_CHAT_BUTTON = "span.adcjb:has-text('免费聊'), button:has-text('免费聊')";

    // ---------- 聊天弹窗 ----------
    /** 聊天输入框：contenteditable 富文本编辑器（语义化类名，稳定） */
    public static final String CHAT_INPUT = "div.fb-editor, div[data-fish-editor='true']";

    /** 聊天发送按钮（语义化类名，稳定） */
    public static final String CHAT_SEND_BUTTON = "button.btn-send";

    /** 聊天弹窗关闭按钮（消息弹窗右上角） */
    public static final String CHAT_CLOSE = "[class*='message'] [class*='close'], [aria-label='Close']";

    // ---------- 城市码映射 ----------
    /**
     * 城市名 -> 鱼泡 URL 城市码（area id）。
     * 来源：yupao-prod.yupaowang.com/lbs/v1/area/hot（2026-11-11 抓取）。
     * 未收录城市会回退到全国（a1）并告警；也支持直接填鱼泡码（如 a76）。
     */
    public static final Map<String, String> CITY_CODES = Map.ofEntries(
            Map.entry("全国", "a1"),
            Map.entry("北京", "a2"),
            Map.entry("上海", "a25"),
            Map.entry("天津", "a27"),
            Map.entry("重庆", "a32"),
            Map.entry("厦门", "a60"),
            Map.entry("广州", "a76"),
            Map.entry("深圳", "a77"),
            Map.entry("佛山", "a80"),
            Map.entry("郑州", "a149"),
            Map.entry("武汉", "a180"),
            Map.entry("长沙", "a197"),
            Map.entry("南京", "a220"),
            Map.entry("苏州", "a221"),
            Map.entry("无锡", "a222"),
            Map.entry("青岛", "a284"),
            Map.entry("西安", "a311"),
            Map.entry("成都", "a322"),
            Map.entry("杭州", "a383"),
            Map.entry("宁波", "a388"),
            Map.entry("合肥", "a3401")
    );

    /** 把配置里的城市字段解析为鱼泡城市码；不认识的城市回退全国并告警 */
    public static String resolveCityCode(String city) {
        if (city == null || city.isBlank() || "全国".equals(city.trim())) {
            return "a1";
        }
        String c = city.trim();
        // 直接填了鱼泡码（如 a76 / 76）
        if (c.matches("a?\\d+")) {
            return c.startsWith("a") ? c : "a" + c;
        }
        // 带后缀的城市名（杭州市/杭州）
        String code = CITY_CODES.get(c);
        if (code == null && c.endsWith("市")) {
            code = CITY_CODES.get(c.substring(0, c.length() - 1));
        }
        if (code == null) {
            // 未知城市：回退全国。这里不 log（静态上下文），由调用方告警
            return null;
        }
        return code;
    }
}
