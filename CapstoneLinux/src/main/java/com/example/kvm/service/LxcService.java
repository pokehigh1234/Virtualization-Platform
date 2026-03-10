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
    /**
     * Execute a system command and return its stdout output.
     *
     * If the binary cannot be found or the process exits non‑zero, the
     * resulting exception includes both stdout and stderr.  We also try the
     * same command prefixed with "sudo" when the original invocation fails
     * with a permissions error; this mimics the behaviour of the disk‑image
     * helper in KvmService.
     */
    private String runCommand(String... cmd) throws Exception {
        StringBuilder output = new StringBuilder();
        int exitCode = -1;

        try {
            exitCode = runCommandInternal(output, cmd);
        } catch (java.io.IOException ioe) {
            // binary doesn't exist or not in PATH
            throw new Exception("Executable not found: " + cmd[0], ioe);
        }

        // if the command failed because of permission and sudo is available,
        // retry once with sudo.  We look for a couple of common phrases rather
        // than rely on a specific exit code since different distros behave
        // differently.
        if (exitCode != 0 && output.toString().toLowerCase().matches("(?s).*permission denied.*") ) {
            String[] sudoCmd = new String[cmd.length + 1];
            sudoCmd[0] = "sudo";
            System.arraycopy(cmd, 0, sudoCmd, 1, cmd.length);
            output = new StringBuilder();
            exitCode = runCommandInternal(output, sudoCmd);
        }

        if (exitCode != 0) {
            throw new Exception("Command `" + String.join(" ", cmd) + "` failed (exit "
                    + exitCode + "):\n" + output);
        }
        return output.toString().trim();
    }

    private int runCommandInternal(StringBuilder output, String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        return process.waitFor();
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
     *
     * Note: Creating unprivileged containers (as non-root) is much slower because
     * the download template fetches images from the internet and configures user
     * namespaces. For production use, the application should be run as root.
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

        // the native tools want a default configuration even if it's empty;
        // older versions simply failed with "failed to open file /home/user/.config/lxc/default.conf"
        ensureDefaultConfig();

        // ensure LXC binary is available
        try {
            runCommand("which", "lxc-create");
        } catch (Exception e) {
            throw new Exception("lxc-create not found; please install LXC or run the app on a system with the LXC client tools.");
        }

        // execute the command, with a fallback when the requested template doesn't exist
        System.out.println("Creating container " + name + " with template " + template + "...");
        try {
            runCommand(cmd.toArray(new String[0]));
            System.out.println("Container " + name + " created successfully (local template)");
            return;
        } catch (Exception primaryEx) {
            String detail = primaryEx.getMessage();
            if (detail != null && detail.contains("Template \"" + template + "\" not found")) {
                // attempt to use the download template if we're not already doing so
                if (!"download".equals(template)) {
                    System.out.println("Local template not found, attempting download template (this may take a while)...");
                    List<String> alt = new ArrayList<>();
                    alt.add("lxc-create");
                    alt.add("-n");
                    alt.add(name);
                    alt.add("-t");
                    alt.add("download");
                    alt.add("--");
                    alt.add("--dist");
                    alt.add(template);
                    if (release != null && !release.isBlank()) {
                        alt.add("--release");
                        alt.add(release);
                    }
                    try {
                        runCommand(alt.toArray(new String[0]));
                        System.out.println("Container " + name + " created successfully (download template)");
                        return;
                    } catch (Exception downloadEx) {
                        throw new Exception("Template '" + template + "' not found and download fallback also failed:\n"
                                + detail + "\n--\n" + downloadEx.getMessage());
                    }
                }
            }
            throw primaryEx;
        }
    }

    /**
     * Create ~/.config/lxc/default.conf if it doesn't already exist.  The
     * file can be empty; absence causes lxc-create to error out even though the
     * runtime configuration isn't strictly required for simple containers.
     *
     * For unprivileged containers, we also add uid/gid mappings if they appear
     * to be missing.  This allows the app to create containers without running
     * as root (though KVM operations still require root).
     */
    private void ensureDefaultConfig() throws Exception {
        String home = System.getProperty("user.home");
        java.io.File cfgDir = new java.io.File(home, ".config/lxc");
        if (!cfgDir.exists() && !cfgDir.mkdirs()) {
            throw new Exception("Could not create LXC config directory: " + cfgDir);
        }
        java.io.File cfg = new java.io.File(cfgDir, "default.conf");
        if (!cfg.exists()) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(cfg)) {
                pw.println("# default LXC config created by application");
                pw.println("lxc.include = /etc/lxc/default.conf");
                pw.println("lxc.idmap = u 0 524288 65536");
                pw.println("lxc.idmap = g 0 524288 65536");
            }
        } else {
            // if the file exists, check if it has the mappings; if not, append them
            String content = new String(java.nio.file.Files.readAllBytes(cfg.toPath()));
            if (!content.contains("lxc.idmap")) {
                try (java.io.PrintWriter pw = new java.io.PrintWriter(
                        new java.io.FileWriter(cfg, true))) {
                    pw.println("lxc.idmap = u 0 524288 65536");
                    pw.println("lxc.idmap = g 0 524288 65536");
                }
            }
        }
    }

    /*
     * Retrieves detailed info about a single container.
     */
    public String getContainerInfo(String name) throws Exception {
        return runCommand("lxc-info", "-n", name);
    }
}
