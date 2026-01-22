package com.example.kvm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * KvmManagerApplication is the main entry point for the KVM (Kernel Virtual Machine) Manager application.
 * 
 * This Spring Boot application provides a REST API to manage and control virtual machines running on
 * a KVM hypervisor. It uses the libvirt library to interact with the underlying QEMU/KVM virtualization
 * platform.
 * 
 * The @SpringBootApplication annotation enables:
 * - Component scanning to discover Spring beans
 * - Auto-configuration of Spring components
 * - Property file configuration support
 */
@SpringBootApplication
public class KvmManagerApplication {

    /*
     * Main method - entry point for the Spring Boot application
     */
    public static void main(String[] args) {
        // Initialize and start the Spring Boot application
        SpringApplication.run(KvmManagerApplication.class, args);
    }
}
