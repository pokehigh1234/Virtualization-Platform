package com.example.kvm.controller;

import com.example.kvm.service.KvmService;
import com.example.kvm.service.LxcService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebController {

    private final KvmService kvmService;
    private final LxcService lxcService;

    public WebController(KvmService kvmService, LxcService lxcService) {
        this.kvmService = kvmService;
        this.lxcService  = lxcService;
    }

    /* ── Index: load both VMs and LXC containers ── */
    @GetMapping("/")
    public String index(Model model) throws Exception {
        model.addAttribute("vms", kvmService.listVMs());
        try {
            model.addAttribute("containers", lxcService.listContainers());
        } catch (Exception e) {
            model.addAttribute("containers", java.util.Collections.emptyList());
        }
        return "index";
    }

    /* ── KVM VM routes ── */
    @GetMapping("/createvm")
    public String createVMForm() { return "redirect:/"; }

    @GetMapping("/vm/{name}")
    public String vmDetails(@PathVariable String name, Model model) {
        model.addAttribute("vmName", name);
        return "vm";
    }

    @PostMapping("/vm/create")
    public String createVM(
            @RequestParam String name,
            @RequestParam int memory,
            @RequestParam int vcpus,
            @RequestParam String iso,
            @RequestParam Integer diskSize,
            @RequestParam String localPath
    ) throws Exception {
        kvmService.createVMFromISO(name, memory, vcpus, iso, diskSize, localPath);
        return "redirect:/";
    }

    @PostMapping("/vm/{name}/start")
    public String startVM(@PathVariable String name) throws Exception {
        kvmService.startVM(name);
        return "redirect:/";
    }

    @PostMapping("/vm/{name}/shutdown")
    public String shutdownVM(@PathVariable String name) throws Exception {
        kvmService.stopVM(name);
        return "redirect:/";
    }

    @PostMapping("/vm/{name}/forceshutdown")
    public String forceShutdownVM(@PathVariable String name) throws Exception {
        kvmService.forceStopVM(name);
        return "redirect:/";
    }

    @PostMapping("/vm/{name}/delete")
    public String deleteVM(@PathVariable String name) throws Exception {
        kvmService.deleteVM(name);
        return "redirect:/";
    }

    @PostMapping("/vm/{name}/connect")
    public String connectToVM(@PathVariable String name) throws Exception {
        kvmService.connectToVM(name);
        return "redirect:/";
    }
}
