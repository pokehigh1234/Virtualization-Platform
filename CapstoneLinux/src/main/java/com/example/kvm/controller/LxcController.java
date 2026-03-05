package com.example.kvm.controller;

import com.example.kvm.service.LxcService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/*
 * LxcController handles all web routes related to LXC container management.
 */
@Controller
@RequestMapping("/lxc")
public class LxcController {

    private final LxcService lxcService;

    public LxcController(LxcService lxcService) {
        this.lxcService = lxcService;
    }

    /*
     * Lists all LXC containers on the host.
     */
    @GetMapping
    public String listContainers(Model model) {
        try {
            model.addAttribute("containers", lxcService.listContainers());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to list containers: " + e.getMessage());
            model.addAttribute("containers", java.util.Collections.emptyList());
        }
        return "lxc";
    }

    /*
     * Shows the form to create a new LXC container.
     */
    @GetMapping("/create")
    public String createContainerForm() {
        return "createlxc";
    }

    /*
     * Handles creation of a new LXC container.
     */
    @PostMapping("/create")
    public String createContainer(
            @RequestParam String name,
            @RequestParam String template,
            @RequestParam(required = false) String release,
            Model model
    ) {
        try {
            lxcService.createContainer(name, template, release);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create container: " + e.getMessage());
            return "createlxc";
        }
        return "redirect:/lxc";
    }

    /*
     * Starts a stopped container.
     */
    @PostMapping("/{name}/start")
    public String startContainer(@PathVariable String name) throws Exception {
        lxcService.startContainer(name);
        return "redirect:/lxc";
    }

    /*
     * Gracefully stops a running container.
     */
    @PostMapping("/{name}/stop")
    public String stopContainer(@PathVariable String name) throws Exception {
        lxcService.stopContainer(name);
        return "redirect:/lxc";
    }

    /*
     * Force-stops a running container.
     */
    @PostMapping("/{name}/forcestop")
    public String forceStopContainer(@PathVariable String name) throws Exception {
        lxcService.forceStopContainer(name);
        return "redirect:/lxc";
    }

    /*
     * Deletes a container permanently.
     */
    @PostMapping("/{name}/delete")
    public String deleteContainer(@PathVariable String name) throws Exception {
        lxcService.deleteContainer(name);
        return "redirect:/lxc";
    }
}
