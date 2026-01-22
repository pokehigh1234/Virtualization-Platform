package com.example.kvm.service;

import java.util.ArrayList;
import java.util.List;

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
        }
    }

    /*
     * Retrieves all virtual machines available on the hypervisor.
     */
    public List<String> listVMs() throws LibvirtException {
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
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Trigger the VM to start
        domain.create();
    }

    /*
     * Retrieves VNC connection information for a virtual machine.
     */
    public String getVNCConnectionInfo(String name) throws LibvirtException {
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Get the VM's XML configuration
        String xmlDesc = domain.getXMLDesc(0);
        
        // Parse the XML to find the graphics section and extract the port
        int portStart = xmlDesc.indexOf("<graphics type='vnc'");
        if (portStart == -1) {
            return "No VNC graphics configured for this VM";
        }
        
        int portAttrStart = xmlDesc.indexOf("port='", portStart);
        if (portAttrStart == -1) {
            return "VNC port not found in configuration";
        }
        
        int portValueStart = portAttrStart + 6; // Length of "port='"
        int portValueEnd = xmlDesc.indexOf("'", portValueStart);
        String portStr = xmlDesc.substring(portValueStart, portValueEnd);
        
        int port = Integer.parseInt(portStr);
        
        // VNC ports in libvirt: 5900 + display number
        // If port is -1, it means auto-assigned; need to check actual port
        if (port == -1) {
            // Get the actual port from the domain state
            port = 5900 + domain.getID();
        }
        
        return "localhost:" + port;
    }

    public void connectToVM(String name) throws LibvirtException {
        // This method is kept for backward compatibility
        // The actual connection info retrieval is in getVNCConnectionInfo()
        getVNCConnectionInfo(name);
    }

    /*
     * Gracefully shuts down a running virtual machine.
     */
    public void stopVM(String name) throws LibvirtException {
        // Look up the Domain object by its configured name
        Domain domain = connect.domainLookupByName(name);
        // Send graceful shutdown signal to the VM's operating system
        domain.shutdown();
    }

    /*
     * Forcefully terminates a virtual machine immediately.
     */
    public void forceStopVM(String name) throws LibvirtException {
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

    /*
     * Creates a qcow2 disk image file using virsh or system command.
     */
    private void createDiskImage(String diskPath, Integer diskSize) throws LibvirtException {
        try {
            // Use ProcessBuilder for safer command execution
            ProcessBuilder pb = new ProcessBuilder(
                "qemu-img", "create", "-f", "qcow2", diskPath, diskSize + "G"
            );
            Process process = pb.start();
            
            // Drain the output streams to prevent deadlock
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            process.getErrorStream().transferTo(System.err);
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new Exception("Failed to create disk image: qemu-img exited with code " + exitCode);
            }
        } catch (Exception e) {
            
        }
    }

    /*
     * Retrieves the VNC port number for a given VM by its name.
     */
    public int getVNCPortByName(String vmName) throws Exception {
        // Query the VM configuration to extract the VNC port
        // This typically involves parsing libvirt domain XML or querying the VM's display settings
        String vncInfo = getVNCConnectionInfo(vmName);
        
        // Extract port number from VNC connection info (e.g., "localhost:5900")
        if (vncInfo != null && vncInfo.contains(":")) {
            String portStr = vncInfo.split(":")[1];
            return Integer.parseInt(portStr);
        }
        
        throw new Exception("Unable to determine VNC port for VM: " + vmName);
    }
}