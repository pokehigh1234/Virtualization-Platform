package com.example.kvm.websocket;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * VNC WebSocket Handler - Proxies WebSocket connections to local VNC servers.
 * Allows web browsers to connect to VNC via WebSocket tunnel.
 */
@Component
public class VNCWebSocketHandler extends AbstractWebSocketHandler {

    private final Map<String, VNCConnection> connections = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final String VNC_HOST = "localhost";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String vmName = session.getAttributes().get("vmName").toString();
        String port = session.getAttributes().get("port").toString();
        
        System.out.println("WebSocket connected for VM: " + vmName + " on port: " + port);
        
        try {
            int vncPort = Integer.parseInt(port);
            VNCConnection vncConn = new VNCConnection(session, VNC_HOST, vncPort);
            connections.put(session.getId(), vncConn);
            
            // Start reading from VNC server in background thread
            executorService.execute(vncConn::readFromVNC);
        } catch (Exception e) {
            System.err.println("Failed to connect to VNC: " + e.getMessage());
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        VNCConnection vncConn = connections.get(session.getId());
        if (vncConn != null && vncConn.isConnected()) {
            vncConn.sendToVNC(message.getPayload().array());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        VNCConnection vncConn = connections.remove(session.getId());
        if (vncConn != null) {
            vncConn.close();
            System.out.println("WebSocket disconnected for session: " + session.getId());
        }
    }

    /*
     * Inner class representing a single VNC connection through WebSocket.
     */
    private class VNCConnection {
        private WebSocketSession webSocketSession;
        private Socket vncSocket;
        private InputStream vncInput;
        private OutputStream vncOutput;
        private volatile boolean connected = false;

        VNCConnection(WebSocketSession webSocketSession, String host, int port) throws IOException {
            this.webSocketSession = webSocketSession;
            this.vncSocket = new Socket(host, port);
            this.vncInput = vncSocket.getInputStream();
            this.vncOutput = vncSocket.getOutputStream();
            this.connected = true;
        }

        void readFromVNC() {
            try {
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while (connected && (bytesRead = vncInput.read(buffer)) != -1) {
                    if (webSocketSession.isOpen()) {
                        webSocketSession.sendMessage(new BinaryMessage(ByteBuffer.wrap(buffer, 0, bytesRead)));
                    } else {
                        connected = false;
                        break;
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("Error reading from VNC: " + e.getMessage());
                }
                connected = false;
                try {
                    webSocketSession.close();
                } catch (IOException ex) {
                    // Already closed
                }
            }
        }

        void sendToVNC(byte[] data) throws IOException {
            if (connected && vncOutput != null) {
                vncOutput.write(data);
                vncOutput.flush();
            }
        }

        boolean isConnected() {
            return connected;
        }

        void close() {
            connected = false;
            try {
                if (vncInput != null) vncInput.close();
                if (vncOutput != null) vncOutput.close();
                if (vncSocket != null) vncSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing VNC connection: " + e.getMessage());
            }
        }
    }
}
