import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * VirtualBox Integration Manager
 * Handles actual VM creation and management using VirtualBox
 */
public class VirtualBoxManager {

    private String vboxManagePath;
    private boolean vboxAvailable = false;

    public VirtualBoxManager() {
        detectVirtualBox();
    }

    /**
     * Detect VirtualBox installation
     */
    private void detectVirtualBox() {
        // Try common VirtualBox installation paths
        String[] possiblePaths = {
                "VBoxManage",  // If in PATH
                "C:\\Program Files\\Oracle\\VirtualBox\\VBoxManage.exe",
                "C:\\Program Files (x86)\\Oracle\\VirtualBox\\VBoxManage.exe",
                "/usr/bin/VBoxManage",
                "/usr/local/bin/VBoxManage"
        };

        for (String path : possiblePaths) {
            if (testVBoxPath(path)) {
                vboxManagePath = path;
                vboxAvailable = true;
                System.out.println("✓ VirtualBox found at: " + path);
                return;
            }
        }

        System.err.println("⚠ VirtualBox not found. VMs will run in simulation mode.");
        System.err.println("  Install VirtualBox from: https://www.virtualbox.org/");
        vboxAvailable = false;
    }

    /**
     * Test if VBoxManage path is valid
     */
    private boolean testVBoxPath(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if VirtualBox is available
     */
    public boolean isAvailable() {
        return vboxAvailable;
    }

    /**
     * Create a new virtual machine
     */
    public boolean createVM(VirtualMachine vm) {
        if (!vboxAvailable) {
            System.out.println("VirtualBox not available - VM created in simulation mode");
            createStorageDirectory(vm);
            return true; // Simulation mode succeeds
        }

        try {
            // Determine the base folder for VirtualBox
            // VirtualBox will automatically create a subfolder with the VM name
            // So if user wants VM at C:\VMs\MyVM, we need to use C:\VMs as basefolder
            String baseFolder = vm.getStorageLocation();
            String vmFolder = vm.getStorageLocation();

            // If storage location ends with VM name, use parent as basefolder
            if (vm.getStorageLocation().endsWith(vm.getName())) {
                // User specified: C:\VMs\MyVM
                // We use C:\VMs as basefolder, VirtualBox creates C:\VMs\MyVM
                baseFolder = Paths.get(vm.getStorageLocation()).getParent().toString();
            } else {
                // User specified: C:\VMs
                // We use C:\VMs as basefolder, VirtualBox creates C:\VMs\VMName
                vmFolder = Paths.get(vm.getStorageLocation(), vm.getName()).toString();
            }

            // Create storage directory
            createStorageDirectory(vm);

            // 1. Create the VM with proper basefolder
            System.out.println("Creating VM: " + vm.getName());
            System.out.println("  Base folder: " + baseFolder);
            System.out.println("  VM folder will be: " + vmFolder);

            executeCommand(vboxManagePath, "createvm",
                    "--name", vm.getName(),
                    "--ostype", "Other",
                    "--register",
                    "--basefolder", baseFolder);

            // 2. Configure memory
            executeCommand(vboxManagePath, "modifyvm", vm.getName(),
                    "--memory", String.valueOf(vm.getMemory()),
                    "--cpus", String.valueOf(vm.getCpuCores()));

            // 3. Create virtual hard disk in the correct location
            String vdiPath = Paths.get(vmFolder, vm.getName() + ".vdi").toString();
            System.out.println("  Creating VDI at: " + vdiPath);

            executeCommand(vboxManagePath, "createhd",
                    "--filename", vdiPath,
                    "--size", String.valueOf(vm.getDiskSize() * 1024)); // Convert GB to MB

            // 4. Add storage controller
            executeCommand(vboxManagePath, "storagectl", vm.getName(),
                    "--name", "SATA Controller",
                    "--add", "sata",
                    "--controller", "IntelAhci");

            // 5. Attach hard disk
            executeCommand(vboxManagePath, "storageattach", vm.getName(),
                    "--storagectl", "SATA Controller",
                    "--port", "0",
                    "--device", "0",
                    "--type", "hdd",
                    "--medium", vdiPath);

            // 6. Add IDE controller for CD/DVD
            executeCommand(vboxManagePath, "storagectl", vm.getName(),
                    "--name", "IDE Controller",
                    "--add", "ide");

            // 7. Attach ISO if provided
            if (vm.getIsoPath() != null && !vm.getIsoPath().isEmpty()) {
                File isoFile = new File(vm.getIsoPath());
                if (isoFile.exists()) {
                    executeCommand(vboxManagePath, "storageattach", vm.getName(),
                            "--storagectl", "IDE Controller",
                            "--port", "0",
                            "--device", "0",
                            "--type", "dvddrive",
                            "--medium", vm.getIsoPath());
                    System.out.println("  Attached ISO: " + vm.getIsoPath());
                } else {
                    System.err.println("  Warning: ISO file not found: " + vm.getIsoPath());
                }
            }

            // 8. Configure network (NAT)
            executeCommand(vboxManagePath, "modifyvm", vm.getName(),
                    "--nic1", "nat");

            // 9. Configure graphics
            executeCommand(vboxManagePath, "modifyvm", vm.getName(),
                    "--vram", "128",
                    "--graphicscontroller", "vmsvga");

            // 10. Enable ACPI
            executeCommand(vboxManagePath, "modifyvm", vm.getName(),
                    "--acpi", "on",
                    "--ioapic", "on");

            System.out.println("✓ VM created successfully: " + vm.getName());
            return true;

        } catch (Exception e) {
            System.err.println("✗ Failed to create VM: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Start a virtual machine
     */
    public boolean startVM(VirtualMachine vm) {
        if (!vboxAvailable) {
            System.out.println("Simulation mode: VM " + vm.getName() + " started");
            return true;
        }

        try {
            System.out.println("Starting VM: " + vm.getName());
            executeCommand(vboxManagePath, "startvm", vm.getName(), "--type", "gui");
            System.out.println("✓ VM started: " + vm.getName());
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to start VM: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stop a virtual machine
     */
    public boolean stopVM(VirtualMachine vm) {
        if (!vboxAvailable) {
            System.out.println("Simulation mode: VM " + vm.getName() + " stopped");
            return true;
        }

        try {
            System.out.println("Stopping VM: " + vm.getName());
            executeCommand(vboxManagePath, "controlvm", vm.getName(), "acpipowerbutton");
            System.out.println("✓ VM shutdown signal sent: " + vm.getName());
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to stop VM: " + e.getMessage());
            return false;
        }
    }

    /**
     * Force stop a virtual machine
     */
    public boolean powerOffVM(VirtualMachine vm) {
        if (!vboxAvailable) {
            return true;
        }

        try {
            executeCommand(vboxManagePath, "controlvm", vm.getName(), "poweroff");
            System.out.println("✓ VM powered off: " + vm.getName());
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to power off VM: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pause a virtual machine
     */
    public boolean pauseVM(VirtualMachine vm) {
        if (!vboxAvailable) {
            return true;
        }

        try {
            executeCommand(vboxManagePath, "controlvm", vm.getName(), "pause");
            System.out.println("✓ VM paused: " + vm.getName());
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to pause VM: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resume a paused virtual machine
     */
    public boolean resumeVM(VirtualMachine vm) {
        if (!vboxAvailable) {
            return true;
        }

        try {
            executeCommand(vboxManagePath, "controlvm", vm.getName(), "resume");
            System.out.println("✓ VM resumed: " + vm.getName());
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to resume VM: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a virtual machine
     */
    public boolean deleteVM(VirtualMachine vm) {
        if (!vboxAvailable) {
            System.out.println("Simulation mode: VM " + vm.getName() + " deleted");
            return true;
        }

        try {
            // Try to power off if running
            try {
                powerOffVM(vm);
                Thread.sleep(2000); // Wait for shutdown
            } catch (Exception e) {
                // VM might not be running
            }

            System.out.println("Deleting VM: " + vm.getName());
            executeCommand(vboxManagePath, "unregistervm", vm.getName(), "--delete");
            System.out.println("✓ VM deleted: " + vm.getName());
            return true;
        } catch (Exception e) {
            System.err.println("✗ Failed to delete VM: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get VM status from VirtualBox
     */
    public String getVMStatus(String vmName) {
        if (!vboxAvailable) {
            return "unknown";
        }

        try {
            String output = executeCommandWithOutput(vboxManagePath, "showvminfo",
                    vmName, "--machinereadable");

            // Parse the output to find VMState
            for (String line : output.split("\n")) {
                if (line.startsWith("VMState=\"")) {
                    String state = line.substring(9, line.length() - 1);
                    return mapVBoxState(state);
                }
            }
        } catch (Exception e) {
            // VM might not exist
        }

        return "stopped";
    }

    /**
     * Map VirtualBox state to our state
     */
    private String mapVBoxState(String vboxState) {
        switch (vboxState.toLowerCase()) {
            case "running":
                return "running";
            case "paused":
                return "paused";
            case "poweroff":
            case "saved":
            case "aborted":
                return "stopped";
            default:
                return "stopped";
        }
    }

    /**
     * Create storage directory
     */
    private void createStorageDirectory(VirtualMachine vm) {
        try {
            Path storagePath = Paths.get(vm.getStorageLocation());
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
                System.out.println("✓ Created storage directory: " + storagePath);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not create storage directory: " + e.getMessage());
        }
    }

    /**
     * Execute VBoxManage command
     */
    private void executeCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Read output
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
     * List all VirtualBox VMs
     */
    public List<String> listVMs() {
        List<String> vms = new ArrayList<>();

        if (!vboxAvailable) {
            return vms;
        }

        try {
            String output = executeCommandWithOutput(vboxManagePath, "list", "vms");
            for (String line : output.split("\n")) {
                if (line.trim().isEmpty()) continue;
                // Format: "VMName" {uuid}
                int firstQuote = line.indexOf('"');
                int secondQuote = line.indexOf('"', firstQuote + 1);
                if (firstQuote >= 0 && secondQuote > firstQuote) {
                    String vmName = line.substring(firstQuote + 1, secondQuote);
                    vms.add(vmName);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to list VMs: " + e.getMessage());
        }

        return vms;
    }
}