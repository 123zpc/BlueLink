package com.bluelink;

import com.bluelink.ai.SpringAiService;
import com.bluelink.ui.ModernQQFrame;
import com.bluelink.util.UiUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.SwingUtilities;

@SpringBootApplication(exclude = {org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class})
public class Main {
    public static void main(String[] args) {
        // 确保数据库初始化
        com.bluelink.db.DatabaseManager.initDatabase();

        // 启动 Spring Context
        SpringApplicationBuilder builder = new SpringApplicationBuilder(Main.class);
        builder.headless(false); // 允许 Swing
        builder.web(WebApplicationType.NONE); // 非 Web 应用
        ConfigurableApplicationContext context = builder.run(args);

        SwingUtilities.invokeLater(() -> {
            UiUtils.initTheme();
            ModernQQFrame frame = new ModernQQFrame();
            
            // 注入 AI 服务
            try {
                SpringAiService aiService = context.getBean(SpringAiService.class);
                frame.setAiService(aiService);
            } catch (Exception e) {
                System.err.println("Warning: SpringAiService not found: " + e.getMessage());
            }

            frame.setVisible(true);
        });
    }
}
