package com.udea.supermercado;

import com.udea.supermercado.service.SupermercadoToolsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SupermercadoMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupermercadoMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider supermercadoTools(SupermercadoToolsService tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
