package com.example.kvm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.kvm.service.KvmService;
import java.util.HashMap;
import java.util.Map;

@Controller
public class WebController {

    private KvmService kvmService = null;

    public WebController(KvmService kvmService) {
        this.kvmService = kvmService;
    }

    @GetMapping("/")
    public String index(Model model) throws Exception {
        model.addAttribute("vms", kvmService.listVMs());
        return "index";
    }

    @GetMapping("/vm/{name}")
    public String vmDetails(@PathVariable String name, Model model) throws Exception {
        model.addAttribute("vmName", name);
        try {
            String vncInfo = kvmService.getVNCConnectionInfo(name);
            int vncPort = kvmService.getVNCPortByName(name);
            model.addAttribute("vncInfo", vncInfo);
            model.addAttribute("vncPort", vncPort);
        } catch (Exception e) {
            model.addAttribute("vncInfo", "Unable to retrieve VNC info");
            model.addAttribute("vncPort", -1);
        }
        return "vm";
    }

    /*
     * REST API endpoint to get VNC port for a VM (used by JavaScript)
     */
    @GetMapping("/api/vm/{name}/vnc-port")
    @ResponseBody
    public Map<String, Object> getVNCPort(@PathVariable String name) {
        Map<String, Object> response = new HashMap<>();
        try {
            int port = kvmService.getVNCPortByName(name);
            response.put("success", true);
            response.put("port", port);
            response.put("host", "localhost");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
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
