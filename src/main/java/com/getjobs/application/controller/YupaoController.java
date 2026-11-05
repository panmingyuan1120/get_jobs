package com.getjobs.application.controller;

import com.getjobs.application.entity.YupaoConfigEntity;
import com.getjobs.application.service.YupaoService;
import com.getjobs.worker.service.YupaoJobService;
import com.getjobs.worker.manager.PlaywrightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 鱼泡直聘控制器
 * 提供鱼泡平台配置、登录与任务管理的 REST API。
 */
@RestController
@RequestMapping("/api/yupao")
@CrossOrigin(origins = "*")
public class YupaoController {

    private static final Logger log = LoggerFactory.getLogger(YupaoController.class);

    @Autowired
    private YupaoJobService yupaoJobService;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private YupaoService yupaoService;

    // ==================== 配置 ====================

    /**
     * 获取鱼泡配置
     */
    @GetMapping("/config")
    public ResponseEntity<YupaoConfigEntity> getConfig() {
        YupaoConfigEntity config = yupaoService.getFirstConfig();
        if (config.getSayHi() == null || config.getSayHi().isBlank()) {
            config.setSayHi(com.getjobs.worker.yupao.YupaoConfig.DEFAULT_SAY_HI);
        }
        return ResponseEntity.ok(config);
    }

    /**
     * 保存鱼泡配置
     */
    @PutMapping("/config")
    public ResponseEntity<YupaoConfigEntity> saveConfig(@RequestBody YupaoConfigEntity config) {
        YupaoConfigEntity saved = yupaoService.saveConfig(config);
        return ResponseEntity.ok(saved);
    }

    // ==================== 登录 ====================

    /**
     * 触发鱼泡登录（打开登录页，等待用户微信扫码）
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> triggerLogin() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.ensureReady();
            playwrightManager.triggerYupaoLogin();
            response.put("success", true);
            response.put("message", "已打开鱼泡登录页，请使用微信扫码登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("触发鱼泡登录失败", e);
            response.put("success", false);
            response.put("message", "触发登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 查询鱼泡登录状态（登录后前端轮询此接口，成功后调用保存 Cookie）
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("yupao");
            if (!isLoggedIn) {
                // 尝试实时刷新一次（用户可能刚扫码完成）
                isLoggedIn = playwrightManager.refreshYupaoLoginStatus();
            }
            response.put("success", true);
            response.put("isLoggedIn", isLoggedIn);
            response.put("message", isLoggedIn ? "已登录" : "未登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查鱼泡登录状态失败", e);
            response.put("success", false);
            response.put("isLoggedIn", false);
            response.put("message", "检查登录状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 手动保存鱼泡 Cookie（扫码完成后调用）
     */
    @PostMapping("/save-cookies")
    public ResponseEntity<Map<String, Object>> saveCookies() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.saveYupaoCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "鱼泡 Cookie 已保存");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存 Cookie 失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== 任务 ====================

    /**
     * 启动鱼泡自动打招呼任务
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startYupaoJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!playwrightManager.isLoggedIn("yupao")) {
                response.put("success", false);
                response.put("message", "请先登录鱼泡");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }
            if (yupaoJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "鱼泡任务已在运行中");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }
            CompletableFuture.runAsync(() ->
                    yupaoJobService.executeDelivery(progressMessage ->
                            log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage())));
            response.put("success", true);
            response.put("message", "鱼泡任务启动成功");
            response.put("status", "started");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动鱼泡任务失败", e);
            response.put("success", false);
            response.put("message", "启动失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止鱼泡任务
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopYupaoJob() {
        Map<String, Object> response = new HashMap<>();
        yupaoJobService.stopDelivery();
        response.put("success", true);
        response.put("message", "停止请求已发送");
        return ResponseEntity.ok(response);
    }

    /**
     * 查询鱼泡任务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(yupaoJobService.getStatus());
    }
}
