class VirtualizationClient {
    constructor() {
        this.ws = null;
        this.currentVM = null;
        this.vms = [];
        this.init();
    }
    
    init() {
        this.connectWebSocket();
        this.attachEventListeners();
        this.loadVMs();
    }
    
    connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;
        
        this.ws = new WebSocket(wsUrl);
        
        this.ws.onopen = () => {
            console.log('WebSocket connected');
            this.updateStatus(true);
        };
        
        this.ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            this.handleMessage(data);
        };
        
        this.ws.onclose = () => {
            console.log('WebSocket disconnected');
            this.updateStatus(false);
            setTimeout(() => this.connectWebSocket(), 3000);
        };
        
        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }
    
    updateStatus(connected) {
        const statusEl = document.getElementById('status');
        statusEl.textContent = connected ? 'Connected' : 'Disconnected';
        statusEl.className = connected ? 'status connected' : 'status disconnected';
    }
    
    attachEventListeners() {
        document.getElementById('createVM').addEventListener('click', () => {
            document.getElementById('createVMModal').classList.add('active');
        });
        
        document.getElementById('cancelCreate').addEventListener('click', () => {
            document.getElementById('createVMModal').classList.remove('active');
        });
        
        document.getElementById('createVMForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.createVM();
        });
        
        document.getElementById('refreshVMs').addEventListener('click', () => {
            this.loadVMs();
        });
        
        document.getElementById('terminalInput').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.executeCommand(e.target.value);
                e.target.value = '';
            }
        });
        
        document.getElementById('clearTerminal').addEventListener('click', () => {
            document.getElementById('terminalOutput').innerHTML = '';
        });
    }
    
    async loadVMs() {
        try {
            const response = await fetch('/api/vms');
            const vms = await response.json();
            this.vms = vms;
            this.renderVMList();
        } catch (error) {
            console.error('Failed to load VMs:', error);
        }
    }
    
    renderVMList() {
        const vmList = document.getElementById('vmList');
        
        if (this.vms.length === 0) {
            vmList.innerHTML = '<p class="empty-state">No virtual machines</p>';
            return;
        }
        
        vmList.innerHTML = this.vms.map(vm => `
            <div class="vm-item ${this.currentVM?.id === vm.id ? 'active' : ''}" data-vm-id="${vm.id}">
                <div class="vm-name">${vm.name}</div>
                <div class="vm-status ${vm.status}">${vm.status}</div>
            </div>
        `).join('');
        
        vmList.querySelectorAll('.vm-item').forEach(item => {
            item.addEventListener('click', () => {
                const vmId = item.dataset.vmId;
                this.selectVM(vmId);
            });
        });
    }
    
    async createVM() {
        const vmData = {
            name: document.getElementById('vmName').value,
            memory: parseInt(document.getElementById('vmMemory').value),
            cpuCores: parseInt(document.getElementById('vmCPU').value),
            diskSize: parseInt(document.getElementById('vmDisk').value)
        };
        
        try {
            const response = await fetch('/api/vms', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(vmData)
            });
            
            if (response.ok) {
                document.getElementById('createVMModal').classList.remove('active');
                document.getElementById('createVMForm').reset();
                this.loadVMs();
                this.addTerminalLine('VM created successfully', false);
            }
        } catch (error) {
            console.error('Failed to create VM:', error);
            this.addTerminalLine('Failed to create VM', true);
        }
    }
    
    async selectVM(vmId) {
        const vm = this.vms.find(v => v.id === vmId);
        if (!vm) return;
        
        this.currentVM = vm;
        this.renderVMList();
        this.renderVMView();
    }
    
    renderVMView() {
        const vmView = document.getElementById('vmView');
        
        if (!this.currentVM) {
            vmView.innerHTML = `
                <div class="welcome">
                    <h2>Welcome to Web Virtualization Platform</h2>
                    <p>Create or select a virtual machine to get started</p>
                </div>
            `;
            return;
        }
        
        vmView.innerHTML = `
            <div class="vm-display">
                <h2>${this.currentVM.name}</h2>
                <div class="vm-info">
                    <div class="info-card">
                        <h3>Status</h3>
                        <p class="${this.currentVM.status}">${this.currentVM.status}</p>
                    </div>
                    <div class="info-card">
                        <h3>Memory</h3>
                        <p>${this.currentVM.memory} MB</p>
                    </div>
                    <div class="info-card">
                        <h3>CPU Cores</h3>
                        <p>${this.currentVM.cpuCores}</p>
                    </div>
                    <div class="info-card">
                        <h3>Disk Size</h3>
                        <p>${this.currentVM.diskSize} GB</p>
                    </div>
                </div>
                <div class="vm-controls">
                    <button class="btn btn-primary" onclick="client.startVM()">Start</button>
                    <button class="btn" onclick="client.stopVM()">Stop</button>
                    <button class="btn" onclick="client.restartVM()">Restart</button>
                    <button class="btn" onclick="client.deleteVM()" style="background: #7d2d2d;">Delete</button>
                </div>
                <div style="background: #000; padding: 2rem; border-radius: 4px; margin-top: 1rem;">
                    <div style="color: #0078d4; font-family: monospace;">
                        Virtual Machine Display Area<br>
                        VM ID: ${this.currentVM.id}<br>
                        ${this.currentVM.status === 'running' ? '● System Running' : '○ System Stopped'}
                    </div>
                </div>
            </div>
        `;
    }
    
    async startVM() {
        if (!this.currentVM) return;
        await this.sendVMCommand('start');
    }
    
    async stopVM() {
        if (!this.currentVM) return;
        await this.sendVMCommand('stop');
    }
    
    async restartVM() {
        if (!this.currentVM) return;
        await this.sendVMCommand('restart');
    }
    
    async deleteVM() {
        if (!this.currentVM) return;
        if (!confirm(`Delete VM "${this.currentVM.name}"?`)) return;
        
        try {
            await fetch(`/api/vms/${this.currentVM.id}`, { method: 'DELETE' });
            this.currentVM = null;
            this.loadVMs();
            this.renderVMView();
        } catch (error) {
            console.error('Failed to delete VM:', error);
        }
    }
    
    async sendVMCommand(command) {
        try {
            const response = await fetch(`/api/vms/${this.currentVM.id}/${command}`, {
                method: 'POST'
            });
            
            if (response.ok) {
                this.addTerminalLine(`VM ${command} command sent`, false);
                setTimeout(() => this.loadVMs(), 1000);
            }
        } catch (error) {
            console.error(`Failed to ${command} VM:`, error);
            this.addTerminalLine(`Failed to ${command} VM`, true);
        }
    }
    
    executeCommand(command) {
        if (!command.trim()) return;
        
        this.addTerminalLine(`$ ${command}`, false);
        
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({
                type: 'command',
                vmId: this.currentVM?.id,
                command: command
            }));
        } else {
            this.addTerminalLine('Not connected to server', true);
        }
    }
    
    handleMessage(data) {
        switch (data.type) {
            case 'terminal':
                this.addTerminalLine(data.output, data.error);
                break;
            case 'vm_status':
                this.loadVMs();
                break;
        }
    }
    
    addTerminalLine(text, isError = false) {
        const output = document.getElementById('terminalOutput');
        const line = document.createElement('div');
        line.className = `terminal-line ${isError ? 'error' : ''}`;
        line.textContent = text;
        output.appendChild(line);
        output.scrollTop = output.scrollHeight;
    }
}

// Initialize the client
const client = new VirtualizationClient();
