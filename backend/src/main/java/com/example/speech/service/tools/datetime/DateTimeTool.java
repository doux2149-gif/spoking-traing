package com.example.speech.service.tools.datetime;

import com.example.speech.service.tools.LlmTool;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * 时间日期工具: 查询当前日期、星期、时间。
 * <p>解决模型无法回答"今天周几/现在几点"等实时时间问题的场景。</p>
 */
@Component
public class DateTimeTool implements LlmTool {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String[] WEEKDAYS = {
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"
    };

    @Override
    public String name() {
        return "get_current_datetime";
    }

    @Override
    public String description() {
        return "获取当前的日期、星期和时间。当用户询问今天是几月几号、今天星期几、现在几点"
                + "等与实时日期时间相关的问题时, 必须调用此工具获取准确信息, 不要凭记忆猜测。";
    }

    @Override
    public String parameterSchema() {
        // 无参数工具
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override
    public String execute(JsonNode arguments) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        return String.format(
                "今天是%d年%d月%d日，%s，当前时间 %02d:%02d:%02d（时区：%s）。",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                WEEKDAYS[now.getDayOfWeek().getValue() - 1],
                now.getHour(), now.getMinute(), now.getSecond(),
                ZONE.getId());
    }
}
