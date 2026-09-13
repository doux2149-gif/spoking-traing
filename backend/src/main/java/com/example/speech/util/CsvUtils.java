package com.example.speech.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CSV 导出工具: UTF-8 BOM(Excel 中文不乱码)、统一响应头、字段转义。
 */
public final class CsvUtils {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvUtils() {
    }

    /** 设置 CSV 下载响应头并写入 BOM, 返回输出流供调用方继续写行 */
    public static OutputStream prepareCsvResponse(HttpServletResponse response, String fileBaseName) throws IOException {
        String fileName = fileBaseName + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        OutputStream out = response.getOutputStream();
        // UTF-8 BOM, Excel 打开中文不乱码
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        return out;
    }

    /** RFC 4180 转义: 含逗号/引号/换行时用双引号包裹, 内部引号双写; null 输出空串 */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_TIME);
    }

    /** 拼接一行 CSV(CRLF 结尾), 每个单元格按规则转义 */
    public static String row(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
