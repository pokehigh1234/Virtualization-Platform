package com.example.kvm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/*
 * KvmService is the logic layer that manages virtual machine operations.
 */
@Service
public class KvmService {

    // Connection object to the KVM hypervisor - maintains the link to libvirtd
    private Connect connect = null;

    /*
     * Constructor - initializes the connection to the KVM hypervisor when the service is created.
     * Spring calls this automatically during application startup.
     */
    public KvmService() {
        initializeConnection();
    }

    /*
     * Establishes a connection to the local KVM hypervisor using libvirt.
     */
    private void initializeConnection() {
        try {
            // Connect to local KVM hypervisor
            // Second parameter (false) means if it is a read-only connection
            connect = new Connect("qemu:///system", false);
            System.out.println("Connection successful: " + connect.getURI());
        } catch (LibvirtException e) {
            // Log error if connection fails
            System.err.println("Failed to connect to qemu:///system: " + e.getMessage());
            e.printStackTrace();
            connect = null;
        }
    }

    /*
     * Checks if the connection to the hypervisor is available.
     */
    private void checkConnection() {
        if (connect == null) {
            throw new RuntimeException("No connection to KVM hypervisor. Ensure libvirtd is running and accessible.");
        }
    }

    /*
     * Retrieves all virtual machines available on the hypervisor.
     */
    public List<String> listVMs() throws LibvirtException {
        checkConnection();
        List<String> vms = new ArrayList<>();

        // Iterate through all currently running VMs and add them to the list
        for (int id : connect.listDomains()) {
            // Look up the Domain object using its numeric ID
            Domain domain = connect.domainLookupByID(id);
            // Add the VM name to the list (running VMs have no status suffix)
            vms.add(domain.getName());
        }

        // Iterate through all defined but stopped VMs and add them to the list
        for (String name : connect.listDefinedDomains()) {
            // Add the VM name with a "(stopped)" suffix to indicate it's not running
            vms.add(name + " (stopped)");
        }

        return vms;
    }

    /*
     * Starts a stopped virtual machine.
     */
    public void startVM(String name) throws LibvirtException {
        checkConnection();
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Trigger the VM to start
        domain.create();
    }

    /*
     * Retrieves VNC connection information for a virtual machine.
     */
    public String getVNCConnectionInfo(String name) throws LibvirtException {
        checkConnection();
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Get the VM's XML configuration
        String xmlDesc = domain.getXMLDesc(0);
        
        // Use regex to find the VNC port
        Pattern pattern = Pattern.compile("<graphics type='vnc'[^>]*port='(\\d+)'");
        Matcher matcher = pattern.matcher(xmlDesc);
        
        if (matcher.find()) {
            String portStr = matcher.group(1);
            int port = Integer.parseInt(portStr);
            
            // If port is -1, it means auto-assigned
            if (port == -1) {
                // For running VMs, try to get from domain ID
                try {
                    port = 5900 + domain.getID();
                } catch (Exception e) {
                    return "VNC port auto-assigned but VM not running";
                }
            }
            
            return "localhost:" + port;
        }
        
        return "No VNC graphics configured for this VM";
    }

    public void connectToVM(String name) throws LibvirtException {
        checkConnection();
        // This method is kept for backward compatibility
        // The actual connection info retrieval is in getVNCConnectionInfo()
        getVNCConnectionInfo(name);
    }

    /*
     * Gracefully shuts down a running virtual machine.
     */
    public void stopVM(String name) throws LibvirtException {
        checkConnection();
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Send graceful shutdown signal to the VM's operating system
        domain.shutdown();
    }

    /*
     * Forcefully terminates a virtual machine immediately.
     */
    public void forceStopVM(String name) throws LibvirtException {
        checkConnection();
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Immediately terminate the VM (equivalent to pulling the power cord)
        domain.destroy();
    }

    /*
     * Cleanup method called when the Spring bean is destroyed (application shutdown).
     */
    @PreDestroy
    public void close() throws LibvirtException {
        // Safely close the connection to the hypervisor if it was established
        if (connect != null) {
            connect.close();
        }
    }
    
