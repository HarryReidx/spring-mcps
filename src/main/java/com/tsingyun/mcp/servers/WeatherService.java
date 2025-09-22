package com.tsingyun.mcp.servers;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WeatherService {

    @Tool(description = "根据城市名称获取天气预报")
    public String getWeatherByCity(String city) {
//        Map<String, String> mockData = Map.of(
//                "西安", "晴天",
//                "北京", "小雨",
//                "上海", "大雨"
//        );
        HashMap<Object, Object> mockData = new HashMap<>();
        mockData.put("西安", "晴天");
        mockData.put("北京", "小雨");
        mockData.put("上海", "大雨");

        return mockData.getOrDefault(city, "抱歉：未查询到对应城市！").toString();
    }

}