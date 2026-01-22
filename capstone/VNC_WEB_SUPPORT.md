# Web-Based VNC Support

## Overview

The KVM Manager now supports viewing and interacting with virtual machine consoles directly through a web browser via WebSocket-based VNC tunneling.

## Features

- **Web Console Access**: View VM console in the browser without external VNC clients
- **WebSocket Proxy**: Secure tunnel between browser and local VNC server
- **Real-time Connection Status**: Visual indicator showing connection state
- **Responsive Design**: Works on desktop and mobile browsers

## Architecture

### Components

1. **WebSocket Handler** (`VNCWebSocketHandler.java`)
   - Proxies WebSocket connections to local VNC servers
   - Bidirectional data transfer between browser and VNC
   - Automatic connection management

2. **Handshake Interceptor** (`VNCHandshakeInterceptor.java`)
   - Extracts VM name and VNC port during connection
   - Validates VM before establishing WebSocket tunnel

3. **WebSocket Configuration** (`WebSocketConfig.java`)
   - Registers WebSocket endpoints
   - Configures handler and interceptor

4. **Updated UI** (`vm.html`)
   - Embedded VNC viewer with Connect/Disconnect buttons
   - Real-time connection status indicator
   - Beautiful, responsive layout

5. **REST API Endpoint** (`/api/vm/{name}/vnc-port`)
   - Returns VNC port for a specific VM
   - Used by JavaScript for WebSocket connection

## Usage

### Accessing VM Console

1. Start your KVM application
2. Navigate to the VM details page
3. Click **"Connect to VNC"** button
4. The console should appear in the black panel

### Connection Flow

```
Browser → WebSocket (/ws/vnc/{vmName}) → VNCWebSocketHandler → Local VNC Server → VM Console
```

## Technical Details

### WebSocket Endpoint

- **URL**: `ws://localhost:8080/ws/vnc/{vmName}`
- **Protocol**: Binary WebSocket (arraybuffer)
- **Data Format**: Raw VNC protocol over WebSocket

### API Endpoints

```
GET /api/vm/{vmName}/vnc-port
Response: {
  "success": true,
  "port": 5901,
  "host": "localhost"
}
```

### VNC Port Discovery

- VNC ports are extracted from VM XML configuration
- Standard libvirt range: 5900 + display number
- Auto-assigned ports detected from domain ID

## Requirements

- Spring Boot 3.5.10+
- WebSocket support (included in dependencies)
- libvirt with VNC graphics configured for VMs

## Configuration

### pom.xml Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<dependency>
    <groupId>javax.websocket</groupId>
    <artifactId>javax.websocket-api</artifactId>
    <version>1.1</version>
</dependency>
```

### VM XML Configuration

Ensure your VMs have VNC graphics configured:

```xml
<graphics type='vnc' port='-1' autoport='yes'/>
```

## Troubleshooting

### Connection Fails

1. Verify VM is running
2. Check VNC port is accessible: `telnet localhost 5901`
3. Ensure libvirt VNC graphics are configured
4. Check browser console for error messages

### No Console Display

1. VM may not have a display server running
2. Try restarting the VM
3. Verify VM has graphics device configured

### Performance Issues

- WebSocket proxying has minimal overhead
- Performance depends on VM's display output
- Network bandwidth is the primary limitation

## Browser Compatibility

- Chrome 43+
- Firefox 45+
- Safari 10+
- Edge 12+
- Requires WebSocket support

## Security Notes

- WebSocket connections are not encrypted (use HTTPS for production)
- Consider implementing authentication for production deployments
- VNC protocol is not designed for untrusted networks
- Use SSH tunneling or VPN for remote access

## Future Enhancements

- Integration with full noVNC library for better rendering
- Clipboard support
- File transfer capability
- Connection persistence
- Multi-user sessions
