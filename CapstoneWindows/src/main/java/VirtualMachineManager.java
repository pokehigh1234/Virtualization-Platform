import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages all Virtual Machine instances
 * Handles creation, deletion, and lifecycle management
 * NOW WITH REAL VIRTUALBOX INTEGRATION!
 */
public class VirtualMachineManager {

    private final Map<String, VirtualMachine> virtualMachines;
    private final ScheduledExecutorService scheduler;
    private final List<VMStatusListener> listeners;
    private final VirtualBoxManager vboxManager;  // ← Command-line integration (NO imports needed!)

    public interface VMStatusListener {
        void onStatusChange(VirtualMachine vm);
    }

    public VirtualMachineManager() {
        this.virtualMachines = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.vboxManager = new VirtualBoxManager();  // ← Uses VBoxManage CLI

        // Start monitoring task
        startMonitoring();

        // Print VirtualBox status
        if (vboxManager.isAvailable()) {
            System.out.println("✓ VirtualBox enabled - Creating REAL VMs!");
        } else {
            System.out.println("⚠ VirtualBox not found - Simulation mode");
            System.out.println("  Install from: https://www.virtualbox.org/");
        }
    }

    /**
     * Check if VirtualBox is available
     */
    public boolean isVirtualBoxAvailable() {
        return vboxManager.isAvailable();
    }

    /**
     * Create a new virtual machine
     */
    public VirtualMachine createVM(String name, int memory, int cpuCores, int diskSize) {
        return createVM(name, memory, cpuCores, diskSize, null, null);
    }

    /**
     * Create a new virtual machine with storage location and ISO
     */
    public VirtualMachine createVM(String name, int memory, int cpuCores, int diskSize,
                                   String storageLocation, String isoPath) {
        // Validate inputs
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("VM name cannot be empty");
        }
        if (memory < 512 || memory > 32768) {
            throw new IllegalArgumentException("Memory must be between 512MB and 32GB");
        }
        if (cpuCores < 1 || cpuCores > 16) {
            throw new IllegalArgumentException("CPU cores must be between 1 and 16");
        }
        if (diskSize < 10 || diskSize > 1000) {
            throw new IllegalArgumentException("Disk size must be between 10GB and 1000GB");
        }

        VirtualMachine vm = new VirtualMachine(name, memory, cpuCores, diskSize,
                storageLocation, isoPath);
        virtualMachines.put(vm.getId(), vm);

        System.out.println("Creating VM: " + vm);
        if (isoPath != null && !isoPath.isEmpty()) {
            System.out.println("  ISO: " + isoPath);
        }

        // ← ACTUALLY CREATE THE VM IN VIRTUALBOX!
        boolean created = vboxManager.createVM(vm);
        if (!created && vboxManager.isAvailable()) {
            System.err.println("Warning: Failed to create VM in VirtualBox");
        }

        notifyListeners(vm);

