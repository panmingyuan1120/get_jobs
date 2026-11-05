package com.getjobs.application.init;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.BossOptionEntity;
import com.getjobs.application.mapper.BossOptionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 应用启动时初始化 Boss 区县选项数据（type=area）：
 * 1) 确保 boss_option 表存在 parent_code 列（列迁移由 BossService 负责，这里做兜底）
 * 2) 从 defaults/area-seed.json 读取区县种子数据（约 3000 条，覆盖 337 个城市）
 *    - parent_code = 所属城市的 Boss 城市码（如 101210100=杭州）
 *    - code = 国标行政区划码（如 330110=余杭区），对应 Boss 搜索 URL 的 areaBusiness 参数
 * 3) 幂等插入：已存在（type+code+parent_code 相同）则跳过
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AreaOptionInitializer implements CommandLineRunner {

    private static final String SEED_PATH = "defaults/area-seed.json";

    private final DataSource dataSource;
    private final BossOptionMapper bossOptionMapper;

    @Override
    public void run(String... args) {
        ensureParentCodeColumn();
        seedAreaOptions();
    }

    private void ensureParentCodeColumn() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            Set<String> names = new HashSet<>();
            try (ResultSet columns = stmt.executeQuery("PRAGMA table_info('boss_option')")) {
                while (columns.next()) {
                    names.add(columns.getString("name"));
                }
            }
            if (!names.isEmpty() && !names.contains("parent_code")) {
                stmt.executeUpdate("ALTER TABLE boss_option ADD COLUMN parent_code VARCHAR(50)");
                log.info("已添加 boss_option.parent_code 列");
            }
        } catch (Exception e) {
            log.warn("检查 boss_option.parent_code 列失败: {}", e.getMessage());
        }
    }

    private void seedAreaOptions() {
        JsonNode seed;
        try {
            ObjectMapper mapper = new ObjectMapper();
            try (var is = new ClassPathResource(SEED_PATH).getInputStream()) {
                seed = mapper.readTree(is);
            }
        } catch (Exception e) {
            log.warn("读取区县种子数据 {} 失败: {}", SEED_PATH, e.getMessage());
            return;
        }
        if (seed == null || !seed.isArray() || seed.isEmpty()) {
            log.warn("区县种子数据为空，跳过初始化");
            return;
        }

        // 已存在的区县选项：type+code+parent_code 联合判定
        Set<String> existingKeys = new HashSet<>();
        QueryWrapper<BossOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", "area");
        wrapper.select("code", "parent_code");
        for (BossOptionEntity e : bossOptionMapper.selectList(wrapper)) {
            existingKeys.add(e.getCode() + "@" + (e.getParentCode() == null ? "" : e.getParentCode()));
        }

        int inserted = 0;
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode item : seed) {
            String code = item.path("code").asText(null);
            String name = item.path("name").asText(null);
            String parentCode = item.path("parentCode").asText(null);
            if (code == null || code.isBlank() || name == null || name.isBlank()) {
                continue;
            }
            String key = code + "@" + (parentCode == null ? "" : parentCode);
            if (existingKeys.contains(key)) {
                continue;
            }
            BossOptionEntity entity = new BossOptionEntity();
            entity.setType("area");
            entity.setName(name);
            entity.setCode(code);
            entity.setParentCode(parentCode);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            try {
                bossOptionMapper.insert(entity);
                inserted++;
            } catch (Exception ex) {
                log.debug("插入区县选项 {} 失败: {}", name, ex.getMessage());
            }
        }
        if (inserted > 0) {
            log.info("已初始化 {} 条 Boss 区县选项（城市级联筛选用）", inserted);
        } else {
            log.debug("Boss 区县选项已就绪，无需插入");
        }
    }
}
