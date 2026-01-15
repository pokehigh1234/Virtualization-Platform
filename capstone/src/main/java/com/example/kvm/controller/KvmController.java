package com.example.kvm.controller;

import com.example.kvm.service.KvmService;
import org.libvirt.LibvirtException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vms")
public class KvmController {

    private final KvmService kvmService;

    public KvmController(KvmService kvmService) {
        this.kvmService = kvmService;
    }

    @GetMapping
    public List<String> listVMs() throws LibvirtException {
        return kvmService.listVMs();
    }

    @PostMapping("/{name}/start")
    public String startVM(@PathVariable String name) throws LibvirtException {
        kvmService.startVM(name);
        return "VM started: " + name;
    }

    @PostMapping("/{name}/stop")
    public String stopVM(@PathVariable String name) throws LibvirtException {
        kvmService.stopVM(name);
        return "VM stopped: " + name;
    }

    @PostMapping("/{name}/force-stop")
    public String forceStopVM(@PathVariable String name) throws LibvirtException {
        kvmService.forceStopVM(name);
        return "VM force stopped: " + name;
    }
}
