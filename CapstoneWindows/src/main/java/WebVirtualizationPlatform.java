import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main Web Virtualization Platform Server
 * Provides web-based VM and Docker container management
 */
public class WebVirtualizationPlatform {

    private static final int DEFAULT_PORT = 8080;
    private Server server;
    private VirtualMachineManager vmManager;
    private DockerManager dockerManager;

    public WebVirtualizationPlatform(int port) {
        this.server = new Server(port);
        this.vmManager = new VirtualMachineManager();
        this.dockerManager = new DockerManager();
    }

    public void start() throws Exception {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Serve static HTML/CSS/JS files
        context.addServlet(new ServletHolder(new StaticFileServlet()), "/");
        context.addServlet(new ServletHolder(new APIServlet(vmManager, dockerManager)), "/api/*");

        // Note: WebSocket removed - using simple polling instead

        server.start();
        System.out.println("Web Virtualization Platform started on port " + DEFAULT_PORT);
        System.out.println("Access at: http://localhost:" + DEFAULT_PORT);
        server.join();
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
            WebVirtualizationPlatform platform = new WebVirtualizationPlatform(port);
            platform.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Servlet for serving static files from webapp folder or classpath
     */
    static class StaticFileServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String path = req.getRequestURI();

            // Default to index.html
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Set content type
            String contentType = getContentType(path);
            resp.setContentType(contentType);
            resp.setCharacterEncoding("UTF-8");

            try {
                String content = loadStaticFile(path);
                resp.getWriter().write(content);
            } catch (IOException e) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("404 - File Not Found: " + path);
            }
        }

        /**
         * Load static file from filesystem or classpath
         */
        private String loadStaticFile(String path) throws IOException {
            // Remove leading slash
            String fileName = path.startsWith("/") ? path.substring(1) : path;

            // Try to load from src/main/webapp first (development)
            Path webappPath = Paths.get("src/main/webapp", fileName);
            if (Files.exists(webappPath)) {
                return Files.readString(webappPath, StandardCharsets.UTF_8);
            }

            // Try to load from classpath (packaged JAR)
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("webapp/" + fileName)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            // If not found, throw exception
            throw new IOException("File not found: " + fileName);
        }

        /**
         * Determine content type based on file extension
         */
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "text/plain";
        }
    }
}