        return vm;
    }

    /**
     * Get a virtual machine by ID
     */
    public VirtualMachine getVM(String id) {
        return virtualMachines.get(id);
    }

    /**
     * Get all virtual machines
     */
    public List<VirtualMachine> getAllVMs() {
        return new ArrayList<>(virtualMachines.values());
    }

    /**
     * Delete a virtual machine
     */
    public boolean deleteVM(String id) {
        VirtualMachine vm = virtualMachines.get(id);
        if (vm == null) {
            return false;
        }

        // Stop VM if running
        if (vm.getStatus() == VirtualMachine.Status.RUNNING) {
            vm.stop();
        }

        // ← ACTUALLY DELETE FROM VIRTUALBOX!
        vboxManager.deleteVM(vm);

        virtualMachines.remove(id);
        System.out.println("Deleted VM: " + vm.getName());

        return true;
    }

    /**
     * Start a virtual machine
     */
    public boolean startVM(String id) {
        VirtualMachine vm = virtualMachines.get(id);
        if (vm == null) {
            return false;
        }

        // ← ACTUALLY START IN VIRTUALBOX!
        boolean started = vboxManager.startVM(vm);

        if (started) {
            vm.start();
            notifyListeners(vm);
        }

        return started;
    }

    /**
     * Stop a virtual machine
     */
    public boolean stopVM(String id) {
        VirtualMachine vm = virtualMachines.get(id);
        if (vm == null) {
            return false;
        }

        // ← ACTUALLY STOP IN VIRTUALBOX!
        boolean stopped = vboxManager.stopVM(vm);

        if (stopped) {
            vm.stop();
            notifyListeners(vm);
        }

        return stopped;
    }

    /**
     * Restart a virtual machine
     */
    public boolean restartVM(String id) {
        VirtualMachine vm = virtualMachines.get(id);
        if (vm == null) {
            return false;
        }

        // Stop then start
        stopVM(id);

        // Wait for clean shutdown
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return startVM(id);
    }

    /**
     * Pause a virtual machine
     */
    public boolean pauseVM(String id) {
        VirtualMachine vm = virtualMachines.get(id);
        if (vm == null) {
            return false;
        }

        // ← ACTUALLY PAUSE IN VIRTUALBOX!
        boolean paused = vboxManager.pauseVM(vm);

        if (paused) {
            vm.pause();
            notifyListeners(vm);
        }

        return paused;
    }

    /**
     * Execute a command in a VM context
     */
    public String executeCommand(String vmId, String command) {
        VirtualMachine vm = virtualMachines.get(vmId);

        if (vm == null) {
            return "Error: VM not found";
        }

        if (vm.getStatus() != VirtualMachine.Status.RUNNING) {
            return "Error: VM is not running";
        }

        // Simulate command execution
        return processCommand(vm, command);
    }

    /**
     * Process commands (simplified simulation)
     */
    private String processCommand(VirtualMachine vm, String command) {
        command = command.trim().toLowerCase();

        // Handle common commands
        if (command.equals("help")) {
            return "Available commands: help, status, ps, top, uptime, df, free, hostname, date, clear";
        } else if (command.equals("status")) {
            return String.format("VM Status: %s\nCPU: %d%%\nMemory: %dMB/%dMB\nNetwork: %d KB/s",
                    vm.getStatus(), vm.getCpuUsage(), vm.getMemoryUsage(), vm.getMemory(),
                    vm.getNetworkThroughput());
        } else if (command.equals("ps")) {
            return "PID\tCOMMAND\n1\tinit\n42\tsystemd\n100\tbash\n156\tjava";
        } else if (command.equals("top")) {
            return String.format("CPU: %d%%\nMemory: %d/%d MB\nProcesses: 156 running",
                    vm.getCpuUsage(), vm.getMemoryUsage(), vm.getMemory());
        } else if (command.equals("uptime")) {
            return "System uptime: 2 hours, 34 minutes";
        } else if (command.equals("df")) {
            return String.format("Filesystem\tSize\tUsed\tAvail\n/dev/sda1\t%dG\t5G\t%dG",
                    vm.getDiskSize(), vm.getDiskSize() - 5);
        } else if (command.equals("free")) {
            return String.format("Total: %d MB\nUsed: %d MB\nFree: %d MB",
                    vm.getMemory(), vm.getMemoryUsage(), vm.getMemory() - vm.getMemoryUsage());
        } else if (command.equals("hostname")) {
            return vm.getName();
        } else if (command.equals("date")) {
            return new Date().toString();
        } else if (command.startsWith("echo ")) {
            return command.substring(5);
        } else if (command.equals("clear")) {
            return "[Terminal cleared]";
        } else {
            return "bash: " + command + ": command not found\nType 'help' for available commands";
        }
    }

    /**
     * Start monitoring running VMs
     */
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            for (VirtualMachine vm : virtualMachines.values()) {
                if (vm.getStatus() == VirtualMachine.Status.RUNNING) {
                    // Simulate resource usage fluctuation
                    long cpuChange = (long)(Math.random() * 20 - 10);
                    long memChange = (long)(Math.random() * 100 - 50);
                    int netChange = (int)(Math.random() * 200 - 100);

                    vm.setCpuUsage(Math.max(5, Math.min(95, vm.getCpuUsage() + cpuChange)));
                    vm.setMemoryUsage(Math.max(100, Math.min(vm.getMemory() - 100,
                            vm.getMemoryUsage() + memChange)));
                    vm.setNetworkThroughput(Math.max(0, vm.getNetworkThroughput() + netChange));
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Add a status change listener
     */
    public void addListener(VMStatusListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a status change listener
     */
    public void removeListener(VMStatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify all listeners of a status change
     */
    private void notifyListeners(VirtualMachine vm) {
        for (VMStatusListener listener : listeners) {
            try {
                listener.onStatusChange(vm);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        }
    }

    /**
     * Get VM statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVMs", virtualMachines.size());
        stats.put("runningVMs", virtualMachines.values().stream()
                .filter(vm -> vm.getStatus() == VirtualMachine.Status.RUNNING)
                .count());
        stats.put("stoppedVMs", virtualMachines.values().stream()
                .filter(vm -> vm.getStatus() == VirtualMachine.Status.STOPPED)
                .count());
        stats.put("totalMemoryAllocated", virtualMachines.values().stream()
                .mapToInt(VirtualMachine::getMemory)
                .sum());
        stats.put("totalCPUCores", virtualMachines.values().stream()
                .mapToInt(VirtualMachine::getCpuCores)
                .sum());
        return stats;
    }

    /**
     * Shutdown the manager and all VMs
     */
    public void shutdown() {
        System.out.println("Shutting down VM Manager...");

        // Stop all running VMs
        for (VirtualMachine vm : virtualMachines.values()) {
            if (vm.getStatus() == VirtualMachine.Status.RUNNING) {
                vm.stop();
            }
        }

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("VM Manager shutdown complete");
    }
}