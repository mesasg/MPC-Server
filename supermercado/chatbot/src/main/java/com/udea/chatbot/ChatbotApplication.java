package com.udea.chatbot;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }

    /**
     * Une las tres piezas del chatbot: el modelo (builder autoconfigurado), las herramientas
     * descubiertas por el cliente MCP y la memoria de cada conversación.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          ToolCallbackProvider herramientasMcp,
                          ChatMemory memoria,
                          @Value("classpath:prompts/sistema.txt") Resource instrucciones) {
        return builder
                .defaultSystem(instrucciones)
                .defaultToolCallbacks(herramientasMcp)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memoria).build())
                .build();
    }
}