    /*
     * Deletes a virtual machine from the hypervisor.
     */
    public void deleteVM(String name) throws LibvirtException {
        checkConnection();
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Undefine (delete) the VM from the hypervisor
        domain.undefine();
    }

    /*
     * Creates a new virtual machine from an ISO image with specified resources.
     */
    public void createVMFromISO(String name, int memoryMB, int vcpus, String isoPath, Integer diskSize, String localPath)
        throws LibvirtException {
        checkConnection();

        // Validate that the ISO file exists and is accessible
        java.io.File isoFile = new java.io.File(isoPath);
        if (!isoFile.exists()) {
            throw new RuntimeException("ISO file not found: " + isoPath);
        }
        if (!isoFile.canRead()) {
            throw new RuntimeException("Cannot read ISO file (permission denied): " + isoPath);
        }
        
        System.out.println("Using ISO: " + isoPath);

    // Create the disk image file at the specified location
    String diskPath = localPath + "/" + name + ".qcow2";
    createDiskImage(diskPath, diskSize);

    String xml =
        "<domain type='kvm'>" +
        "  <name>" + name + "</name>" +
        "  <memory unit='MiB'>" + memoryMB + "</memory>" +
        "  <vcpu>" + vcpus + "</vcpu>" +
        "  <os>" +
        "    <type arch='x86_64'>hvm</type>" +
        "    <boot dev='cdrom'/>" +
        "  </os>" +
        "  <devices>" +
        "    <disk type='file' device='cdrom'>" +
        "      <driver name='qemu' type='raw'/>" +
        "      <source file='" + isoPath + "'/>" +
        "      <target dev='hdc' bus='ide'/>" +
        "      <readonly/>" +
        "    </disk>" +
        "    <disk type='file' device='disk'>" +
        "      <driver name='qemu' type='qcow2'/>" +
        "      <source file='" + diskPath + "'/>" +
        "      <target dev='vda' bus='virtio'/>" +
        "    </disk>" +
        "    <interface type='network'>" +
        "      <source network='default'/>" +
        "      <model type='virtio'/>" +
        "    </interface>" +
        "    <graphics type='vnc' port='-1' autoport='yes'/>" +
        "  </devices>" +
        "</domain>";

    connect.domainDefineXML(xml);
    }

    /**
     * External helper for adjusting permissions on an existing qcow2 file and its
     * parent directories.  This is handy when a user points at an already‑created
     * image (for example on a USB/NTFS/exfat mount) and libvirt refuses to open it
     * because the qemu user (uid:107) can't traverse the path.
     */
    public void ensureDiskPermissions(String diskPath) {
        ensureFileAndParentsAccessible(diskPath);
    }

