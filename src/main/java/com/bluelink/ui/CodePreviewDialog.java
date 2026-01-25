package com.bluelink.ui;

import com.bluelink.util.UiUtils;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 代码预览窗口
 * 基于 RSyntaxTextArea，支持编辑、保存、编码切换和主题切换
 */
public class CodePreviewDialog extends JFrame {

    private final RSyntaxTextArea textArea;
    private final File file;
    private Charset currentCharset = StandardCharsets.UTF_8;

    // UI Components
    private JToolBar toolbar;
    private JComboBox<String> charsetCombo;
    private JToggleButton editToggle;
    private JButton saveButton;
    private JToggleButton themeToggle;
    private JLabel encodingLabel;
    private RTextScrollPane sp;

    private boolean isDarkTheme = true; // 默认暗色

    public CodePreviewDialog(File file) {
        this.file = file;
        this.textArea = new RSyntaxTextArea(30, 80);

        initUI();
        // 初始化主题 (默认暗色)
        applyTheme(isDarkTheme);

        loadFile();
    }

    private void initUI() {
        setTitle("预览: " + file.getName());
        setIconImage(UiUtils.createAppIcon());
        setLayout(new BorderLayout());

        // --- 工具栏 ---
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        // 去掉默认边框，使用 FlatLaf 风格

        // 编辑模式开关
        editToggle = new JToggleButton("启用编辑");
        editToggle.setFocusable(false);
        editToggle.addActionListener(e -> {
            boolean editable = editToggle.isSelected();
            textArea.setEditable(editable);
            saveButton.setEnabled(editable);

            // 更新按钮文本
            editToggle.setText(editable ? "退出编辑" : "启用编辑");

            // 更新窗口标题
            setTitle((editable ? "编辑: " : "预览: ") + file.getName());

            // 强制刷新光标状态
            if (editable) {
                textArea.getCaret().setVisible(true);
                textArea.setCaretColor(isDarkTheme ? Color.WHITE : Color.BLACK);
            }

            // 编辑模式下更新背景色 (如果不是暗色模式，才需要变白；暗色模式保持黑)
            if (!isDarkTheme) {
                textArea.setBackground(editable ? Color.WHITE : new Color(250, 250, 250));
            }
        });
        toolbar.add(editToggle);

        toolbar.addSeparator();

        // 保存按钮
        saveButton = new JButton("保存");
        saveButton.setFocusable(false);
        saveButton.setEnabled(false); // 默认不可用
        saveButton.addActionListener(e -> saveFile());
        toolbar.add(saveButton);

        toolbar.addSeparator();

        // 主题切换
        themeToggle = new JToggleButton("主题: Dark");
        themeToggle.setSelected(true); // 默认选中=暗色
        themeToggle.setToolTipText("切换主题 (Light/Dark)");
        themeToggle.setFocusable(false);
        themeToggle.addActionListener(e -> {
            boolean dark = themeToggle.isSelected();
            themeToggle.setText(dark ? "主题: Dark" : "主题: Light");
            applyTheme(dark);
        });
        toolbar.add(themeToggle);

        toolbar.add(Box.createHorizontalGlue());

        // 编码切换
        encodingLabel = new JLabel("编码: ");
        toolbar.add(encodingLabel);

        String[] charsets = { "UTF-8", "GBK", "GB2312", "ISO-8859-1", "US-ASCII", "UTF-16" };
        charsetCombo = new JComboBox<>(charsets);
        charsetCombo.setFocusable(false);
        charsetCombo.setSelectedItem("UTF-8");
        charsetCombo.addActionListener(e -> {
            String selectedName = (String) charsetCombo.getSelectedItem();
            try {
                Charset newCharset = Charset.forName(selectedName);
                if (!newCharset.equals(currentCharset)) {
                    currentCharset = newCharset;
                    loadFile();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "不支持的编码: " + selectedName);
            }
        });
        charsetCombo.setMaximumSize(new Dimension(100, 30));
        toolbar.add(charsetCombo);

        add(toolbar, BorderLayout.NORTH);

        // --- 编辑器 ---
        textArea.setSyntaxEditingStyle(determineSyntaxStyle(file.getName()));
        textArea.setCodeFoldingEnabled(true);
        textArea.setEditable(false);
        textArea.setAntiAliasingEnabled(true);
        textArea.setLineWrap(false);

        // --- 修复光标问题 ---
        textArea.setTextMode(org.fife.ui.rtextarea.RTextArea.INSERT_MODE);
        InputMap im = textArea.getInputMap();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_INSERT, 0), "none");

        textArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        sp = new RTextScrollPane(textArea);
        add(sp, BorderLayout.CENTER);

        setSize(1000, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void applyTheme(boolean dark) {
        this.isDarkTheme = dark;

        try {
            // 先切换全局 LookAndFeel 并只更新预览窗口
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            SwingUtilities.updateComponentTreeUI(this);

            // 立即恢复主窗口的原始主题（FlatLightLaf）
            // 注意：这里只切换主题，不刷新其他窗口的 UI
            // 避免破坏主窗口中的自定义边框设置（如 inputScroll.setBorder(null)）
            FlatLightLaf.setup();

            // 加载 RSyntaxTextArea 主题
            Theme theme;
            // 尝试加载 Monokai/Eclipse 主题
            InputStream in = getClass().getResourceAsStream(dark ? "/org/fife/ui/rsyntaxtextarea/themes/monokai.xml"
                    : "/org/fife/ui/rsyntaxtextarea/themes/eclipse.xml");

            // 如果 Monokai/Eclipse 不存在，则回退到 dark/default
            if (in == null) {
                in = getClass().getResourceAsStream(dark ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                        : "/org/fife/ui/rsyntaxtextarea/themes/default.xml");
            }

            if (in != null) {
                theme = Theme.load(in);
                theme.apply(textArea);
            } else {
                // 如果主题文件都加载失败，则手动设置颜色
                if (dark) {
                    textArea.setBackground(new Color(39, 40, 34));
                    textArea.setForeground(new Color(248, 248, 242));
                    textArea.setCaretColor(Color.WHITE);
                    textArea.setCurrentLineHighlightColor(new Color(50, 50, 50));
                } else {
                    textArea.setBackground(Color.WHITE);
                    textArea.setForeground(Color.BLACK);
                    textArea.setCaretColor(Color.BLACK);
                    textArea.setCurrentLineHighlightColor(new Color(230, 230, 230));
                }
            }

            // 显式修复光标和字体
            textArea.setCaretColor(dark ? Color.WHITE : Color.BLACK);
            textArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

            // 非编辑模式下的背景微调
            if (!dark && !editToggle.isSelected()) {
                textArea.setBackground(new Color(250, 250, 250));
            }

            // 手动设置对话框和组件的颜色（不影响全局主题）
            Color bgColor = dark ? new Color(43, 43, 43) : Color.WHITE;
            Color fgColor = dark ? Color.WHITE : Color.BLACK;
            Color toolbarBg = dark ? new Color(60, 63, 65) : new Color(240, 240, 240);
            Color borderColor = dark ? new Color(60, 63, 65) : new Color(200, 200, 200);

            // 设置对话框背景
            getContentPane().setBackground(bgColor);

            // 设置工具栏
            toolbar.setBackground(toolbarBg);
            toolbar.setForeground(fgColor);
            toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor));

            // 设置所有工具栏组件
            for (Component comp : toolbar.getComponents()) {
                comp.setBackground(toolbarBg);
                comp.setForeground(fgColor);
                if (comp instanceof AbstractButton) {
                    ((AbstractButton) comp).setFocusPainted(false);
                }
            }

            // 设置滚动面板
            sp.getViewport().setBackground(bgColor);
            sp.setBorder(BorderFactory.createLineBorder(borderColor));

            // 重绘
            repaint();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFile() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), currentCharset))) {
            textArea.read(reader, null);
            textArea.setCaretPosition(0);
            textArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        } catch (IOException e) {
            e.printStackTrace();
            textArea.setText("无法读取文件: " + e.getMessage());
        }
    }

    private void saveFile() {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), currentCharset))) {
            textArea.write(writer);
            JOptionPane.showMessageDialog(this, "文件保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String determineSyntaxStyle(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".java"))
            return SyntaxConstants.SYNTAX_STYLE_JAVA;
        if (name.endsWith(".xml"))
            return SyntaxConstants.SYNTAX_STYLE_XML;
        if (name.endsWith(".json"))
            return SyntaxConstants.SYNTAX_STYLE_JSON;
        if (name.endsWith(".css"))
            return SyntaxConstants.SYNTAX_STYLE_CSS;
        if (name.endsWith(".html"))
            return SyntaxConstants.SYNTAX_STYLE_HTML;
        if (name.endsWith(".js"))
            return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
        if (name.endsWith(".ts"))
            return SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
        if (name.endsWith(".sql"))
            return SyntaxConstants.SYNTAX_STYLE_SQL;
        if (name.endsWith(".py"))
            return SyntaxConstants.SYNTAX_STYLE_PYTHON;
        if (name.endsWith(".c"))
            return SyntaxConstants.SYNTAX_STYLE_C;
        if (name.endsWith(".cpp") || name.endsWith(".h"))
            return SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
        if (name.endsWith(".cs"))
            return SyntaxConstants.SYNTAX_STYLE_CSHARP;
        if (name.endsWith(".sh"))
            return SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
        if (name.endsWith(".bat"))
            return SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
        if (name.endsWith(".properties") || name.endsWith(".ini"))
            return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
        if (name.endsWith(".md"))
            return SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
        if (name.endsWith(".yml") || name.endsWith(".yaml"))
            return SyntaxConstants.SYNTAX_STYLE_YAML;
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    public static void preview(File file) {
        SwingUtilities.invokeLater(() -> {
            new CodePreviewDialog(file).setVisible(true);
        });
    }
}
