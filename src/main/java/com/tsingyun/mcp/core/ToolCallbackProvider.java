package com.tsingyun.mcp.core;

import com.tsingyun.mcp.servers.WeatherService;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建工具回调提供者
 */
@Configuration
public class ToolCallbackProvider {
    @Bean
    public MethodToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }

}