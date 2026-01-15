package com.example.kvm.service;

import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Service
public class KvmService {

    private Connect connect;

    public KvmService() throws LibvirtException {
        // Connect to local KVM hypervisor
        this.connect = new Connect("qemu:///system", false);
    }

    public List<String> listVMs() throws LibvirtException {
        List<String> vms = new ArrayList<>();

        // Running domains
        for (int id : connect.listDomains()) {
            Domain domain = connect.domainLookupByID(id);
            vms.add(domain.getName());
        }

        // Defined but stopped domains
        for (String name : connect.listDefinedDomains()) {
            vms.add(name + " (stopped)");
        }

        return vms;
    }

    public void startVM(String name) throws LibvirtException {
        Domain domain = connect.domainLookupByName(name);
        domain.create();
    }

    public void stopVM(String name) throws LibvirtException {
        Domain domain = connect.domainLookupByName(name);
        domain.shutdown();
    }

    public void forceStopVM(String name) throws LibvirtException {
        Domain domain = connect.domainLookupByName(name);
        domain.destroy();
    }

    @PreDestroy
    public void close() throws LibvirtException {
        if (connect != null) {
            connect.close();
        }
    }
}
