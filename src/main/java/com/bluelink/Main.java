package com.bluelink;

import com.bluelink.ai.SpringAiService;
import com.bluelink.ui.ModernQQFrame;
import com.bluelink.util.SingleInstanceLock;
import com.bluelink.util.UiUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootApplication(exclude = {org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class})
public class Main {
    public static void main(String[] args) {
        // 0. 单实例检测
        if (SingleInstanceLock.notifyExistingInstance()) {
            System.out.println("检测到应用已在运行，已发送唤醒信号。");
            System.exit(0);
        }
        
        // 启动监听 (在 Spring 启动前占用端口，防止并发启动竞争)
        SingleInstanceLock.startServer();

        // 总是设置 Embedded Redis 相关属性，因为我们移除了 @ConditionalOnProperty
        // 但控制权交给了 EmbeddedRedisConfig
        if (true) {
            System.setProperty("app.redis.enabled-embedded", "true");
            // 使用冷门端口防止冲突 (26379)
            String redisPort = "26379";
            System.setProperty("app.redis.port", redisPort);
            System.setProperty("spring.data.redis.port", redisPort);
        }

        System.setProperty("logging.level.com.bluelink", "DEBUG");
        System.setProperty("spring.main.lazy-initialization", "true");

        // 启动 Spring Context
        SpringApplicationBuilder builder = new SpringApplicationBuilder(Main.class);
        builder.headless(false); // 允许 Swing
        builder.web(WebApplicationType.NONE); // 非 Web 应用

        AtomicReference<ModernQQFrame> frameRef = new AtomicReference<>();
        CountDownLatch frameReady = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            UiUtils.initTheme();
            ModernQQFrame frame = new ModernQQFrame();
            
            // 注册单实例锁的窗口引用
            SingleInstanceLock.registerFrame(frame);
            
            frame.setVisible(true);
            frameRef.set(frame);
            frameReady.countDown();
        });

        new Thread(() -> {
            try {
                frameReady.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                com.bluelink.db.DatabaseManager.initDatabase();
            } catch (Throwable t) {
                System.err.println("Database init failed: " + t.getMessage());
            }

            try {
                ConfigurableApplicationContext context = builder.run(args);
                SpringAiService aiService = context.getBean(SpringAiService.class);
                ModernQQFrame frame = frameRef.get();
                if (frame != null) {
                    SwingUtilities.invokeLater(() -> frame.setAiService(aiService));
                }
            } catch (Exception e) {
                System.err.println("Warning: SpringAiService not ready: " + e.getMessage());
            }
        }, "app-bootstrap").start();
    }
}
