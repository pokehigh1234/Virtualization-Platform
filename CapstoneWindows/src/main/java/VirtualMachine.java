import java.util.UUID;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a Virtual Machine instance
 */
public class VirtualMachine {

    public enum Status {
        STOPPED, RUNNING, PAUSED, ERROR
    }

    private String id;
    private String name;
    private int memory; // MB
    private int cpuCores;
    private int diskSize; // GB
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;

    // Storage and ISO configuration
    private String storageLocation; // Path where VM files are stored
    private String isoPath; // Path to ISO file for installation

    // Runtime statistics
    private long cpuUsage; // percentage
    private long memoryUsage; // MB
    private int networkThroughput; // KB/s

    public VirtualMachine(String name, int memory, int cpuCores, int diskSize) {
        this(name, memory, cpuCores, diskSize, null, null);
    }

    public VirtualMachine(String name, int memory, int cpuCores, int diskSize,
                          String storageLocation, String isoPath) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.memory = memory;
        this.cpuCores = cpuCores;
        this.diskSize = diskSize;
        this.status = Status.STOPPED;
        this.createdAt = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.storageLocation = storageLocation != null ? storageLocation : getDefaultStorageLocation();
        this.isoPath = isoPath;
        this.cpuUsage = 0;
        this.memoryUsage = 0;
        this.networkThroughput = 0;
    }

    private String getDefaultStorageLocation() {
        // Default to user's home directory + VMs folder
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "VirtualMachines", id).toString();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public int getMemory() { return memory; }
    public int getCpuCores() { return cpuCores; }
    public int getDiskSize() { return diskSize; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModified() { return lastModified; }
    public long getCpuUsage() { return cpuUsage; }
    public long getMemoryUsage() { return memoryUsage; }
    public int getNetworkThroughput() { return networkThroughput; }
    public String getStorageLocation() { return storageLocation; }
    public String getIsoPath() { return isoPath; }

    // Setters
    public void setName(String name) {
        this.name = name;
        this.lastModified = LocalDateTime.now();
    }

    public void setStatus(Status status) {
        this.status = status;
        this.lastModified = LocalDateTime.now();
    }

    public void setCpuUsage(long cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public void setMemoryUsage(long memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public void setNetworkThroughput(int networkThroughput) {
        this.networkThroughput = networkThroughput;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
        this.lastModified = LocalDateTime.now();
    }

    public void setIsoPath(String isoPath) {
        this.isoPath = isoPath;
        this.lastModified = LocalDateTime.now();
    }

    // VM Control Operations
    public void start() {
        if (status == Status.STOPPED || status == Status.PAUSED) {
            status = Status.RUNNING;
            lastModified = LocalDateTime.now();
            System.out.println("VM " + name + " started");
            if (isoPath != null && !isoPath.isEmpty()) {
                System.out.println("  Using ISO: " + isoPath);
            }
            System.out.println("  Storage Location: " + storageLocation);

            // Simulate startup - generate some load
            cpuUsage = (long)(Math.random() * 30 + 10);
            memoryUsage = (long)(Math.random() * (memory * 0.5) + (memory * 0.2));
            networkThroughput = (int)(Math.random() * 1000 + 100);
        }
    }

    public void stop() {
        if (status == Status.RUNNING || status == Status.PAUSED) {
            status = Status.STOPPED;
            lastModified = LocalDateTime.now();
            cpuUsage = 0;
            memoryUsage = 0;
            networkThroughput = 0;
            System.out.println("VM " + name + " stopped");
        }
    }

    public void pause() {
        if (status == Status.RUNNING) {
            status = Status.PAUSED;
            lastModified = LocalDateTime.now();
            System.out.println("VM " + name + " paused");
        }
    }

    public void restart() {
        stop();
        try {
            Thread.sleep(1000); // Simulate restart delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        start();
    }

    // Convert to JSON
    public String toJSON() {
        String escapedStorage = storageLocation != null ?
                storageLocation.replace("\\", "\\\\") : "";
        String escapedIso = isoPath != null ?
                isoPath.replace("\\", "\\\\") : "";

        return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"memory\":%d,\"cpuCores\":%d,\"diskSize\":%d," +
                        "\"status\":\"%s\",\"cpuUsage\":%d,\"memoryUsage\":%d,\"networkThroughput\":%d," +
                        "\"storageLocation\":\"%s\",\"isoPath\":\"%s\"," +
                        "\"createdAt\":\"%s\",\"lastModified\":\"%s\"}",
                id, name, memory, cpuCores, diskSize,
                status.toString().toLowerCase(), cpuUsage, memoryUsage, networkThroughput,
                escapedStorage, escapedIso,
                createdAt.toString(), lastModified.toString()
        );
    }

    @Override
    public String toString() {
        return String.format("VM[%s: %s, %dMB, %d cores, %s, Storage: %s]",
                id, name, memory, cpuCores, status, storageLocation);
    }
}