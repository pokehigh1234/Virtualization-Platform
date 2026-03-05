package com.example.kvm.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * LxcService manages LXC (Linux Containers) via the lxc command-line tools.
 * It runs system commands using ProcessBuilder to list, start, stop,
 * create, and delete containers.
 */
@Service
public class LxcService {

    /*
     * Runs a system command and returns its stdout output as a string.
     */
    private String runCommand(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Command failed (exit " + exitCode + "): " + output);
        }
        return output.toString().trim();
    }

    /*
     * Lists all LXC containers with their name, state, and IP address.
     * Returns a list of maps, each containing "name", "state", and "ip".
     */
    public List<Map<String, String>> listContainers() throws Exception {
        List<Map<String, String>> containers = new ArrayList<>();

        String output;
        try {
            output = runCommand("lxc-ls", "--fancy", "--fancy-format", "name,state,ipv4");
        } catch (Exception e) {
            // lxc-ls not available or no containers — return empty list
            return containers;
        }

        String[] lines = output.split("\n");
        // Skip header lines (first two lines: column headers and separator)
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 3);
            Map<String, String> container = new LinkedHashMap<>();
            container.put("name",  parts.length > 0 ? parts[0] : "");
            container.put("state", parts.length > 1 ? parts[1] : "UNKNOWN");
            container.put("ip",    parts.length > 2 ? parts[2] : "-");
            containers.add(container);
        }

        return containers;
    }

    /*
     * Starts a stopped LXC container.
     */
    public void startContainer(String name) throws Exception {
        runCommand("lxc-start", "-n", name);
    }

    /*
     * Gracefully stops a running LXC container.
     */
    public void stopContainer(String name) throws Exception {
        runCommand("lxc-stop", "-n", name);
    }

    /*
     * Force-stops a running LXC container immediately.
     */
    public void forceStopContainer(String name) throws Exception {
        runCommand("lxc-stop", "-n", name, "-k");
    }

    /*
     * Deletes an LXC container and all of its storage.
     */
    public void deleteContainer(String name) throws Exception {
        runCommand("lxc-destroy", "-n", name);
    }

    /*
     * Creates a new LXC container using the specified template (e.g. "ubuntu", "debian").
     * An optional release/version can be passed via extraArgs (e.g. "focal").
     */
    public void createContainer(String name, String template, String release) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("lxc-create");
        cmd.add("-n");
        cmd.add(name);
        cmd.add("-t");
        cmd.add(template);

        if (release != null && !release.isBlank()) {
            cmd.add("--");
            cmd.add("--release");
            cmd.add(release);
        }

        runCommand(cmd.toArray(new String[0]));
    }

    /*
     * Retrieves detailed info about a single container.
     */
    public String getContainerInfo(String name) throws Exception {
        return runCommand("lxc-info", "-n", name);
    }
}
