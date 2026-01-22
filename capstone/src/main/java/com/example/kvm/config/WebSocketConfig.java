package com.example.kvm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.kvm.websocket.VNCWebSocketHandler;
import com.example.kvm.websocket.VNCHandshakeInterceptor;

/*
 * WebSocket configuration for VNC proxy connections.
 * Enables WebSocket support and registers the VNC handler.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VNCWebSocketHandler vncWebSocketHandler;
    private final VNCHandshakeInterceptor vncHandshakeInterceptor;

    public WebSocketConfig(VNCWebSocketHandler vncWebSocketHandler, VNCHandshakeInterceptor vncHandshakeInterceptor) {
        this.vncWebSocketHandler = vncWebSocketHandler;
        this.vncHandshakeInterceptor = vncHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register the VNC WebSocket handler at /ws/vnc/{vmName}
        registry.addHandler(vncWebSocketHandler, "/ws/vnc/{vmName}")
                .addInterceptors(vncHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
