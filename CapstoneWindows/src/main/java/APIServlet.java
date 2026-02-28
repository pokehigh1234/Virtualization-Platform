import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/**
 * REST API Servlet for VM and Docker Container management
 */
public class APIServlet extends HttpServlet {

    private final VirtualMachineManager vmManager;
    private final DockerManager dockerManager;
    private final Gson gson;

    public APIServlet(VirtualMachineManager vmManager, DockerManager dockerManager) {
        this.vmManager = vmManager;
        this.dockerManager = dockerManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            // VM endpoints
            if (pathInfo == null || pathInfo.equals("/vms")) {
                handleGetAllVMs(resp);
            } else if (pathInfo.startsWith("/vms/")) {
                String vmId = pathInfo.substring(5);
                handleGetVM(vmId, resp);
            } else if (pathInfo.equals("/stats")) {
                handleGetStats(resp);
            }
            // Docker endpoints
            else if (pathInfo.equals("/containers")) {
                handleGetAllContainers(req, resp);
            } else if (pathInfo.equals("/containers/images")) {
                handleGetImages(resp);
            } else if (pathInfo.startsWith("/containers/") && pathInfo.endsWith("/logs")) {
                String containerId = pathInfo.substring(12, pathInfo.length() - 5);
                handleGetLogs(containerId, req, resp);
            }
            else {
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
            // VM endpoints
            if (pathInfo.equals("/vms")) {
                handleCreateVM(req, resp);
            } else if (pathInfo.matches("/vms/[^/]+/start")) {
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "start", resp);
            } else if (pathInfo.matches("/vms/[^/]+/stop")) {
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "stop", resp);
            } else if (pathInfo.matches("/vms/[^/]+/restart")) {
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "restart", resp);
            } else if (pathInfo.matches("/vms/[^/]+/pause")) {
                String vmId = extractVMId(pathInfo);
                handleVMAction(vmId, "pause", resp);
            }
            // Docker endpoints
            else if (pathInfo.equals("/containers")) {
                handleCreateContainer(req, resp);
            } else if (pathInfo.matches("/containers/[^/]+/start")) {
                String containerId = extractContainerId(pathInfo);
                handleContainerAction(containerId, "start", resp);
            } else if (pathInfo.matches("/containers/[^/]+/stop")) {
                String containerId = extractContainerId(pathInfo);
                handleContainerAction(containerId, "stop", resp);
            } else if (pathInfo.matches("/containers/[^/]+/restart")) {
                String containerId = extractContainerId(pathInfo);
                handleContainerAction(containerId, "restart", resp);
            } else if (pathInfo.equals("/containers/pull")) {
                handlePullImage(req, resp);
            }
            else {
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
            } else if (pathInfo.startsWith("/containers/")) {
                String containerId = pathInfo.substring(12);
                handleDeleteContainer(containerId, resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ===== VM Handlers =====

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

    private void handleGetVM(String vmId, HttpServletResponse resp) throws IOException {
        VirtualMachine vm = vmManager.getVM(vmId);

        if (vm == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "VM not found: " + vmId);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(vm.toJSON());
    }

    private void handleGetStats(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(vmManager.getStatistics()));
    }

    private void handleCreateVM(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        String name = json.get("name").getAsString();
        int memory = json.get("memory").getAsInt();
        int cpuCores = json.get("cpuCores").getAsInt();
        int diskSize = json.get("diskSize").getAsInt();

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

    private void handleDeleteVM(String vmId, HttpServletResponse resp) throws IOException {
        boolean success = vmManager.deleteVM(vmId);

        if (!success) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "VM not found: " + vmId);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write("{\"success\":true,\"message\":\"VM deleted\"}");
    }

    // ===== Docker Handlers =====

    private void handleGetAllContainers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean all = "true".equals(req.getParameter("all"));
        List<DockerManager.DockerContainer> containers = dockerManager.listContainers(all);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < containers.size(); i++) {
            json.append(containers.get(i).toJSON());
            if (i < containers.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(json.toString());
    }

    private void handleGetImages(HttpServletResponse resp) throws IOException {
        List<String> images = dockerManager.listImages();
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(images));
    }

    private void handleGetLogs(String containerId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int lines = 100;
        try {
            String linesParam = req.getParameter("lines");
            if (linesParam != null) {
                lines = Integer.parseInt(linesParam);
            }
        } catch (Exception e) {
            // Use default
        }

        String logs = dockerManager.getContainerLogs(containerId, lines);
        JsonObject response = new JsonObject();
        response.addProperty("logs", logs);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(response));
    }

    private void handleCreateContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        String name = json.get("name").getAsString();
        String image = json.get("image").getAsString();
        String ports = json.has("ports") ? json.get("ports").getAsString() : "";
        String volumes = json.has("volumes") ? json.get("volumes").getAsString() : "";

        boolean success = dockerManager.createContainer(name, image, ports, volumes);

        if (success) {
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write("{\"success\":true,\"message\":\"Container created\"}");
        } else {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create container");
        }
    }

    private void handleContainerAction(String containerId, String action, HttpServletResponse resp) throws IOException {
        boolean success = false;

        switch (action) {
            case "start":
                success = dockerManager.startContainer(containerId);
                break;
            case "stop":
                success = dockerManager.stopContainer(containerId);
                break;
            case "restart":
                success = dockerManager.restartContainer(containerId);
                break;
        }

        if (!success) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Container not found or action failed");
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write("{\"success\":true}");
    }

    private void handleDeleteContainer(String containerId, HttpServletResponse resp) throws IOException {
        boolean success = dockerManager.removeContainer(containerId, true);

        if (!success) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Container not found: " + containerId);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write("{\"success\":true,\"message\":\"Container deleted\"}");
    }

    private void handlePullImage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        String image = json.get("image").getAsString();
        boolean success = dockerManager.pullImage(image);

        if (success) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"success\":true,\"message\":\"Image pulled\"}");
        } else {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to pull image");
        }
    }

    // ===== Helper Methods =====

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", status);
        resp.getWriter().write(gson.toJson(error));
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = req.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private String extractVMId(String pathInfo) {
        String[] parts = pathInfo.split("/");
        return parts.length > 2 ? parts[2] : "";
    }

    private String extractContainerId(String pathInfo) {
        String[] parts = pathInfo.split("/");
        return parts.length > 2 ? parts[2] : "";
    }
}