package com.udea.chatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    public record Peticion(@NotBlank @Size(max = 64) String sesion, @NotBlank @Size(max = 2000) String mensaje) {
    }

    public record Respuesta(String respuesta) {
    }

    private final ChatClient chat;
    private final ChatMemory memoria;

    public ChatController(ChatClient chat, ChatMemory memoria) {
        this.chat = chat;
        this.memoria = memoria;
    }

    @PostMapping
    public Respuesta conversar(@Valid @RequestBody Peticion peticion) {
        try {
            String texto = chat.prompt()
                    .user(peticion.mensaje())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, peticion.sesion()))
                    .call()
                    .content();
            return new Respuesta(texto == null ? "" : texto);
        } catch (RuntimeException e) {
            log.error("No fue posible obtener respuesta del modelo o del servidor MCP", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        }
    }

    @DeleteMapping("/{sesion}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reiniciar(@PathVariable String sesion) {
        memoria.clear(sesion);
    }
}
