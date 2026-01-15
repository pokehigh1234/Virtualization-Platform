package com.example.kvm;

import org.libvirt.Connect;
import org.libvirt.LibvirtException;

public class LibvirtTest {
    public static void main(String[] args) {
        try {
            // Connect to the local system libvirtd instance
            Connect conn = new Connect("qemu:///system", false);
            System.out.println("Connection successful: " + conn.getURI());
        } catch (LibvirtException e) {
            System.err.println("Failed to connect to qemu:///system: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
