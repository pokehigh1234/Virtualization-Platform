class VirtualizationClient {
    constructor() {
        this.currentTab = 'vms';
        this.currentVM = null;
        this.currentContainer = null;
        this.vms = [];
        this.containers = [];
        this.refreshInterval = null;
        this.init();
    }

    init() {
        this.attachEventListeners();
        this.loadData();
        this.startAutoRefresh();
    }

    attachEventListeners() {
        // Tab switching
        document.querySelectorAll('.tab-button').forEach(btn => {
            btn.addEventListener('click', (e) => {
                this.switchTab(e.target.dataset.tab);
            });
        });

        // Refresh button
        document.getElementById('refreshBtn').addEventListener('click', () => {
            this.loadData();
        });

        // VM Modal
        document.getElementById('createVM').addEventListener('click', () => {
            document.getElementById('createVMModal').classList.add('active');
        });

        document.getElementById('createVMForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.createVM();
        });

        // Container Modal
        document.getElementById('createContainer').addEventListener('click', () => {
            document.getElementById('createContainerModal').classList.add('active');
        });

        document.getElementById('createContainerForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.createContainer();
        });

        // Cancel buttons
        document.querySelectorAll('.cancel-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.modal').forEach(modal => {
                    modal.classList.remove('active');
                });
            });
        });
    }

    switchTab(tabName) {
        this.currentTab = tabName;

        // Update tab buttons
        document.querySelectorAll('.tab-button').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });

        // Update tab content
        document.querySelectorAll('.tab-content').forEach(content => {
            content.classList.toggle('active', content.id === `${tabName}-tab`);
        });

        // Load data for active tab
        this.loadData();
    }

    async loadData() {
        if (this.currentTab === 'vms') {
            await this.loadVMs();
        } else if (this.currentTab === 'containers') {
            await this.loadContainers();
        }
    }

    startAutoRefresh() {
        // Auto-refresh every 5 seconds
        this.refreshInterval = setInterval(() => {
            this.loadData();
        }, 5000);
    }

    // ===== VM Functions =====

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
            diskSize: parseInt(document.getElementById('vmDisk').value),
            storageLocation: document.getElementById('vmStorage').value || null,
            isoPath: document.getElementById('vmISO').value || null
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
                alert('VM created successfully!');
            } else {
                const error = await response.json();
                alert('Failed to create VM: ' + error.error);
            }
        } catch (error) {
            console.error('Failed to create VM:', error);
            alert('Failed to create VM');
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

        const isoInfo = this.currentVM.isoPath
            ? `<div class="info-card">
                   <h3>ISO File</h3>
                   <p class="path-text" title="${this.currentVM.isoPath}">${this.currentVM.isoPath}</p>
               </div>`
            : '';

        const storageInfo = this.currentVM.storageLocation
            ? `<div class="info-card full-width">
                   <h3>Storage Location</h3>
                   <p class="path-text" title="${this.currentVM.storageLocation}">${this.currentVM.storageLocation}</p>
               </div>`
            : '';

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
                    ${isoInfo}
                    ${storageInfo}
                </div>
                <div class="vm-controls">
                    <button class="btn btn-primary" onclick="client.startVM()">Start</button>
                    <button class="btn" onclick="client.stopVM()">Stop</button>
                    <button class="btn" onclick="client.restartVM()">Restart</button>
                    <button class="btn btn-danger" onclick="client.deleteVM()">Delete</button>
                </div>
                <div style="background: #000; padding: 2rem; border-radius: 4px; margin-top: 1rem;">
                    <div style="color: #0078d4; font-family: monospace;">
                        Virtual Machine Display Area<br>
                        VM ID: ${this.currentVM.id}<br>
                        ${this.currentVM.status === 'running' ? '● System Running' : '○ System Stopped'}
                        ${this.currentVM.isoPath ? '<br>🔧 Boot from: ' + this.currentVM.isoPath.split(/[\\/]/).pop() : ''}
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
                setTimeout(() => this.loadVMs(), 1000);
            }
        } catch (error) {
            console.error(`Failed to ${command} VM:`, error);
        }
    }

    // ===== Docker Functions =====

    async loadContainers() {
        try {
            const response = await fetch('/api/containers?all=true');
            const containers = await response.json();
            this.containers = containers;
            this.renderContainerList();
        } catch (error) {
            console.error('Failed to load containers:', error);
        }
    }

    renderContainerList() {
        const containerList = document.getElementById('containerList');

        if (this.containers.length === 0) {
            containerList.innerHTML = '<p class="empty-state">No containers</p>';
            return;
        }

        containerList.innerHTML = this.containers.map(container => {
            const status = container.status.toLowerCase().includes('up') ? 'up' : 'exited';
            return `
                <div class="vm-item ${this.currentContainer?.id === container.id ? 'active' : ''}" data-container-id="${container.id}">
                    <div class="vm-name">${container.name}</div>
                    <div class="vm-status ${status}">${container.status}</div>
                </div>
            `;
        }).join('');

        containerList.querySelectorAll('.vm-item').forEach(item => {
            item.addEventListener('click', () => {
                const containerId = item.dataset.containerId;
                this.selectContainer(containerId);
            });
        });
    }

    async createContainer() {
        const containerData = {
            name: document.getElementById('containerName').value,
            image: document.getElementById('containerImage').value,
            ports: document.getElementById('containerPorts').value || '',
            volumes: document.getElementById('containerVolumes').value || ''
        };

        try {
            const response = await fetch('/api/containers', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(containerData)
            });

            if (response.ok) {
                document.getElementById('createContainerModal').classList.remove('active');
                document.getElementById('createContainerForm').reset();
                this.loadContainers();
                alert('Container created successfully!');
            } else {
                const error = await response.json();
                alert('Failed to create container: ' + error.error);
            }
        } catch (error) {
            console.error('Failed to create container:', error);
            alert('Failed to create container');
        }
    }

    async selectContainer(containerId) {
        const container = this.containers.find(c => c.id === containerId);
        if (!container) return;

        this.currentContainer = container;
        this.renderContainerList();
        this.renderContainerView();
    }

    renderContainerView() {
        const containerView = document.getElementById('containerView');

        if (!this.currentContainer) {
            containerView.innerHTML = `
                <div class="welcome">
                    <h2>Docker Containers</h2>
                    <p>Create or select a container to get started</p>
                </div>
            `;
            return;
        }

        const isRunning = this.currentContainer.status.toLowerCase().includes('up');

        containerView.innerHTML = `
            <div class="vm-display">
                <h2>${this.currentContainer.name}</h2>
                <div class="vm-info">
                    <div class="info-card">
                        <h3>Status</h3>
                        <p class="${isRunning ? 'running' : 'stopped'}">${this.currentContainer.status}</p>
                    </div>
                    <div class="info-card">
                        <h3>Image</h3>
                        <p style="font-size: 0.9rem !important;">${this.currentContainer.image}</p>
                    </div>
                    <div class="info-card full-width">
                        <h3>Container ID</h3>
                        <p style="font-size: 0.9rem !important;">${this.currentContainer.id}</p>
                    </div>
                    ${this.currentContainer.ports ? `
                        <div class="info-card full-width">
                            <h3>Ports</h3>
                            <p style="font-size: 0.9rem !important;">${this.currentContainer.ports}</p>
                        </div>
                    ` : ''}
                </div>
                <div class="vm-controls">
                    <button class="btn btn-primary" onclick="client.startContainer()">Start</button>
                    <button class="btn" onclick="client.stopContainer()">Stop</button>
                    <button class="btn" onclick="client.restartContainer()">Restart</button>
                    <button class="btn" onclick="client.viewLogs()">View Logs</button>
                    <button class="btn btn-danger" onclick="client.deleteContainer()">Remove</button>
                </div>
                <div id="logsContainer"></div>
            </div>
        `;
    }

    async startContainer() {
        if (!this.currentContainer) return;
        await this.sendContainerCommand('start');
    }

    async stopContainer() {
        if (!this.currentContainer) return;
        await this.sendContainerCommand('stop');
    }

    async restartContainer() {
        if (!this.currentContainer) return;
        await this.sendContainerCommand('restart');
    }

    async deleteContainer() {
        if (!this.currentContainer) return;
        if (!confirm(`Remove container "${this.currentContainer.name}"?`)) return;

        try {
            await fetch(`/api/containers/${this.currentContainer.id}`, { method: 'DELETE' });
            this.currentContainer = null;
            this.loadContainers();
            this.renderContainerView();
        } catch (error) {
            console.error('Failed to remove container:', error);
        }
    }

    async sendContainerCommand(command) {
        try {
            const response = await fetch(`/api/containers/${this.currentContainer.id}/${command}`, {
                method: 'POST'
            });

            if (response.ok) {
                setTimeout(() => this.loadContainers(), 1000);
            }
        } catch (error) {
            console.error(`Failed to ${command} container:`, error);
        }
    }

    async viewLogs() {
        if (!this.currentContainer) return;

        try {
            const response = await fetch(`/api/containers/${this.currentContainer.id}/logs?lines=100`);
            const data = await response.json();

            const logsContainer = document.getElementById('logsContainer');
            logsContainer.innerHTML = `
                <div class="logs-section">
                    <div class="logs-header">
                        <span>Container Logs (last 100 lines)</span>
                        <button class="btn btn-small" onclick="client.viewLogs()">Refresh Logs</button>
                    </div>
                    <div class="logs-content">${data.logs || 'No logs available'}</div>
                </div>
            `;
        } catch (error) {
            console.error('Failed to get logs:', error);
        }
    }
}

// Initialize the client
const client = new VirtualizationClient();