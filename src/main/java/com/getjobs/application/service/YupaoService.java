package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.YupaoConfigEntity;
import com.getjobs.application.mapper.YupaoConfigMapper;
import com.getjobs.worker.yupao.YupaoConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 鱼泡直聘配置服务
 * 负责 yupao_config 表的创建（幂等）与配置读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YupaoService {

    private final YupaoConfigMapper yupaoConfigMapper;
    private final DataSource dataSource;

    /** 启动时确保 yupao_config 表存在（幂等）。 */
    @PostConstruct
    public void ensureTableExists() {
        String ddl = "CREATE TABLE IF NOT EXISTS yupao_config (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " keywords VARCHAR(500)," +
                " city VARCHAR(50)," +
                " area VARCHAR(50)," +
                " wait_time INTEGER DEFAULT 10," +
                " max_page INTEGER DEFAULT 10," +
                " say_hi TEXT," +
                " expected_salary_min INTEGER," +
                " expected_salary_max INTEGER," +
                " created_at DATETIME," +
                " updated_at DATETIME" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
            log.info("确保 yupao_config 表已存在");
        } catch (Exception e) {
            log.warn("创建 yupao_config 表失败: {}", e.getMessage());
        }
    }

    /** 获取第一条配置（不存在则返回空对象）。 */
    public YupaoConfigEntity getFirstConfig() {
        QueryWrapper<YupaoConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        YupaoConfigEntity entity = yupaoConfigMapper.selectOne(wrapper);
        return entity != null ? entity : new YupaoConfigEntity();
    }

    /** 保存配置：存在则按 ID 更新，否则插入。 */
    public YupaoConfigEntity saveConfig(YupaoConfigEntity partial) {
        YupaoConfigEntity existing = getFirstConfig();
        LocalDateTime now = LocalDateTime.now();
        if (existing.getId() == null) {
            partial.setCreatedAt(now);
            partial.setUpdatedAt(now);
            yupaoConfigMapper.insert(partial);
            return partial;
        }
        // 选择性合并
        if (partial.getKeywords() != null) existing.setKeywords(partial.getKeywords());
        if (partial.getCity() != null) existing.setCity(partial.getCity());
        if (partial.getArea() != null) existing.setArea(partial.getArea());
        if (partial.getWaitTime() != null) existing.setWaitTime(partial.getWaitTime());
        if (partial.getMaxPage() != null) existing.setMaxPage(partial.getMaxPage());
        if (partial.getSayHi() != null) existing.setSayHi(partial.getSayHi());
        if (partial.getExpectedSalaryMin() != null) existing.setExpectedSalaryMin(partial.getExpectedSalaryMin());
        if (partial.getExpectedSalaryMax() != null) existing.setExpectedSalaryMax(partial.getExpectedSalaryMax());
        existing.setUpdatedAt(now);
        yupaoConfigMapper.updateById(existing);
        return existing;
    }

    /**
     * 加载 Worker 配置：把实体中的 JSON 关键词列表解析为 List。
     */
    public YupaoConfig loadYupaoConfig() {
        YupaoConfigEntity entity = getFirstConfig();
        YupaoConfig config = new YupaoConfig();
        config.setCity(entity.getCity());
        config.setArea(entity.getArea());
        config.setWaitTime(entity.getWaitTime());
        config.setMaxPage(entity.getMaxPage());
        config.setExpectedSalaryMin(entity.getExpectedSalaryMin());
        config.setExpectedSalaryMax(entity.getExpectedSalaryMax());
        config.setSayHi(entity.getSayHi() != null && !entity.getSayHi().isBlank()
                ? entity.getSayHi() : YupaoConfig.DEFAULT_SAY_HI);
        config.setKeywords(parseKeywords(entity.getKeywords()));
        return config;
    }

    /**
     * 解析关键词：支持 JSON 数组、括号列表、逗号分隔文本。
     */
    private List<String> parseKeywords(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return List.of();
        }
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            // 先尝试 JSON
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
                if (node.isArray()) {
                    return java.util.stream.StreamSupport
                            .stream(node.spliterator(), false)
                            .map(n -> n.asText().trim())
                            .filter(t -> !t.isEmpty())
                            .collect(Collectors.toList());
                }
            } catch (Exception ignored) {
                // 非严格 JSON（如 [a,b]），拆括号走通用解析
            }
            s = s.substring(1, s.length() - 1);
        }
        return Arrays.stream(s.split("[,，]"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(t -> t.replaceAll("^\"|\"$", ""))
                .collect(Collectors.toList());
    }
}