    /*
     * Creates a qcow2 disk image file using qemu-img command.
     */
    private void createDiskImage(String diskPath, Integer diskSize) {
        try {
            // Ensure the parent directory exists (may be several levels deep)
            java.io.File diskFile = new java.io.File(diskPath);
            java.io.File parentDir = diskFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // If the file doesn't already exist, create it now.  If it does exist
            // (for example when reusing an existing disk), we still need to adjust
            // permissions below so that the QEMU process can open it.
            if (!diskFile.exists()) {
                ProcessBuilder pb = new ProcessBuilder(
                    "qemu-img", "create", "-f", "qcow2", diskPath, diskSize + "G"
                );
                Process process = pb.start();
                process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                process.getErrorStream().transferTo(System.err);

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new Exception("Failed to create disk image: qemu-img exited with code " + exitCode);
                }
            }

            // Adjust permissions on the file and every directory leading to it.
            ensureFileAndParentsAccessible(diskPath);

        } catch (Exception e) {
            System.err.println("createDiskImage error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create disk image: " + e.getMessage(), e);
        }
    }

    /*
     * Runs a permission-related command (chmod / chown), trying with sudo first
     * and falling back to a direct call if sudo is unavailable.
     */
    private void runPermCommand(String... cmd) {
        try {
            // Try with sudo first (covers running as non-root)
            String[] sudoCmd = new String[cmd.length + 1];
            sudoCmd[0] = "sudo";
            System.arraycopy(cmd, 0, sudoCmd, 1, cmd.length);
            ProcessBuilder pb = new ProcessBuilder(sudoCmd);
            Process p = pb.start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            p.getErrorStream().transferTo(System.err);
            if (p.waitFor() == 0) return;
        } catch (Exception ignored) {}

        try {
            // Fallback: direct call (works if already running as root)
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            p.getErrorStream().transferTo(System.err);
            p.waitFor();
        } catch (Exception e) {
            System.err.println("Warning: Permission command failed: " + e.getMessage());
        }
    }

    /**
     * Guarantee that the disk file is readable/writable by the QEMU user and that
     * every directory in the path is traversable ("o+rx").
     *
     * This method is called unconditionally after the image is created so that it
     * also fixes permissions on pre‑existing files which may have been created by
     * the user or some other process.  The original implementation only touched
     * the immediate parent directory which was insufficient when the storage
     * directory itself was mounted or had more than one level of nesting.
     */
    private void ensureFileAndParentsAccessible(String diskPath) {
        java.io.File diskFile = new java.io.File(diskPath);

        // First make the file world‑read/write and try to chown it to the QEMU user.
        runPermCommand("chmod", "666", diskPath);
        System.out.println("Set 666 permissions on disk image: " + diskPath);

        String qemuUser = resolveQemuUser();
        if (tryChown(qemuUser + ":kvm", diskPath)) {
            System.out.println("chowned disk image to " + qemuUser + ":kvm");
        }

        // Walk up the directory hierarchy and ensure each directory is executable.
        java.io.File dir = diskFile.getParentFile();
        while (dir != null) {
            runPermCommand("chmod", "o+rx", dir.getAbsolutePath());
            System.out.println("Set o+rx on directory: " + dir.getAbsolutePath());
            dir = dir.getParentFile();
        }
    }

    /*
     * Returns the username QEMU runs as on this system.
     * Debian/Ubuntu use "libvirt-qemu"; Fedora/Arch/openSUSE use "qemu".
     */
    private String resolveQemuUser() {
        for (String candidate : new String[]{"libvirt-qemu", "qemu"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder("id", "-u", candidate);
                Process p = pb.start();
                p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                p.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());
                if (p.waitFor() == 0) {
                    System.out.println("Detected QEMU user: " + candidate);
                    return candidate;
                }
            } catch (Exception ignored) {}
        }
        // Fallback — chmod 666 already covers access
        System.out.println("Could not detect QEMU user, using 'qemu' as fallback");
        return "qemu";
    }

    /*
     * Attempts to chown a file to the given owner:group.
     * Returns true if successful.
     */
    private boolean tryChown(String ownerGroup, String path) {
        try {
            String[] cmd = {"chown", ownerGroup, path};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            p.getErrorStream().transferTo(System.err);
            boolean ok = p.waitFor() == 0;
            if (ok) System.out.println("chown " + ownerGroup + " succeeded for: " + path);
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Retrieves the VNC port number for a given VM by its name.
     * FIXED: Now properly parses the port number from XML using regex.
     */
    public int getVNCPortByName(String vmName) throws Exception {
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(vmName);
        
        // Get the VM's XML configuration
        String xmlDesc = domain.getXMLDesc(0);
        
        // Use regex to extract the VNC port from the graphics tag
        // Pattern matches: <graphics type='vnc' ... port='5901' ...>
        Pattern pattern = Pattern.compile("<graphics type='vnc'[^>]*port='(\\d+)'");
        Matcher matcher = pattern.matcher(xmlDesc);
        
        if (matcher.find()) {
            String portStr = matcher.group(1);
            int port = Integer.parseInt(portStr);
            
            System.out.println("Found VNC port for " + vmName + ": " + port);
            
            // If port is -1, it means auto-assigned
            if (port == -1) {
                // For running VMs, calculate from domain ID
                try {
                    port = 5900 + domain.getID();
                    System.out.println("Auto-assigned port calculated as: " + port);
                } catch (Exception e) {
                    throw new Exception("VNC port auto-assigned but VM not running");
                }
            }
            
            return port;
        }
        
        throw new Exception("Unable to determine VNC port for VM: " + vmName);
    }
}
