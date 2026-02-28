import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Docker Container Manager
 * Manages Docker containers through Docker CLI
 */
public class DockerManager {
    
    private boolean dockerAvailable = false;
    private String dockerCommand;
    
    public DockerManager() {
        detectDocker();
    }
    
    /**
     * Detect Docker installation
     */
    private void detectDocker() {
        String[] possibleCommands = {"docker", "docker.exe"};
        
        for (String cmd : possibleCommands) {
            if (testDockerCommand(cmd)) {
                dockerCommand = cmd;
                dockerAvailable = true;
                System.out.println("✓ Docker found: " + cmd);
                return;
            }
        }
        
        System.err.println("⚠ Docker not found. Install from: https://www.docker.com/");
        dockerAvailable = false;
    }
    
    /**
     * Test if Docker command works
     */
    private boolean testDockerCommand(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if Docker is available
     */
    public boolean isAvailable() {
        return dockerAvailable;
    }
    
    /**
     * List all containers
     */
    public List<DockerContainer> listContainers(boolean all) {
        List<DockerContainer> containers = new ArrayList<>();
        
        if (!dockerAvailable) {
            return containers;
        }
        
        try {
            String[] cmd = all ? 
                new String[]{dockerCommand, "ps", "-a", "--format", "{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}"} :
                new String[]{dockerCommand, "ps", "--format", "{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}"};
            
            String output = executeCommandWithOutput(cmd);
            
            for (String line : output.split("\n")) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 4) {
                    DockerContainer container = new DockerContainer();
                    container.setId(parts[0].trim());
                    container.setName(parts[1].trim());
                    container.setImage(parts[2].trim());
                    container.setStatus(parts[3].trim());
                    container.setPorts(parts.length > 4 ? parts[4].trim() : "");
                    containers.add(container);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to list containers: " + e.getMessage());
        }
        
        return containers;
    }
    
    /**
     * Create and run a new container
     */
    public boolean createContainer(String name, String image, String ports, String volumes) {
        if (!dockerAvailable) {
            System.out.println("Docker not available - container not created");
            return false;
        }
        
        try {
            List<String> command = new ArrayList<>();
            command.add(dockerCommand);
            command.add("run");
            command.add("-d");
            command.add("--name");
            command.add(name);
            
            // Add port mappings
            if (ports != null && !ports.trim().isEmpty()) {
                for (String port : ports.split(",")) {
                    port = port.trim();
                    if (!port.isEmpty()) {
                        command.add("-p");
                        command.add(port);
                    }
                }
            }
            
            // Add volume mappings
            if (volumes != null && !volumes.trim().isEmpty()) {
                for (String volume : volumes.split(",")) {
                    volume = volume.trim();
                    if (!volume.isEmpty()) {
                        command.add("-v");
                        command.add(volume);
                    }
                }
            }
            
            command.add(image);
            
            System.out.println("Creating container: " + name + " from image: " + image);
            executeCommand(command.toArray(new String[0]));
            System.out.println("✓ Container created: " + name);
            return true;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to create container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Start a container
     */
    public boolean startContainer(String containerId) {
        if (!dockerAvailable) return false;
        
        try {
            System.out.println("Starting container: " + containerId);
            executeCommand(dockerCommand, "start", containerId);
            System.out.println("✓ Container started: " + containerId);
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to start container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop a container
     */
    public boolean stopContainer(String containerId) {
        if (!dockerAvailable) return false;
        
        try {
            System.out.println("Stopping container: " + containerId);
            executeCommand(dockerCommand, "stop", containerId);
            System.out.println("✓ Container stopped: " + containerId);
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to stop container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Restart a container
     */
    public boolean restartContainer(String containerId) {
        if (!dockerAvailable) return false;
        
        try {
            System.out.println("Restarting container: " + containerId);
            executeCommand(dockerCommand, "restart", containerId);
            System.out.println("✓ Container restarted: " + containerId);
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to restart container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove a container
     */
    public boolean removeContainer(String containerId, boolean force) {
        if (!dockerAvailable) return false;
        
        try {
            System.out.println("Removing container: " + containerId);
            if (force) {
                executeCommand(dockerCommand, "rm", "-f", containerId);
            } else {
                executeCommand(dockerCommand, "rm", containerId);
            }
            System.out.println("✓ Container removed: " + containerId);
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to remove container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get container logs
     */
    public String getContainerLogs(String containerId, int lines) {
        if (!dockerAvailable) return "Docker not available";
        
        try {
            return executeCommandWithOutput(dockerCommand, "logs", "--tail", 
                String.valueOf(lines), containerId);
        } catch (Exception e) {
            return "Error getting logs: " + e.getMessage();
        }
    }
    
    /**
     * Pull an image
     */
    public boolean pullImage(String image) {
        if (!dockerAvailable) return false;
        
        try {
            System.out.println("Pulling image: " + image);
            executeCommand(dockerCommand, "pull", image);
            System.out.println("✓ Image pulled: " + image);
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to pull image: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * List available images
     */
    public List<String> listImages() {
        List<String> images = new ArrayList<>();
        
        if (!dockerAvailable) {
            return images;
        }
        
        try {
            String output = executeCommandWithOutput(dockerCommand, "images", 
                "--format", "{{.Repository}}:{{.Tag}}");
            
            for (String line : output.split("\n")) {
                if (line.trim().isEmpty() || line.contains("<none>")) continue;
                images.add(line.trim());
            }
        } catch (Exception e) {
            System.err.println("Failed to list images: " + e.getMessage());
        }
        
        return images;
    }
    
    /**
     * Execute Docker command
     */
    private void executeCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("  " + line);
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Command failed with exit code: " + exitCode);
        }
    }
    
    /**
     * Execute command and return output
     */
    private String executeCommandWithOutput(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Command failed with exit code: " + exitCode);
        }
        
        return output.toString();
    }
    
    /**
     * Docker Container class
     */
    public static class DockerContainer {
        private String id;
        private String name;
        private String image;
        private String status;
        private String ports;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getPorts() { return ports; }
        public void setPorts(String ports) { this.ports = ports; }
        
        public String toJSON() {
            return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"image\":\"%s\",\"status\":\"%s\",\"ports\":\"%s\"}",
                id, name, image, status, ports != null ? ports : ""
            );
        }
    }
}
