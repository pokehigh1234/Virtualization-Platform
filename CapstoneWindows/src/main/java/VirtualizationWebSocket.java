import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket endpoint for real-time VM communication
 */
@WebSocket
public class VirtualizationWebSocket {

    private static final Map<Session, String> sessions = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static VirtualMachineManager vmManager;

    public static void setVMManager(VirtualMachineManager manager) {
        vmManager = manager;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        sessions.put(session, null);
        System.out.println("Client connected: " + session.getRemoteAddress());

        // Check if VM Manager is initialized
        if (vmManager == null) {
            System.err.println("WARNING: VirtualMachineManager not initialized for WebSocket");
            sendMessage(session, createMessage("system", "Server initializing, please wait...", false));
            return;
        }

        // Send welcome message
        sendMessage(session, createMessage("system", "Connected to Web Virtualization Platform", false));
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        System.out.println("Client disconnected: " + session.getRemoteAddress());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();

            switch (type) {
                case "command":
                    handleCommand(session, json);
                    break;
                case "subscribe_vm":
                    handleSubscribe(session, json);
                    break;
                case "ping":
                    sendMessage(session, createMessage("pong", "pong", false));
                    break;
                default:
                    sendMessage(session, createMessage("error", "Unknown message type: " + type, true));
            }
        } catch (Exception e) {
            sendMessage(session, createMessage("error", "Error processing message: " + e.getMessage(), true));
        }
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        System.err.println("WebSocket error: " + error.getMessage());
    }

    private void handleCommand(Session session, JsonObject json) {
        String vmId = json.has("vmId") ? json.get("vmId").getAsString() : null;
        String command = json.get("command").getAsString();

        if (vmManager == null) {
            sendMessage(session, createMessage("terminal", "Error: VM Manager not initialized", true));
            return;
        }

        String output;
        if (vmId == null || vmId.isEmpty()) {
            output = "Error: No VM selected. Please select a VM first.";
            sendMessage(session, createMessage("terminal", output, true));
        } else {
            output = vmManager.executeCommand(vmId, command);
            sendMessage(session, createMessage("terminal", output, output.startsWith("Error:")));
        }
    }

    private void handleSubscribe(Session session, JsonObject json) {
        String vmId = json.get("vmId").getAsString();
        sessions.put(session, vmId);
        sendMessage(session, createMessage("system", "Subscribed to VM: " + vmId, false));
    }

    /**
     * Broadcast a message to all connected clients
     */
    public static void broadcast(String messageJson) {
        for (Session session : sessions.keySet()) {
            sendMessage(session, messageJson);
        }
    }

    /**
     * Send message to specific VM subscribers
     */
    public static void sendToVMSubscribers(String vmId, String messageJson) {
        for (Map.Entry<Session, String> entry : sessions.entrySet()) {
            if (vmId.equals(entry.getValue())) {
                sendMessage(entry.getKey(), messageJson);
            }
        }
    }

    /**
     * Send a message to a specific session
     */
    private static void sendMessage(Session session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.getRemote().sendString(message);
            } catch (IOException e) {
                System.err.println("Error sending message: " + e.getMessage());
            }
        }
    }

    /**
     * Create a JSON message
     */
    private static String createMessage(String type, String content, boolean isError) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        message.addProperty("output", content);
        message.addProperty("error", isError);
        message.addProperty("timestamp", System.currentTimeMillis());
        return gson.toJson(message);
    }

    /**
     * Notify all clients of VM status change
     */
    public static void notifyVMStatusChange(VirtualMachine vm) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "vm_status");
        message.addProperty("vmId", vm.getId());
        message.addProperty("status", vm.getStatus().toString().toLowerCase());
        message.addProperty("cpuUsage", vm.getCpuUsage());
        message.addProperty("memoryUsage", vm.getMemoryUsage());

        broadcast(gson.toJson(message));
    }
}