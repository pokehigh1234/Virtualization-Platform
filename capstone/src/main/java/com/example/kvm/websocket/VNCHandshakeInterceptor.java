package com.example.kvm.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.stereotype.Component;

import com.example.kvm.service.KvmService;

import java.util.Map;

/*
 * WebSocket Handshake Interceptor - Extracts VM name and port before connection.
 */
@Component
public class VNCHandshakeInterceptor implements HandshakeInterceptor {

    private final KvmService kvmService;

    public VNCHandshakeInterceptor(KvmService kvmService) {
        this.kvmService = kvmService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // Extract VM name from URI path
        String path = request.getURI().getPath();
        String[] parts = path.split("/");
        if (parts.length > 0) {
            String vmName = parts[parts.length - 1];
            attributes.put("vmName", vmName);
            
            try {
                // Get VNC port for this VM
                int port = kvmService.getVNCPortByName(vmName);
                attributes.put("port", String.valueOf(port));
                System.out.println("VNC Handshake: VM=" + vmName + " Port=" + port);
            } catch (Exception e) {
                System.err.println("Failed to get VNC port for " + vmName + ": " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            System.err.println("WebSocket handshake failed: " + exception.getMessage());
        }
    }
}
