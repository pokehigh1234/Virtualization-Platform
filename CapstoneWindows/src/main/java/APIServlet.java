import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/**
 * REST API Servlet for Virtual Machine management
 */
public class APIServlet extends HttpServlet {

    private final VirtualMachineManager vmManager;
    private final Gson gson;

    public APIServlet(VirtualMachineManager vmManager) {
        this.vmManager = vmManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Add listener for VM status changes
        vmManager.addListener(VirtualizationWebSocket::notifyVMStatusChange);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo == null || pathInfo.equals("/vms")) {
                // Get all VMs
                handleGetAllVMs(resp);
            } else if (pathInfo.startsWith("/vms/")) {
                // Get specific VM
                String vmId = pathInfo.substring(5);
                handleGetVM(vmId, resp);
            } else if (pathInfo.equals("/stats")) {
                // Get statistics
                handleGetStats(resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo.equals("/vms")) {
                // Create new VM
                handleCreateVM(req, resp);
            } else if (pathInfo.matches("/vms/[^/]+/start")) {
                // Start VM
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "start", resp);
            } else if (pathInfo.matches("/vms/[^/]+/stop")) {
                // Stop VM
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "stop", resp);
            } else if (pathInfo.matches("/vms/[^/]+/restart")) {
                // Restart VM
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "restart", resp);
            } else if (pathInfo.matches("/vms/[^/]+/pause")) {
                // Pause VM
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "pause", resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo.startsWith("/vms/")) {
                String vmId = pathInfo.substring(5);
                handleDeleteVM(vmId, resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Handle GET all VMs
     */
    private void handleGetAllVMs(HttpServletResponse resp) throws IOException {
        List<VirtualMachine> vms = vmManager.getAllVMs();
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < vms.size(); i++) {
            json.append(vms.get(i).toJSON());
            if (i < vms.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(json.toString());
    }

    /**
     * Handle GET specific VM
     */
    private void handleGetVM(String vmId, HttpServletResponse resp) throws IOException {
        VirtualMachine vm = vmManager.getVM(vmId);

        if (vm == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "VM not found: " + vmId);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(vm.toJSON());
    }

    /**
     * Handle GET statistics
     */
    private void handleGetStats(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(vmManager.getStatistics()));
    }

    /**
     * Handle POST create VM
     */
    private void handleCreateVM(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        String name = json.get("name").getAsString();
        int memory = json.get("memory").getAsInt();
        int cpuCores = json.get("cpuCores").getAsInt();
        int diskSize = json.get("diskSize").getAsInt();

        // Optional parameters
        String storageLocation = json.has("storageLocation") && !json.get("storageLocation").isJsonNull()
                ? json.get("storageLocation").getAsString() : null;
        String isoPath = json.has("isoPath") && !json.get("isoPath").isJsonNull()
                ? json.get("isoPath").getAsString() : null;

        try {
            VirtualMachine vm = vmManager.createVM(name, memory, cpuCores, diskSize,
                    storageLocation, isoPath);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write(vm.toJSON());
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Handle VM actions (start, stop, restart, pause)
     */
    private void handleVMAction(String vmId, String action, HttpServletResponse resp) throws IOException {
        boolean success = false;

        switch (action) {
            case "start":
                success = vmManager.startVM(vmId);
                break;
            case "stop":
                success = vmManager.stopVM(vmId);
                break;
            case "restart":
                success = vmManager.restartVM(vmId);
                break;
            case "pause":
                success = vmManager.pauseVM(vmId);
                break;
        }

        if (!success) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "VM not found or action failed");
            return;
        }

        VirtualMachine vm = vmManager.getVM(vmId);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(vm.toJSON());
    }

    /**
     * Handle DELETE VM
     */
    private void handleDeleteVM(String vmId, HttpServletResponse resp) throws IOException {
        boolean success = vmManager.deleteVM(vmId);

        if (!success) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "VM not found: " + vmId);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write("{\"success\":true,\"message\":\"VM deleted\"}");
    }

    /**
     * Send error response
     */
    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", status);
        resp.getWriter().write(gson.toJson(error));
    }

    /**
     * Read request body
     */
    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = req.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Extract VM ID from path
     */
    private String extractVMId(String pathInfo) {
        String[] parts = pathInfo.split("/");
        return parts.length > 2 ? parts[2] : "";
    }
}