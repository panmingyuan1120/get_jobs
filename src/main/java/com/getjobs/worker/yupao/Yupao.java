package com.getjobs.worker.yupao;

import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static com.getjobs.worker.yupao.Locators.*;

/**
 * 鱼泡直聘自动打招呼
 *
 * <p>✅ 2026-11-11 已按真实页面结构校准。流程：
 * 按关键词打开搜索结果页（/topic/{城市码}c0/?keywords=xxx）->
 * 收集岗位卡片链接（/zhaogong/{id}/{bizId}.html）-> 逐个打开详情页 ->
 * 识别操作按钮（免费聊=可沟通 / 继续聊=已沟通跳过 / 聊一聊=付费跳过）->
 * 点击「免费聊」打开聊天弹窗（鱼泡会自动发一条默认招呼语）->
 * 输入自定义打招呼语并发送 -> 下一个岗位。
 *
 * <p>说明：
 * - 搜索结果页为单页列表（无翻页组件），maxPage 配置暂不参与控制；
 *   岗位数量以页面实际渲染为准。
 * - 点击「免费聊」时鱼泡自动发送默认招呼语；配置了 sayHi 时会追加发送自定义话术。
 * - 防御性设计：单个岗位失败不中断任务。
 */
@Slf4j
@Component
@Scope("prototype")
public class Yupao {
    static {
        System.setProperty("log.name", "yupao");
    }

    public static final String HOME_URL = "https://www.yupao.com/";
    /** 搜索结果页 URL 模板：{cityCode} 如 a383(杭州)/a1(全国)，keywords 需 urlencode */
    public static final String SEARCH_URL_TEMPLATE =
            "https://www.yupao.com/topic/%sc0/?keywords=%s";

    @Getter
    private Date startDate;
    @Getter
    private final List<String> resultList = new ArrayList<>();

    @Setter
    private YupaoConfig config;
    @Setter
    private Page page;

    public interface ProgressCallback {
        void onProgress(String message, Integer current, Integer total);
    }

    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private void report(String message) {
        if (progressCallback != null) {
            progressCallback.onProgress(message, null, null);
        }
    }

    private void report(String message, int current, int total) {
        if (progressCallback != null) {
            progressCallback.onProgress(message, current, total);
        }
    }

    private boolean shouldStop() {
        return shouldStopCallback != null && shouldStopCallback.get();
    }

    /**
     * 执行入口。返回成功打招呼的岗位数。
     */
    public int execute() {
        if (page == null) {
            throw new IllegalStateException("Yupao.page 未设置");
        }
        if (config == null) {
            throw new IllegalStateException("Yupao.config 未设置");
        }
        this.startDate = new Date();
        this.resultList.clear();

        List<String> keywords = config.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            log.warn("未配置关键词，执行结束");
            report("未配置搜索关键词，请先在管理页面配置");
            return 0;
        }

        int totalDelivered = 0;
        for (int k = 0; k < keywords.size(); k++) {
            if (shouldStop()) {
                report("收到停止请求，任务终止");
                break;
            }
            String keyword = keywords.get(k);
            report(String.format("开始处理关键词【%s】(%d/%d)", keyword, k + 1, keywords.size()));
            totalDelivered += deliverByKeyword(keyword);
        }

