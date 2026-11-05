package com.getjobs.worker.service;

import com.getjobs.application.service.YupaoService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.yupao.Yupao;
import com.getjobs.worker.yupao.YupaoConfig;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 鱼泡直聘任务服务
 * 负责鱼泡平台的自动打招呼任务管理（参照猎聘模式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YupaoJobService implements JobPlatformService {

    private static final String PLATFORM = "yupao";

    private final PlaywrightManager playwrightManager;
    private final YupaoService yupaoService;
    private final ObjectProvider<Yupao> yupaoProvider;

    // 运行状态标志
    private volatile boolean isRunning = false;

    // 停止请求标志
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "检查浏览器状态..."));
            playwrightManager.ensureReady();

            Page page = playwrightManager.getYupaoPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "鱼泡页面未初始化"));
                return;
            }

            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录鱼泡"));
                return;
            }

            isRunning = true;
            shouldStop = false;

            YupaoConfig config = yupaoService.loadYupaoConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始打招呼任务..."));

            Yupao.ProgressCallback cb = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Yupao yupao = yupaoProvider.getObject();
            yupao.setPage(page);
            yupao.setConfig(config);
            yupao.setProgressCallback(cb);
            yupao.setShouldStopCallback(this::shouldStop);

            // 必须跑在 Playwright 专用线程上
            int deliveredCount = playwrightManager.callOnPlaywright(yupao::execute);

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                    String.format("打招呼任务完成，共发起 %d 次沟通", deliveredCount)));
        } catch (Exception e) {
            log.error("鱼泡任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "任务失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
        }
    }

    @Override
    public void stopDelivery() {
        if (!isRunning) {
            log.warn("鱼泡任务未在运行，无需停止");
            return;
        }
        log.info("收到停止鱼泡任务请求");
        shouldStop = true;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("platform", PLATFORM);
        status.put("isRunning", isRunning);
        status.put("isLoggedIn", playwrightManager.isLoggedIn(PLATFORM));
        return status;
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    public boolean shouldStop() {
        return shouldStop;
    }
}
