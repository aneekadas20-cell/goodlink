/**
 * GoodLink Interactive Web Portal & Algorithmic Lab
 * Backed by Spring Boot 3.2.5 REST API & Custom Data Structures
 */

// Application State
let appData = {
    ngos: [],
    activities: [],
    volunteers: [],
    donations: [],
    urgentRequests: [],
    achievements: [],
    categoryTreeAscii: '',
    stats: {}
};

// DOM Ready initialization
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initForms();
    initBotConsole();
    fetchPlatformData();

    // Auto-refresh data every 8 seconds
    setInterval(fetchPlatformData, 8000);
});

/* ==========================================================================
   Navigation & Tabs
   ========================================================================== */

function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', () => {
            const tabId = item.getAttribute('data-tab');
            switchTab(tabId);
        });
    });

    document.getElementById('btn-refresh').addEventListener('click', () => {
        fetchPlatformData(true);
    });

    document.getElementById('nav-btn-bot').addEventListener('click', (e) => {
        e.preventDefault();
        switchTab('bot-simulator');
    });
}

function switchTab(tabId) {
    // Update nav items
    document.querySelectorAll('.nav-item').forEach(item => {
        if (item.getAttribute('data-tab') === tabId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    // Update panes
    document.querySelectorAll('.tab-pane').forEach(pane => {
        if (pane.id === `pane-${tabId}`) {
            pane.classList.add('active');
        } else {
            pane.classList.remove('active');
        }
    });
}

/* ==========================================================================
   Data Fetching & State Sync
   ========================================================================== */

async function fetchPlatformData(isManual = false) {
    try {
        const response = await fetch('/admin/data');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        appData = data;

        updateServerStatus(true);
        renderOverviewMetrics();
        renderDsLab();
        renderActivitiesCatalog();
        renderVolunteerRoster();
        renderDonationsAndUrgent();
        renderAchievementsWall();
        populateNgoSelectDropdown();

        if (isManual) {
            showToast('Platform data synced successfully', 'success');
        }
    } catch (err) {
        console.warn('Could not sync with /admin/data:', err);
        updateServerStatus(false);
        if (isManual) {
            showToast('Unable to connect to GoodLink backend on :8081', 'error');
        }
    }
}

function updateServerStatus(online) {
    const pill = document.getElementById('server-status-pill');
    const label = document.getElementById('status-text');
    const dot = pill.querySelector('.status-dot');

    if (online) {
        label.textContent = 'Server Online :8081';
        dot.style.background = '#10b981';
        dot.style.boxShadow = '0 0 8px #10b981';
    } else {
        label.textContent = 'Server Disconnected';
        dot.style.background = '#f43f5e';
        dot.style.boxShadow = '0 0 8px #f43f5e';
    }
}

/* ==========================================================================
   Render Dashboard & Metrics
   ========================================================================== */

function renderOverviewMetrics() {
    const stats = appData.stats || {};
    document.getElementById('val-ngos-count').textContent = stats.ngosCount ?? appData.ngos.length;
    document.getElementById('val-activities-count').textContent = stats.activitiesCount ?? appData.activities.length;
    document.getElementById('val-volunteers-count').textContent = stats.volunteersCount ?? appData.volunteers.length;
    document.getElementById('val-donations-count').textContent = stats.donationsCount ?? appData.donations.length;
    document.getElementById('val-urgent-count').textContent = stats.urgentCount ?? 0;
    document.getElementById('val-normal-count').textContent = `${stats.normalRequestsCount ?? 0} Normal Queued`;
    document.getElementById('val-achievements-count').textContent = stats.achievementsCount ?? appData.achievements.length;

    // Render quick tree preview
    const treeDisplay = document.getElementById('category-tree-display');
    if (treeDisplay && appData.categoryTreeAscii) {
        treeDisplay.textContent = appData.categoryTreeAscii;
    }
}

/* ==========================================================================
   Render Data Structures Lab
   ========================================================================== */

function renderDsLab() {
    // 1. NgoBST
    document.getElementById('badge-bst-size').textContent = `${appData.ngos.length} Nodes in BST`;
    const bstContainer = document.getElementById('bst-visual-container');
    if (bstContainer) {
        bstContainer.innerHTML = '';
        if (appData.ngos.length === 0) {
            bstContainer.innerHTML = '<div class="empty-lane">NgoBST is empty</div>';
        } else {
            appData.ngos.forEach(ngo => {
                const nodeCard = document.createElement('div');
                nodeCard.className = 'bst-node-card';
                nodeCard.innerHTML = `
                    <div style="display:flex; align-items:center; gap:0.75rem;">
                        <span class="bst-node-key">#${ngo.id}</span>
                        <div>
                            <div class="bst-node-info"><strong>${escapeHtml(ngo.name)}</strong> (${escapeHtml(ngo.category)})</div>
                            <div class="bst-node-pointers">Location: ${escapeHtml(ngo.city)} &bull; ${escapeHtml(ngo.email)}</div>
                        </div>
                    </div>
                    <span class="badge badge-emerald">BST Node</span>
                `;
                bstContainer.appendChild(nodeCard);
            });
        }
    }

    // 2. ActivityArray
    document.getElementById('badge-array-size').textContent = `${appData.activities.length} Elements`;
    const arrayContainer = document.getElementById('array-visual-container');
    if (arrayContainer) {
        arrayContainer.innerHTML = '';
        const capacity = Math.max(appData.activities.length + 3, 8);
        for (let i = 0; i < capacity; i++) {
            const cell = document.createElement('div');
            if (i < appData.activities.length) {
                const act = appData.activities[i];
                cell.className = 'array-cell filled';
                cell.innerHTML = `
                    <span class="array-cell-index">[${i}]</span>
                    <div class="array-cell-content" title="${escapeHtml(act.title)}">${escapeHtml(act.title)}</div>
                    <div class="array-cell-sub">#${act.id} &bull; ${act.volunteerCount || 0} Vol</div>
                `;
            } else {
                cell.className = 'array-cell';
                cell.innerHTML = `
                    <span class="array-cell-index">[${i}]</span>
                    <div class="array-cell-content" style="color:var(--text-muted); font-weight:normal;">[Empty]</div>
                    <div class="array-cell-sub">Pre-allocated</div>
                `;
            }
            arrayContainer.appendChild(cell);
        }
    }

    // 3. UrgentRequests Dual-Queue
    const urgentLane = document.getElementById('queue-urgent-list');
    const normalLane = document.getElementById('queue-normal-list');
    if (urgentLane && normalLane) {
        urgentLane.innerHTML = '';
        normalLane.innerHTML = '';

        const urgentItems = appData.urgentRequests.filter(r => r.urgent);
        const normalItems = appData.urgentRequests.filter(r => !r.urgent);

        if (urgentItems.length === 0) {
            urgentLane.innerHTML = '<div class="empty-lane">No urgent emergency requests</div>';
        } else {
            urgentItems.forEach(item => {
                const node = document.createElement('div');
                node.className = 'queue-node-item';
                node.innerHTML = `
                    <div style="color:#fb7185; font-weight:700;">🚨 ${escapeHtml(item.type)}</div>
                    <div>${escapeHtml(item.details)}</div>
                    <div style="color:var(--text-muted); font-size:0.68rem;">${escapeHtml(item.city)} &bull; ${item.date}</div>
                `;
                urgentLane.appendChild(node);
            });
        }

        if (normalItems.length === 0) {
            normalLane.innerHTML = '<div class="empty-lane">No standard requests queued</div>';
        } else {
            normalItems.forEach(item => {
                const node = document.createElement('div');
                node.className = 'queue-node-item';
                node.innerHTML = `
                    <div style="color:#fcd34d; font-weight:700;">📦 ${escapeHtml(item.type)}</div>
                    <div>${escapeHtml(item.details)}</div>
                    <div style="color:var(--text-muted); font-size:0.68rem;">${escapeHtml(item.city)} &bull; ${item.date}</div>
                `;
                normalLane.appendChild(node);
            });
        }
    }

    // 4. Donations MyQueue (FIFO)
    document.getElementById('badge-donations-size').textContent = `${appData.donations.length} Items Queued`;
    const donationsQueueVisual = document.getElementById('donations-queue-visual');
    if (donationsQueueVisual) {
        donationsQueueVisual.innerHTML = '';
        if (appData.donations.length === 0) {
            donationsQueueVisual.innerHTML = '<div class="empty-lane">FIFO Queue is empty. Ready for new pledges.</div>';
        } else {
            appData.donations.forEach((d, idx) => {
                const node = document.createElement('div');
                node.className = 'fifo-node';
                node.innerHTML = `
                    <div style="font-size:0.68rem; color:var(--primary); font-weight:700;">FIFO #${idx + 1}</div>
                    <div style="font-weight:700; color:#fff; font-size:0.85rem;">${escapeHtml(d.item)}</div>
                    <div style="color:var(--cyan); font-size:0.75rem;">Qty: ${d.quantity}</div>
                `;
                donationsQueueVisual.appendChild(node);
            });
        }
    }

    // 5. Achievements MyLinkedList
    document.getElementById('badge-achievements-size').textContent = `${appData.achievements.length} Recorded`;
    const achievementsVisual = document.getElementById('achievements-strip-visual');
    if (achievementsVisual) {
        achievementsVisual.innerHTML = '';
        if (appData.achievements.length === 0) {
            achievementsVisual.innerHTML = '<div class="empty-lane">No achievements recorded yet.</div>';
        } else {
            appData.achievements.forEach((ach, index) => {
                const node = document.createElement('div');
                node.className = 'linked-node';
                node.innerHTML = `
                    ${index === 0 ? '<span class="node-badge-newest">HEAD (Newest)</span>' : '<span style="font-family:var(--font-mono); color:var(--text-muted); font-size:0.7rem;">#' + (index + 1) + '</span>'}
                    <div style="flex:1;">
                        <strong>${escapeHtml(ach.title)}</strong>
                        <div style="font-size:0.75rem; color:var(--text-muted);">${escapeHtml(ach.volunteerName)} &bull; ${ach.date}</div>
                    </div>
                `;
                achievementsVisual.appendChild(node);
            });
        }
    }

    // 6. CategoryTree Lab ASCII
    const labTree = document.getElementById('category-tree-lab-display');
    if (labTree && appData.categoryTreeAscii) {
        labTree.textContent = appData.categoryTreeAscii;
    }
}

/* ==========================================================================
   Render Activities Catalog & Action Handlers
   ========================================================================== */

function renderActivitiesCatalog() {
    const container = document.getElementById('activities-container');
    if (!container) return;
    container.innerHTML = '';

    if (appData.activities.length === 0) {
        container.innerHTML = '<div class="empty-lane">No activities scheduled yet.</div>';
        return;
    }

    appData.activities.forEach(act => {
        const card = document.createElement('div');
        card.className = 'activity-card';

        const volunteerPills = (act.volunteers && act.volunteers.length > 0)
            ? act.volunteers.map(v => `
                <span class="vol-pill" title="Chat ID: ${v.chatId}">
                    👤 ${escapeHtml(v.name)}
                    <button class="btn btn-sm btn-outline" style="padding:0.1rem 0.4rem; margin-left:0.3rem; font-size:0.65rem;" onclick="adminComplete(${act.id}, ${v.chatId})" title="Mark complete & email certificate">Complete</button>
                    <a href="/admin/certificate/${act.id}/${v.chatId}" target="_blank" class="btn btn-sm btn-secondary" style="padding:0.1rem 0.4rem; margin-left:0.2rem; font-size:0.65rem;" title="Download PDF Certificate">PDF</a>
                </span>
            `).join('')
            : '<span style="font-size:0.75rem; color:var(--text-muted); font-style:italic;">No volunteers registered yet</span>';

        card.innerHTML = `
            <div>
                <div class="activity-top">
                    <span class="activity-id-pill">Activity #${act.id}</span>
                    <span class="activity-date">📅 ${escapeHtml(act.date)}</span>
                </div>
                <h3 class="activity-title">${escapeHtml(act.title)}</h3>
                <div class="activity-ngo-name">
                    <span>🏢</span>
                    <span>${escapeHtml(act.ngoName)}</span>
                </div>
                <div class="volunteer-roster-section">
                    <div class="roster-header">
                        <span>Enrolled Volunteers (${act.volunteerCount || 0})</span>
                        <span style="font-family:var(--font-mono); font-size:0.68rem; color:var(--cyan);">MyLinkedList</span>
                    </div>
                    <div class="roster-pills">
                        ${volunteerPills}
                    </div>
                </div>
            </div>
            <div class="activity-actions">
                <button class="btn btn-primary btn-sm" style="flex:1;" onclick="promptJoinActivity(${act.id})">
                    <span>➕ Join Drive</span>
                </button>
            </div>
        `;
        container.appendChild(card);
    });
}

function promptJoinActivity(activityId) {
    const chatId = prompt(`Enter Volunteer Telegram Chat ID to join Activity #${activityId}:`, '1001');
    if (!chatId) return;

    joinActivity(chatId, activityId);
}

async function joinActivity(chatId, activityId) {
    try {
        const formData = new URLSearchParams();
        formData.append('chatId', chatId);
        formData.append('activityId', activityId);

        const response = await fetch('/admin/join', {
            method: 'POST',
            body: formData
        });
        const data = await response.json();

        if (data.status === 'SUCCESS' || data.status === 'INFO') {
            showToast(data.message.replace(/[*_`]/g, ''), 'success');
            fetchPlatformData();
        } else {
            showToast(data.message.replace(/[*_`]/g, ''), 'error');
        }
    } catch (err) {
        showToast('Failed to join activity: ' + err.message, 'error');
    }
}

async function adminComplete(activityId, chatId) {
    if (!confirm(`Mark participation complete for Chat ID ${chatId} on Activity #${activityId}?\nThis will generate the OpenPDF certificate, dispatch the completion email, and post to Achievements Wall.`)) {
        return;
    }

    try {
        const response = await fetch(`/admin/complete/${activityId}/${chatId}`, {
            method: 'POST'
        });
        const data = await response.json();

        if (response.ok && data.status === 'SUCCESS') {
            showToast('Participation completed! Certificate generated & emailed.', 'success');
            // Offer instant certificate preview
            window.open(`/admin/certificate/${activityId}/${chatId}`, '_blank');
            fetchPlatformData();
        } else {
            showToast(data.message || 'Error completing participation', 'error');
        }
    } catch (err) {
        showToast('Completion failed: ' + err.message, 'error');
    }
}

/* ==========================================================================
   Render Volunteer Roster
   ========================================================================== */

function renderVolunteerRoster() {
    const tbody = document.getElementById('volunteers-tbody');
    const badge = document.getElementById('badge-volunteer-count');
    if (!tbody) return;

    badge.textContent = `${appData.volunteers.length} Volunteer${appData.volunteers.length === 1 ? '' : 's'}`;
    tbody.innerHTML = '';

    if (appData.volunteers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center" style="color:var(--text-muted); padding:1.5rem;">No volunteers registered yet.</td></tr>';
        return;
    }

    appData.volunteers.forEach(v => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="chatid-mono">${v.chatId}</td>
            <td><strong>${escapeHtml(v.name)}</strong></td>
            <td>${escapeHtml(v.email)}</td>
            <td><span class="badge badge-emerald">Verified</span></td>
        `;
        tbody.appendChild(tr);
    });
}

/* ==========================================================================
   Render Donations & Urgent Requests
   ========================================================================== */

function renderDonationsAndUrgent() {
    const donationsList = document.getElementById('donations-display-list');
    if (donationsList) {
        donationsList.innerHTML = '';
        if (appData.donations.length === 0) {
            donationsList.innerHTML = '<div class="empty-state">No donations currently queued.</div>';
        } else {
            appData.donations.forEach((d, i) => {
                const item = document.createElement('div');
                item.className = 'bst-node-card';
                item.style.marginBottom = '0.5rem';
                item.innerHTML = `
                    <div>
                        <strong>#${i + 1}: ${escapeHtml(d.item)}</strong> (Quantity: ${d.quantity})
                        <div style="font-size:0.75rem; color:var(--text-muted);">Pledged by ChatId: ${d.chatId} &bull; ${d.date}</div>
                    </div>
                    <span class="badge badge-indigo">FIFO Queue</span>
                `;
                donationsList.appendChild(item);
            });
        }
    }

    const urgentList = document.getElementById('urgent-display-list');
    if (urgentList) {
        urgentList.innerHTML = '';
        if (appData.urgentRequests.length === 0) {
            urgentList.innerHTML = '<div class="empty-state">No active emergency requests.</div>';
        } else {
            appData.urgentRequests.forEach(req => {
                const item = document.createElement('div');
                item.className = 'bst-node-card';
                item.style.marginBottom = '0.5rem';
                item.style.borderColor = req.urgent ? 'rgba(244, 63, 94, 0.4)' : 'rgba(245, 158, 11, 0.4)';
                item.innerHTML = `
                    <div>
                        <strong style="color:${req.urgent ? '#fb7185' : '#fcd34d'}">${req.urgent ? '🚨 URGENT' : '📦 NORMAL'}: ${escapeHtml(req.type)} in ${escapeHtml(req.city)}</strong>
                        <div style="font-size:0.82rem; margin-top:0.2rem;">${escapeHtml(req.details)}</div>
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-top:0.2rem;">Reported: ${req.date} &bull; ChatId: ${req.chatId}</div>
                    </div>
                    <span class="badge ${req.urgent ? 'badge-rose' : 'badge-amber'}">${req.urgent ? 'Top Priority' : 'Normal'}</span>
                `;
                urgentList.appendChild(item);
            });
        }
    }
}

/* ==========================================================================
   Render Achievements Wall
   ========================================================================== */

function renderAchievementsWall() {
    const container = document.getElementById('achievements-timeline-container');
    if (!container) return;
    container.innerHTML = '';

    if (appData.achievements.length === 0) {
        container.innerHTML = '<div class="empty-lane">No achievements recorded on the wall yet.</div>';
        return;
    }

    appData.achievements.forEach((ach, index) => {
        const card = document.createElement('div');
        card.className = 'achievement-card';
        card.innerHTML = `
            <div class="ach-icon-circle">🏆</div>
            <div class="ach-content">
                <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:0.25rem;">
                    <h3 class="ach-title">${escapeHtml(ach.title)}</h3>
                    ${index === 0 ? '<span class="badge badge-amber">Latest Milestone</span>' : ''}
                </div>
                <p class="ach-desc">${escapeHtml(ach.description)}</p>
                <div class="ach-meta">
                    Awarded to: <span>${escapeHtml(ach.volunteerName)}</span> &bull; <span>${ach.date}</span>
                </div>
            </div>
        `;
        container.appendChild(card);
    });
}

/* ==========================================================================
   Forms & Registrations
   ========================================================================== */

function initForms() {
    // 1. Quick Register
    const formQuick = document.getElementById('form-quick-register');
    if (formQuick) {
        formQuick.addEventListener('submit', async (e) => {
            e.preventDefault();
            const chatId = document.getElementById('quick-chatId').value;
            const name = document.getElementById('quick-name').value;
            const email = document.getElementById('quick-email').value;
            await handleRegister(chatId, name, email, 'quick-reg-feedback', formQuick);
        });
    }

    // 2. Full Register
    const formFull = document.getElementById('form-full-register');
    if (formFull) {
        formFull.addEventListener('submit', async (e) => {
            e.preventDefault();
            const chatId = document.getElementById('reg-chatId').value;
            const name = document.getElementById('reg-name').value;
            const email = document.getElementById('reg-email').value;
            await handleRegister(chatId, name, email, 'full-reg-feedback', formFull);
        });
    }

    // 3. Pledge Donation
    const formDonation = document.getElementById('form-pledge-donation');
    if (formDonation) {
        formDonation.addEventListener('submit', async (e) => {
            e.preventDefault();
            const chatId = document.getElementById('donate-chatId').value;
            const item = document.getElementById('donate-item').value;
            const quantity = document.getElementById('donate-quantity').value;

            try {
                const formData = new URLSearchParams();
                formData.append('chatId', chatId);
                formData.append('item', item);
                formData.append('quantity', quantity);

                const response = await fetch('/admin/donate', {
                    method: 'POST',
                    body: formData
                });
                const data = await response.json();
                showToast(data.message.replace(/[*_`]/g, ''), 'success');
                formDonation.reset();
                fetchPlatformData();
            } catch (err) {
                showToast('Failed to pledge donation: ' + err.message, 'error');
            }
        });
    }

    // 4. File Urgent Request
    const formUrgent = document.getElementById('form-file-urgent');
    if (formUrgent) {
        formUrgent.addEventListener('submit', async (e) => {
            e.preventDefault();
            const chatId = document.getElementById('urgent-chatId').value;
            const type = document.getElementById('urgent-type').value;
            const details = document.getElementById('urgent-details').value;
            const city = document.getElementById('urgent-city').value;

            try {
                const formData = new URLSearchParams();
                formData.append('chatId', chatId);
                formData.append('type', type);
                formData.append('details', details);
                formData.append('city', city);

                const response = await fetch('/admin/urgent', {
                    method: 'POST',
                    body: formData
                });
                const data = await response.json();
                showToast(data.message.replace(/[*_`]/g, ''), 'success');
                formUrgent.reset();
                fetchPlatformData();
            } catch (err) {
                showToast('Failed to file urgent request: ' + err.message, 'error');
            }
        });
    }

    // 5. Add New Activity
    const formAddAct = document.getElementById('form-add-activity');
    if (formAddAct) {
        formAddAct.addEventListener('submit', async (e) => {
            e.preventDefault();
            const id = document.getElementById('act-id').value;
            const ngoId = document.getElementById('act-ngo-id').value;
            const title = document.getElementById('act-title').value;
            const date = document.getElementById('act-date').value;

            try {
                const formData = new URLSearchParams();
                formData.append('id', id);
                formData.append('ngoId', ngoId);
                formData.append('title', title);
                formData.append('date', date);

                const response = await fetch('/admin/activity', {
                    method: 'POST',
                    body: formData
                });
                const data = await response.json();
                showToast(data.message, 'success');
                closeAddActivityModal();
                formAddAct.reset();
                fetchPlatformData();
            } catch (err) {
                showToast('Failed to save activity: ' + err.message, 'error');
            }
        });
    }

    // BST Search Bar
    const btnBstSearch = document.getElementById('btn-bst-search');
    if (btnBstSearch) {
        btnBstSearch.addEventListener('click', () => {
            const idInput = document.getElementById('bst-search-id').value;
            const resPill = document.getElementById('bst-search-result');
            if (!idInput) {
                resPill.textContent = '';
                return;
            }
            const targetId = parseInt(idInput);
            const found = appData.ngos.find(n => n.id === targetId);
            if (found) {
                resPill.textContent = `Found: ${found.name} (${found.category})`;
                resPill.className = 'search-result-pill badge-emerald';
            } else {
                resPill.textContent = `NGO #${targetId} not in BST`;
                resPill.className = 'search-result-pill badge-rose';
            }
        });
    }
}

async function handleRegister(chatId, name, email, feedbackId, form) {
    const feedback = document.getElementById(feedbackId);
    try {
        const formData = new URLSearchParams();
        formData.append('chatId', chatId);
        formData.append('name', name);
        formData.append('email', email);

        const response = await fetch('/admin/register', {
            method: 'POST',
            body: formData
        });
        const data = await response.json();

        if (response.ok && data.status === 'SUCCESS') {
            if (feedback) {
                feedback.className = 'form-feedback success';
                feedback.textContent = `✅ Successfully registered ${data.name} (Chat ID: ${data.chatId})`;
            }
            showToast(`Volunteer ${data.name} registered`, 'success');
            form.reset();
            fetchPlatformData();
        } else {
            throw new Error(data.message || 'Registration failed');
        }
    } catch (err) {
        if (feedback) {
            feedback.className = 'form-feedback error';
            feedback.textContent = `❌ ${err.message}`;
        }
        showToast('Registration error: ' + err.message, 'error');
    }
}

function populateNgoSelectDropdown() {
    const select = document.getElementById('act-ngo-id');
    if (!select) return;
    select.innerHTML = '';
    appData.ngos.forEach(ngo => {
        const opt = document.createElement('option');
        opt.value = ngo.id;
        opt.textContent = `#${ngo.id} - ${ngo.name} (${ngo.category})`;
        select.appendChild(opt);
    });
}

function openAddActivityModal() {
    const modal = document.getElementById('modal-add-activity');
    if (modal) {
        modal.classList.add('open');
        // suggest next ID
        const nextId = (appData.activities.length > 0)
            ? Math.max(...appData.activities.map(a => a.id)) + 1
            : 1;
        document.getElementById('act-id').value = nextId;
    }
}

function closeAddActivityModal() {
    const modal = document.getElementById('modal-add-activity');
    if (modal) modal.classList.remove('open');
}

/* ==========================================================================
   Bot Simulator Console
   ========================================================================== */

function initBotConsole() {
    const form = document.getElementById('bot-input-form');
    if (!form) return;

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const input = document.getElementById('bot-input-text');
        const text = input.value.trim();
        if (!text) return;

        sendBotMessage(text);
        input.value = '';
    });
}

