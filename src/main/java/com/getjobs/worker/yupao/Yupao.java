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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static com.getjobs.worker.yupao.Locators.*;

/**
 * 鱼泡直聘自动打招呼
 *
 * <p>流程：打开鱼泡直聘 web 端 -> 按关键词搜索 -> 遍历岗位卡片 ->
 * 点击「打招呼/立即沟通」-> 在聊天框输入打招呼语并发送 -> 关闭聊天窗口 -> 下一页。
 *
 * <p>⚠️ 骨架说明：
 * 页面结构基于鱼泡直聘 web 端（React + antd）的通用模式编写，选择器集中在
 * {@link Locators}。上线前必须真实登录并校准选择器（见 Locators 顶部说明）。
 * 每一处分页/卡片/按钮操作都做了防御性 try-catch，单个卡片失败不会中断整体任务。
 */
@Slf4j
@Component
@Scope("prototype")
public class Yupao {
    static {
        System.setProperty("log.name", "yupao");
    }

    public static final String HOME_URL = "https://www.yupao.com/web/";
    /** 岗位列表页（鱼泡 SPA 真实路由，来自站点路由表；关键词通过页面内搜索框输入） */
    public static final String JOB_LIST_URL = "https://www.yupao.com/web/job/list";

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
        return totalDelivered;
    }

    /**
     * 按关键词执行一轮：打开搜索页 -> 逐页遍历岗位卡片。
     */
    private int deliverByKeyword(String keyword) {
        int delivered = 0;
        try {
            report(String.format("【%s】打开岗位列表页：%s", keyword, JOB_LIST_URL));
            navigate(JOB_LIST_URL);
            // 关键词通过页面内搜索框输入（URL 参数格式未公开，页面内输入最可靠）
            searchOnPage(keyword);

            int maxPage = config.getMaxPage() != null && config.getMaxPage() > 0
                    ? config.getMaxPage() : 10;
            for (int pageNum = 1; pageNum <= maxPage; pageNum++) {
                if (shouldStop()) {
                    break;
                }
                int pageDelivered = processCurrentPage(keyword, pageNum);
                delivered += pageDelivered;
                report(String.format("【%s】第 %d/%d 页完成，本页打招呼 %d 次",
                        keyword, pageNum, maxPage, pageDelivered), pageNum, maxPage);

                if (!gotoNextPage()) {
                    log.info("【{}】没有下一页，结束该关键词", keyword);
                    break;
                }
                sleepSeconds(waitSeconds());
            }
        } catch (Exception e) {
            log.error("【{}】关键词投递异常: {}", keyword, e.getMessage(), e);
            report(String.format("【%s】执行异常：%s", keyword, e.getMessage()));
        }
        return delivered;
    }

    /**
     * 在岗位列表页的搜索框中输入关键词并触发搜索。
     * 找不到搜索框时仅记录日志，不中断流程（列表页本身也会展示推荐岗位）。
     */
    private void searchOnPage(String keyword) {
        try {
            Locator searchInput = page.locator(Locators.SEARCH_INPUT).first();
            searchInput.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                    .setTimeout(8_000));
            searchInput.fill(keyword);
            // 回车触发搜索（antd 搜索框通用行为），失败再点搜索按钮
            try {
                searchInput.press("Enter");
            } catch (Exception e) {
                Locator btn = page.locator(Locators.SEARCH_BUTTON).first();
                if (btn.count() > 0) {
                    btn.click();
                }
            }
            sleepSeconds(2);
            log.info("【{}】已在页面搜索框输入关键词并触发搜索", keyword);
        } catch (Exception e) {
            log.warn("【{}】未找到搜索框（{}），将按列表页默认内容继续", keyword, e.getMessage());
        }
    }

    /**
     * 处理当前页的所有岗位卡片。返回成功打招呼的数量。
     */
    private int processCurrentPage(String keyword, int pageNum) {
        int delivered = 0;
        Locator cards;
        try {
            cards = page.locator(JOB_CARDS);
            cards.first().waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                    .setTimeout(10_000));
        } catch (Exception e) {
            log.warn("【{}】第 {} 页未找到岗位卡片: {}", keyword, pageNum, e.getMessage());
            return 0;
        }
        int count = cards.count();
        log.info("【{}】第 {} 页发现 {} 个岗位卡片", keyword, pageNum, count);
        if (count == 0) {
            return 0;
        }

        for (int i = 0; i < count; i++) {
            if (shouldStop()) {
                break;
            }
            try {
                Locator card = cards.nth(i);
                // 薪资过滤：可选
                if (!salaryMatches(card)) {
                    log.debug("【{}】第 {} 页卡片 {} 薪资不匹配，跳过", keyword, pageNum, i + 1);
                    continue;
                }
                if (greetCard(card, i + 1)) {
                    delivered++;
                    resultList.add(keyword + "#" + pageNum + "-" + (i + 1));
                }
                // 卡片之间稍作停顿，模拟真人节奏
                sleepSeconds(1 + (int) (Math.random() * 2));
            } catch (Exception e) {
                log.warn("【{}】第 {} 页卡片 {} 处理失败: {}", keyword, pageNum, i + 1, e.getMessage());
            }
        }
        return delivered;
    }

    /**
     * 对单张卡片打招呼：点击沟通按钮 -> 输入打招呼语 -> 发送 -> 关闭聊天窗。
     */
    private boolean greetCard(Locator card, int index) {
        try {
            Locator applyBtn = card.locator(JOB_APPLY_BUTTON).first();
            if (applyBtn.count() == 0) {
                // 部分卡片把按钮悬浮显示，先 hover 再找
                card.hover();
                applyBtn = card.locator(JOB_APPLY_BUTTON).first();
                if (applyBtn.count() == 0) {
                    log.debug("卡片 {} 未找到沟通按钮，跳过", index);
                    return false;
                }
            }
            String btnText = applyBtn.innerText().trim();
            // 已是「已沟通/已报名」状态的卡片跳过
            if (btnText.contains("已")) {
                log.debug("卡片 {} 状态为【{}】，跳过", index, btnText);
                return false;
            }
            applyBtn.click();
            PlaywrightUtil.sleep(1);

            // 点击后可能弹聊天窗或站内 IM，尝试输入并发送打招呼语
            String sayHi = config.getSayHi() != null && !config.getSayHi().isBlank()
                    ? config.getSayHi() : YupaoConfig.DEFAULT_SAY_HI;
            boolean sent = false;
            try {
                Locator input = page.locator(CHAT_INPUT).first();
                if (input.count() > 0 && input.isVisible()) {
                    input.fill(sayHi);
                    PlaywrightUtil.sleep(1);
                    Locator sendBtn = page.locator(CHAT_SEND_BUTTON).first();
                    if (sendBtn.count() > 0) {
                        sendBtn.click();
                        sent = true;
                    } else {
                        input.press("Enter");
                        sent = true;
                    }
                }
            } catch (Exception e) {
                log.debug("聊天输入失败（可能是纯报名按钮无需输入）: {}", e.getMessage());
            }
            // 有的平台点按钮即完成报名，无输入框也算成功
            if (!sent) {
                // 确认弹窗（如有）
                try {
                    Locator confirm = page.locator(DIALOG_CONFIRM).first();
                    if (confirm.count() > 0 && confirm.isVisible()) {
                        confirm.click();
                        PlaywrightUtil.sleep(1);
                    }
                    sent = true;
                } catch (Exception ignored) {
                }
            }
            // 关闭聊天窗口（如有）
            closeChatIfPresent();
            log.info("卡片 {} 打招呼完成（消息已发送: {}）", index, sent);
            return true;
        } catch (Exception e) {
            log.warn("卡片 {} 打招呼失败: {}", index, e.getMessage());
            closeChatIfPresent();
            return false;
        }
    }

    /** 薪资过滤：解析卡片薪资文本（如 "8000-12000元/月"、"150元/天"），与配置的月薪区间比较。 */
    private boolean salaryMatches(Locator card) {
        Integer min = config.getExpectedSalaryMin();
        Integer max = config.getExpectedSalaryMax();
        if (min == null && max == null) {
            return true;
        }
        try {
            Locator salaryLocator = card.locator(JOB_SALARY_TEXT).first();
            if (salaryLocator.count() == 0) {
                return true; // 没有薪资信息时不拦截
            }
            String text = salaryLocator.innerText();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+)\\s*[-~至]\\s*(\\d+)").matcher(text == null ? "" : text);
            int jobMin;
            int jobMax;
            if (m.find()) {
                jobMin = Integer.parseInt(m.group(1));
                jobMax = Integer.parseInt(m.group(2));
            } else {
                java.util.regex.Matcher single = java.util.regex.Pattern
                        .compile("(\\d+)").matcher(text == null ? "" : text);
                if (!single.find()) {
                    return true;
                }
                jobMin = jobMax = Integer.parseInt(single.group(1));
            }
            // 元/天 的岗位按 22 个工作日折算成月薪
            if (text != null && text.contains("/天")) {
                jobMin *= 22;
                jobMax *= 22;
            }
            boolean ok = (min == null || jobMax >= min) && (max == null || jobMin <= max);
            if (!ok) {
                log.debug("薪资不匹配：岗位 {} vs 目标 {}-{}", text, min, max);
            }
            return ok;
        } catch (Exception e) {
            return true;
        }
    }

    /** 关闭可能弹出的聊天/沟通窗口，避免挡住后续卡片操作。 */
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

    /** 翻到下一页。返回是否成功（失败通常意味着已到最后一页）。 */
    private boolean gotoNextPage() {
        try {
            Locator next = page.locator(NEXT_PAGE).first();
            if (next.count() == 0 || !next.isEnabled()) {
                return false;
            }
            next.click();
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(10_000));
            PlaywrightUtil.sleep(2);
            return true;
        } catch (Exception e) {
            log.info("翻页失败（可能已到最后一页）: {}", e.getMessage());
            return false;
        }
    }

    private void navigate(String url) {
        page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(45_000));
        page.waitForLoadState(LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout(10_000));
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
}
