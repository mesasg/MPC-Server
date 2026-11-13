package com.udea.chatbot;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica la capa del chat con un modelo simulado: no consume la API de Gemini ni requiere el servidor MCP. */
@SpringBootTest(properties = {
        "spring.ai.mcp.client.enabled=false",
        "spring.ai.google.genai.api-key=prueba"
})
@AutoConfigureMockMvc
@DirtiesContext
class ChatControllerTests {

    @TestConfiguration
    static class Herramientas {
        @Bean
        ToolCallbackProvider herramientasVacias() {
            return () -> new ToolCallback[0];
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChatModel modelo;

    private void modeloResponde(String texto) {
        reset(modelo);
        when(modelo.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(texto)))));
        when(modelo.getDefaultOptions()).thenReturn(null);
    }

    private org.springframework.test.web.servlet.ResultActions enviar(String sesion, String mensaje) throws Exception {
        return mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sesion\":\"" + sesion + "\",\"mensaje\":\"" + mensaje + "\"}"));
    }

    private String ultimoPrompt() {
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(modelo, atLeastOnce()).call(prompt.capture());
        return prompt.getValue().getInstructions().toString();
    }

    @Test
    void respondeYRecuerdaSoloLaConversacionDeLaMismaSesion() throws Exception {
        modeloResponde("Tenemos arroz");
        enviar("sesion-a", "quiero arroz").andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("Tenemos arroz"));

        modeloResponde("Claro");
        enviar("sesion-a", "agrega dos").andExpect(status().isOk());
        assertThat(ultimoPrompt()).contains("quiero arroz", "agrega dos");

        modeloResponde("Hola");
        enviar("sesion-b", "buenas").andExpect(status().isOk());
        assertThat(ultimoPrompt()).doesNotContain("quiero arroz");
    }

    @Test
    void reiniciarBorraLaMemoriaDeLaSesion() throws Exception {
        modeloResponde("Listo");
        enviar("sesion-c", "dato anterior").andExpect(status().isOk());

        mvc.perform(delete("/api/chat/sesion-c")).andExpect(status().isNoContent());

        modeloResponde("Hola");
        enviar("sesion-c", "nuevo").andExpect(status().isOk());
        assertThat(ultimoPrompt()).doesNotContain("dato anterior");
    }

    @Test
    void mensajeVacioEsRechazado() throws Exception {
        enviar("sesion-d", "   ").andExpect(status().isBadRequest());
    }

    @Test
    void fallaDelModeloResponde502() throws Exception {
        reset(modelo);
        when(modelo.getDefaultOptions()).thenReturn(null);
        when(modelo.call(any(Prompt.class))).thenThrow(new RuntimeException("API key inválida"));

        enviar("sesion-e", "hola").andExpect(status().isBadGateway());
    }
}