function sendQuickBotMsg(cmd) {
    sendBotMessage(cmd);
}

async function sendBotMessage(messageText) {
    const chatId = document.getElementById('bot-chatid-input').value || 987654321;
    appendChatMessage('user', messageText);

    try {
        const formData = new URLSearchParams();
        formData.append('chatId', chatId);
        formData.append('message', messageText);

        const response = await fetch('/admin/bot/simulate', {
            method: 'POST',
            body: formData
        });
        const data = await response.json();

        appendChatMessage('bot', data.reply);
        // Refresh backend data in case command modified state (e.g. JOIN, REGISTER, DONATE, URGENT)
        fetchPlatformData();
    } catch (err) {
        appendChatMessage('bot', `⚠️ Could not reach GoodLinkBot backend: ${err.message}`);
    }
}

function appendChatMessage(sender, text) {
    const container = document.getElementById('chat-messages-container');
    if (!container) return;

    const msgDiv = document.createElement('div');
    msgDiv.className = `chat-msg ${sender}`;

    const formatted = formatTelegramMarkdown(text);
    const now = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    msgDiv.innerHTML = `
        <div class="bubble">${formatted}</div>
        <span class="msg-time">${now}</span>
    `;

    container.appendChild(msgDiv);
    container.scrollTop = container.scrollHeight;
}

function formatTelegramMarkdown(raw) {
    if (!raw) return '';
    let text = escapeHtml(raw);

    // Bold *text*
    text = text.replace(/\*(.*?)\*/g, '<strong>$1</strong>');
    // Italics _text_
    text = text.replace(/_(.*?)_/g, '<em>$1</em>');
    // Inline code `code`
    text = text.replace(/`([^`]+)`/g, '<code>$1</code>');
    // Code block ```code```
    text = text.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');

    return text;
}

/* ==========================================================================
   Utilities & Toast
   ========================================================================== */

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(30px)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
