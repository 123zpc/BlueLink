package com.bluelink.util;

import com.vdurmont.emoji.EmojiParser;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.List;

/**
 * Markdown 工具类
 * 使用 commonmark-java 进行解析
 */
public class MarkdownUtils {

    private static final Parser parser;
    private static final HtmlRenderer renderer;

    static {
        List<Extension> extensions = Arrays.asList(TablesExtension.create());
        parser = Parser.builder()
            .extensions(extensions)
            .build();
        renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .escapeHtml(true) // 防止 XSS
            .build();
    }

    /**
     * 将 Markdown 转换为适合 Swing HTML3.2 渲染的 HTML
     */
    public static String markdownToHtml(String markdown) {
        if (markdown == null) return "";

        // 1. Markdown -> HTML
        Node document = parser.parse(markdown);
        String html = renderer.render(document);
        
        // CRITICAL FIX: Swing HTML3.2 不支持相对路径图片，也不支持某些 HTTPS 图片加载（取决于实现）。
        // 这里的图片链接如果是 Markdown 里的 ![](url)，commonmark 会直接输出 <img src="url" ... />
        // 我们需要确保图片能被加载。
        // 如果是 Base64 图片，Swing 支持。如果是网络图片，Swing 需要异步加载或者自定义 View。
        // 但最简单的方法是，如果图片无法显示，可能是 HTML 结构问题。
        // 很多 Markdown 渲染器输出的 img 标签没有闭合或者属性问题。commonmark 输出的是 <img src="" alt="" /> (XHTML)
        // Swing HTML3.2 可能更喜欢 <img src="">
        
        // 另外，给 img 增加最大宽度限制，防止撑破气泡
        // 但 Swing HTML 不支持 max-width CSS。只能通过 width 属性。
        // 这是一个难点。我们暂时不处理宽度，依赖 Swing 自己的缩放（通常不缩放）。
        
        // 2. Emoji -> Twemoji Image Tags
        // 使用 emoji-java 将 Unicode Emoji 转换为 Twemoji 图片链接
        html = EmojiParser.parseToUnicode(html); // 确保是 Unicode (虽然一般已经是)
        
        // 自定义转换逻辑：Unicode -> <img src="...">
        // 注意：我们直接操作 HTML 字符串，这可能有一些风险，但对于 Emoji 来说相对安全
        html = EmojiParser.parseFromUnicode(html, unicodeCandidate -> {
            String hex = unicodeCandidate.getEmoji().getHtmlHexadecimal().replace("&#x", "").replace(";", "");
            // emoji-java 的 hex 可能包含前导0或大小写问题，以及多码点问题
            // 更可靠的方法是获取 codepoints
            // 但为了简单，我们尝试构造 Twemoji URL
            // Twemoji URL 需要小写的 hex code，多码点用 - 连接
            
            // 手动构建正确的 hex string
            StringBuilder sb = new StringBuilder();
            String unicode = unicodeCandidate.getEmoji().getUnicode();
            for (int i = 0; i < unicode.length(); i++) {
                int codePoint = unicode.codePointAt(i);
                if (Character.isHighSurrogate(unicode.charAt(i))) continue;
                if (sb.length() > 0) sb.append("-");
                sb.append(Integer.toHexString(codePoint).toLowerCase());
            }
            // 上面的循环有问题，Character.codePointAt 处理 surrogate pairs 需要小心
            
            // 更简单的方法：使用 emoji-java 内部逻辑或者 unicode 字符串转换
            String code = unicodeToHex(unicodeCandidate.getEmoji().getUnicode());
            
            // 使用 EmojiLoader 获取 URL (支持本地缓存)
            String url = EmojiLoader.getEmojiUrl(code);
            
            // 返回 img 标签，垂直居中对齐
            return "<img src='" + url + "' width='16' height='16' style='vertical-align:middle'>";
        });

        // 后处理：优化 Swing 的渲染效果 (HTML 3.2 兼容)

        // 3. 代码块样式优化
        // 模仿现代 AI 聊天窗口 (ChatGPT/Gemini)
        // 深色圆角风格 (在 HTML 3.2 中用表格模拟圆角很难，我们用深色背景+浅色文字)
        // 增加一个 "header" 栏显示 "Code" 字样，未来可以作为复制按钮的锚点
        
        // 黑色背景 #1e1e1e, 浅色文字 #d4d4d4
        // 头部背景 #2d2d2d
        
        html = html.replace("<pre>", 
            // 外层表格：边框色/背景色 #1e1e1e
            "<table width='100%' border='0' cellspacing='0' cellpadding='0' bgcolor='#1e1e1e'>" +
            // 头部：Code 标签
            "<tr><td bgcolor='#2d2d2d' style='padding:4px 8px;'><font color='#a0a0a0' size='-1'>Code</font></td></tr>" +
            // 内容区域
            "<tr><td style='padding:8px;'><pre><font color='#d4d4d4'>");
            
        html = html.replace("</pre>", 
            "</font></pre></td></tr></table><br>"); // 增加底部间距
        
        // 4. 行内代码 <code> 增加样式
        // 模仿 ChatGPT：深色背景，橙色文字
        // #f7f7f8 (light) or #2d2d2d (dark) -> 这里我们用浅灰背景+深红文字，类似 Notion/GitHub
        html = html.replace("<code>", "<font face='Monospaced' color='#c7254e' style='background-color:#f9f2f4'>"); 
        html = html.replace("</code>", "</font>");

        // 5. 标题样式调整
        // 增加上下间距，加粗
        html = html.replace("<h1>", "<br><h3><b>").replace("</h1>", "</b></h3>");
        html = html.replace("<h2>", "<br><h4><b>").replace("</h2>", "</b></h4>");
        html = html.replace("<h3>", "<br><b>").replace("</h3>", "</b><br>"); // h3 转为粗体文本

        
        // 6. 表格样式 (Flat Style)
        // 同样使用 cellspacing=1 trick
        // 表头背景 #e0e0e0, 单元格背景 #ffffff
        html = html.replace("<table>", "<table width='100%' border='0' cellspacing='1' cellpadding='4' bgcolor='#cccccc'>");
        html = html.replace("<thead>", "<thead bgcolor='#e0e0e0'>");
        html = html.replace("<tbody>", "<tbody bgcolor='#ffffff'>");
        // 注意: commonmark 可能不输出 tbody/thead，而是直接 tr。
        // 如果没有 thead/tbody，我们需要处理 tr。
        // 但为了简单，我们假设 commonmark 输出标准结构。如果不输出，我们全局替换 tr 可能会误伤代码块里的 tr (如果代码块里有 table... 虽然代码块里的 html 会被转义)
        // 更安全的做法是依靠 bgcolor='#ffffff' 在 tbody 上 (如果存在) 或者在 table 上
        // 这里我们给 table 设了边框色，所以 cell 必须设背景色，否则就是边框色。
        // 简单暴力：把 <td> 替换为 <td bgcolor='#ffffff'>
        html = html.replace("<td>", "<td bgcolor='#ffffff'>");
        html = html.replace("<th>", "<th bgcolor='#f0f0f0'>");

        // 7. 分割线 <hr>
        html = html.replace("<hr />", "<table width='100%' border='0' cellspacing='0' cellpadding='0' bgcolor='#dddddd' height='1'><tr><td></td></tr></table>");
        
        // 8. 包裹基本样式
        // 回退到 Microsoft YaHei，因为 Emoji 已经变成了图片
        // 增加行高 (line-height 在 Swing HTML 中支持有限，通过 margin/padding 调整)
        // 整体字体颜色 #333333，背景透明 (由外部组件控制)
        return "<html><body style='font-family: \"Microsoft YaHei\", sans-serif; font-size:12px; color:#333333; margin:0; padding:0;'>" + html + "</body></html>";
    }

    /**
     * 将 Unicode 字符串转换为 Twemoji 兼容的 Hex 字符串
     * e.g. 😀 -> 1f600
     *      👨‍👩‍👧 -> 1f468-200d-1f469-200d-1f467
     */
    private static String unicodeToHex(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            int codePoint = str.codePointAt(i);
            if (Character.isSupplementaryCodePoint(codePoint)) {
                i++; // Skip low surrogate
            }
            // 移除 Variation Selectors (FE0F) 以匹配 Twemoji 命名规范
            // 但有些 Emoji 必须带 FE0F 吗？Twemoji 通常移除 FE0F
            if (codePoint == 0xFE0F) continue;

            if (sb.length() > 0) sb.append("-");
            sb.append(Integer.toHexString(codePoint).toLowerCase());
        }
        return sb.toString();
    }
}