        log.info("鱼泡任务完成，共打招呼 {} 次", totalDelivered);
        report(String.format("任务完成，共打招呼 %d 次", totalDelivered));
        return totalDelivered;
    }

    /**
     * 按关键词执行一轮：打开搜索结果页 -> 收集卡片 -> 逐个进详情页打招呼。
     */
    private int deliverByKeyword(String keyword) {
        int delivered = 0;
        try {
            // 解析城市码
            String city = config.getCity();
            String cityCode = resolveCityCode(city);
            if (cityCode == null) {
                log.warn("【{}】未收录城市【{}】，回退全国搜索", keyword, city);
                report(String.format("【%s】城市「%s」暂未收录（支持全国+20个热门城市），按全国搜索", keyword, city));
                cityCode = "a1";
            }

            String url = String.format(SEARCH_URL_TEMPLATE,
                    cityCode, URLEncoder.encode(keyword, StandardCharsets.UTF_8));
            report(String.format("【%s】打开搜索页：%s", keyword, url));
            navigate(url);

            // 等待岗位卡片出现
            Locator cards = page.locator(JOB_CARDS);
            try {
                cards.first().waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                        .setTimeout(15_000));
            } catch (Exception e) {
                log.warn("【{}】搜索页未出现岗位卡片：{}", keyword, e.getMessage());
                report(String.format("【%s】搜索结果为空或页面未加载出岗位，跳过该关键词", keyword));
                return 0;
            }

            // 滚动到底部触发懒加载（如有的话）
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            PlaywrightUtil.sleep(2);

            int count = cards.count();
            log.info("【{}】搜索到 {} 个岗位", keyword, count);
            report(String.format("【%s】搜索到 %d 个岗位，开始逐个打招呼", keyword, count));

            // 先收集所有卡片信息（href + 薪资 + 职位名），后续逐个导航
            List<CardInfo> cardInfos = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Locator card = cards.nth(i);
                try {
                    String href = card.getAttribute("href");
                    if (href == null || !href.endsWith(".html")) {
                        continue;
                    }
                    CardInfo info = new CardInfo();
                    info.href = href;
                    info.title = safeInnerText(card.locator(JOB_TITLE_TEXT).first());
                    info.salary = safeInnerText(card.locator(JOB_SALARY_TEXT).first());
                    info.company = safeInnerText(card.locator(JOB_COMPANY_TEXT).first());
                    cardInfos.add(info);
                } catch (Exception e) {
                    log.debug("【{}】卡片 {} 信息采集失败: {}", keyword, i + 1, e.getMessage());
                }
            }
            log.info("【{}】有效岗位卡片 {} 个", keyword, cardInfos.size());

            // 逐个岗位处理
            for (int i = 0; i < cardInfos.size(); i++) {
                if (shouldStop()) {
                    report("收到停止请求，任务终止");
                    break;
                }
                CardInfo info = cardInfos.get(i);
                try {
                    report(String.format("【%s】岗位 %d/%d：%s %s",
                            keyword, i + 1, cardInfos.size(),
                            info.title, info.salary), i + 1, cardInfos.size());

                    // 薪资过滤
                    if (!salaryMatches(info.salary)) {
                        log.info("【{}】岗位【{}】薪资 {} 不匹配，跳过", keyword, info.title, info.salary);
                        report(String.format("  薪资 %s 不在目标区间，跳过", info.salary));
                        continue;
                    }

                    if (greetJob(info)) {
                        delivered++;
                        resultList.add(keyword + "#" + info.title + "@" + info.company);
                        report("  ✓ 打招呼成功");
                    }
                    // 岗位之间稍作停顿，模拟真人节奏
                    sleepSeconds(1 + (int) (Math.random() * 2));
                } catch (Exception e) {
                    log.warn("【{}】岗位 {}【{}】处理失败: {}", keyword, i + 1, info.title, e.getMessage());
                    report(String.format("  处理失败：%s", e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("【{}】关键词投递异常: {}", keyword, e.getMessage(), e);
            report(String.format("【%s】执行异常：%s", keyword, e.getMessage()));
        }
        return delivered;
    }

    /**
     * 对单个岗位打招呼：打开详情页 -> 识别并点击「免费聊」 -> 输入并发送话术。
     */
    private boolean greetJob(CardInfo info) {
        String detailUrl = HOME_URL + (info.href.startsWith("/") ? info.href.substring(1) : info.href);
        navigate(detailUrl);

        // 等详情页信息区加载
        try {
            page.locator(DETAIL_BASE_INFO_BOX).first().waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                    .setTimeout(15_000));
        } catch (Exception e) {
            log.warn("岗位详情页未加载出操作区：{}", e.getMessage());
            report("  详情页未加载出操作区，跳过");
            return false;
        }

        // 读取操作按钮，判断沟通状态
        Locator actionBtns = page.locator(DETAIL_ACTION_BUTTONS);
        int btnCount = actionBtns.count();
        String freeChatBtnText = null;
        for (int j = 0; j < btnCount; j++) {
            String t = safeInnerText(actionBtns.nth(j));
            if (t == null) {
                continue;
            }
            if (t.contains("免费聊")) {
                freeChatBtnText = t;
                break;
            }
            if (t.contains("继续聊")) {
                log.info("岗位【{}】已沟通过（按钮：{}），跳过", info.title, t);
                report("  已沟通过，跳过");
                return false;
            }
            if (t.contains("聊一聊") || t.contains("聊一 聊")) {
                // 付费沟通（显示价格），不主动付费
                log.info("岗位【{}】为付费沟通（按钮：{}），跳过", info.title, t);
                report(String.format("  付费沟通（%s），跳过", t));
                return false;
            }
        }
        if (freeChatBtnText == null) {
            log.warn("岗位【{}】未找到可用的沟通按钮（共 {} 个按钮），跳过", info.title, btnCount);
            report("  未找到「免费聊」按钮，跳过");
            return false;
        }

        // 点击「免费聊」打开聊天弹窗（鱼泡会自动发送默认招呼语）
        Locator freeBtn = page.locator(FREE_CHAT_BUTTON).first();
        try {
            freeBtn.click();
        } catch (Exception e) {
            log.warn("点击「免费聊」失败：{}", e.getMessage());
            report("  点击免费聊失败，跳过");
            return false;
        }
        PlaywrightUtil.sleep(2);

        // 等聊天输入框出现
        Locator editor;
        try {
            editor = page.locator(CHAT_INPUT).first();
            editor.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                    .setTimeout(10_000));
        } catch (Exception e) {
            log.warn("岗位【{}】聊天窗口未打开：{}", info.title, e.getMessage());
            report("  聊天窗口未打开（可能免费次数用尽），跳过");
            closeChatIfPresent();
            return false;
        }

        // 是否发送自定义话术：配置了 sayHi 就追加发送；留空则只用鱼泡默认招呼语
        String sayHi = config.getSayHi();
        boolean customSent = false;
        if (sayHi != null && !sayHi.isBlank()) {
            try {
                editor.fill(sayHi);
                PlaywrightUtil.sleep(1);
                Locator sendBtn = page.locator(CHAT_SEND_BUTTON).first();
                if (sendBtn.count() > 0 && sendBtn.isEnabled()) {
                    sendBtn.click();
                    customSent = true;
                } else {
                    // 按钮未激活时用回车兜底
                    editor.press("Enter");
                    customSent = true;
                }
                PlaywrightUtil.sleep(1);
            } catch (Exception e) {
                log.warn("自定义话术发送失败（默认招呼语已生效）：{}", e.getMessage());
            }
        } else {
            log.info("未配置自定义打招呼语，仅依赖鱼泡默认招呼语");
        }

        closeChatIfPresent();
        log.info("岗位【{}】打招呼完成（自定义话术已发送: {}）", info.title, customSent);
        return true;
    }

    /** 薪资过滤：解析薪资文本（如 "5000-10000元/月"、"1.5-2.3万元/月"、"150元/天"），与目标月薪区间比较。 */
    private boolean salaryMatches(String salaryText) {
        Integer min = config.getExpectedSalaryMin();
        Integer max = config.getExpectedSalaryMax();
        if (min == null && max == null) {
            return true;
        }
        if (salaryText == null || salaryText.isBlank()) {
            return true; // 没有薪资信息时不拦截
        }
        try {
            // 万单位归一化为元：1.5-2.3万元/月 -> 15000-23000元/月
            String text = salaryText.replace("万", "0000").replace(",", "");
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+(?:\\.\\d+)?)\\s*[-~至]\\s*(\\d+(?:\\.\\d+)?)").matcher(text);
            double jobMin;
            double jobMax;
            if (m.find()) {
                jobMin = Double.parseDouble(m.group(1));
                jobMax = Double.parseDouble(m.group(2));
            } else {
                java.util.regex.Matcher single = java.util.regex.Pattern
                        .compile("(\\d+(?:\\.\\d+)?)").matcher(text);
                if (!single.find()) {
                    return true;
                }
                jobMin = jobMax = Double.parseDouble(single.group(1));
            }
            // 元/天 按每月 22 个工作日折算
            if (salaryText.contains("/天")) {
                jobMin *= 22;
                jobMax *= 22;
            }
            boolean ok = (min == null || jobMax >= min) && (max == null || jobMin <= max);
            return ok;
        } catch (Exception e) {
            return true;
        }
    }

    /** 关闭聊天弹窗（如有）。导航到下一岗位也会自然关闭，这里只是保险。 */
    private void closeChatIfPresent() {
        try {
            Locator close = page.locator(CHAT_CLOSE).first();
            if (close.count() > 0 && close.isVisible()) {
                close.click();
                PlaywrightUtil.sleep(1);
            }
        } catch (Exception ignored) {
        }
    }

    private String safeInnerText(Locator locator) {
        try {
            if (locator != null && locator.count() > 0) {
                String t = locator.innerText();
                return t == null ? null : t.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void navigate(String url) {
        page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(45_000));
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(8_000));
        } catch (Exception ignored) {
            // 网络空闲等待超时不影响流程
        }
        PlaywrightUtil.sleep(1);
    }

    private int waitSeconds() {
        return config.getWaitTime() != null && config.getWaitTime() > 0
                ? config.getWaitTime() : 10;
    }

    private void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 岗位卡片信息（搜索页采集） */
    private static class CardInfo {
        String href;
        String title;
        String salary;
        String company;
    }
}
