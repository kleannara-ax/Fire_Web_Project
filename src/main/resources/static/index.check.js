

// ===== ?袁⑸열 ?怨밴묶 =====
let currentUser = null;
let isAdmin = false;
let outdoorHydrants = [];
let currentOutdoorId = null;
let outdoorMode = false;
let outdoorAddMode = false;
let outdoorMoveMode = false;
let outdoorByKey = new Map();
let currentInspectHydrantId = null;

// ===== JWT ?醫뤾쿃 ?온??=====
function getToken() {
    return localStorage.getItem('fireweb_token') || localStorage.getItem('fw_token');
}
function setToken(token) {
    localStorage.setItem('fireweb_token', token);
    localStorage.setItem('fw_token', token);
}

async function apiFetch(url, options = {}) {
    const token = getToken();
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const res = await fetch(url, { ...options, headers });
    if (res.status === 401) {
        window.location.href = '/login.html';
        return null;
    }
    return res;
}

// ===== 嚥≪뮄??袁⑹뜍 =====
function logout() {
    localStorage.removeItem('fireweb_token');
    localStorage.removeItem('fw_token');
    localStorage.removeItem('fireweb_user');
    localStorage.removeItem('fw_user');
    window.location.href = '/login.html';
}

// ===== ?λ뜃由?? ?紐꾩쵄 ?類ㅼ뵥 獄???????類ｋ궖 嚥≪뮆諭?=====
async function init() {
    const token = getToken();
    if (!token) {
        window.location.href = '/login.html';
        return;
    }
    try {
        const savedUser = localStorage.getItem('fireweb_user');
        const fallbackUser = localStorage.getItem('fw_user');
        if (savedUser) {
            currentUser = JSON.parse(savedUser);
            isAdmin = currentUser.role === 'ADMIN';
        } else if (fallbackUser) {
            currentUser = { username: fallbackUser, displayName: fallbackUser, role: 'USER' };
        }
        const accountBtn = document.getElementById('accountBtn');
        if (accountBtn && currentUser) {
            accountBtn.textContent = currentUser.username || '?ъ슜??;
        }
        if (isAdmin) {
            const adminLi = document.getElementById('adminMenuLi');
            if (adminLi) adminLi.style.display = '';
            ['btnOutdoorAdd','btnOutdoorMove','btnOutdoorEdit'].forEach(id => {
                const el = document.getElementById(id);
                if (el) el.style.display = '';
            });
            const editCol = document.getElementById('outdoorSelectEditCol');
            if (editCol) editCol.style.display = '';
        } else {
            setOutdoorHint('??곗뺘 ????癒?뮉 ?癒?筌?揶쎛?館鍮??덈뼄.', 'error');
        }
        await loadOutdoorHydrants();
    } catch(e) {
        console.error('init error', e);
    }
}

// ===== ?關????곗넅???怨쀬뵠??嚥≪뮆諭?=====
async function loadOutdoorHydrants() {
    try {
        const res = await apiFetch('/fire-api/hydrants?size=200&page=0');
        if (!res) return;
        const json = await res.json();
        if (json.ok && json.data && json.data.content) {
            outdoorHydrants = json.data.content
                .filter(h => h.hydrantType === 'Outdoor' && h.active !== false);
        }
        renderOutdoorMarkers();
    } catch(e) {
        console.error('??곗넅??嚥≪뮆諭???살첒', e);
    }
}

// ===== DOM 筌〓챷??=====
const screen = document.getElementById('screen');
const menuBtn = document.getElementById('menuBtn');
const overlay = document.getElementById('overlay');
const scene = document.getElementById('scene');
const mapStage = document.getElementById('mapStage');
const tooltip = document.getElementById('zoneTooltip');
const zoneSvg = document.getElementById('zoneOverlay');
const zonePopup = document.getElementById('zonePopup');
const infoTitle = document.getElementById('infoTitle');
const infoSub = document.getElementById('infoSub');
const infoBody = document.getElementById('infoBody');
const outdoorToggle = document.getElementById('outdoorToggle');
const outdoorPanel = document.getElementById('outdoorPanel');
const outdoorMarkers = document.getElementById('outdoorMarkers');
const outdoorSelect = document.getElementById('outdoorSelect');
const outdoorSelectList = document.getElementById('outdoorSelectList');
const btnOutdoorSelectInspect = document.getElementById('btnOutdoorSelectInspect');
const btnOutdoorSelectEdit = document.getElementById('btnOutdoorSelectEdit');
const btnOutdoorSelectClose = document.getElementById('btnOutdoorSelectClose');
const btnOutdoorSelectClose2 = document.getElementById('outdoorSelectCloseBtn');
const osId = document.getElementById('osId');
const osSerial = document.getElementById('osSerial');
const osType = document.getElementById('osType');
const osOp = document.getElementById('osOp');
const osCoord = document.getElementById('osCoord');
const osLoc = document.getElementById('osLoc');
const btnOutdoorAdd = document.getElementById('btnOutdoorAdd');
const btnOutdoorMove = document.getElementById('btnOutdoorMove');
const btnOutdoorEdit = document.getElementById('btnOutdoorEdit');
const outdoorActionHint = document.getElementById('outdoorActionHint');
const mapImg = document.getElementById('mapImg');

menuBtn.onclick = () => screen.classList.toggle('menu-open');
overlay.onclick = () => screen.classList.remove('menu-open');

if (mapImg) {
    mapImg.addEventListener('dragstart', (e) => e.preventDefault());
}

// ===== ?癒?염 ??由?=====
function getNaturalSize(){
    const fallback = { w: 1955, h: 985 };
    if (!mapImg) return fallback;
    if (mapImg.naturalWidth > 0 && mapImg.naturalHeight > 0)
        return { w: mapImg.naturalWidth, h: mapImg.naturalHeight };
    return fallback;
}

// ===== SVG viewBox ??녿┛??=====
function setOverlayViewBox(){
    if (!zoneSvg) return;
    const { w, h } = getNaturalSize();
    zoneSvg.setAttribute('viewBox', `0 0 ${w} ${h}`);
    zoneSvg.dataset.naturalW = String(w);
    zoneSvg.dataset.naturalH = String(h);
    zoneSvg.setAttribute('preserveAspectRatio', 'xMidYMid slice');
    applyPctPointsToAll();
}

// ===== points <-> % 癰궰??=====
function parsePoints(pointsStr){
    const s = (pointsStr || '').trim();
    if (!s) return [];
    return s.split(/\s+/).map(pair => {
        const [x, y] = pair.split(',').map(v => parseFloat(v));
        return { x, y };
    }).filter(p => Number.isFinite(p.x) && Number.isFinite(p.y));
}
function toPctPoints(pxPoints, natW, natH){
    return pxPoints.map(p => ({ xPct: (p.x / natW) * 100, yPct: (p.y / natH) * 100 }));
}
function pctString(pctPoints){
    return pctPoints.map(p => `${p.xPct.toFixed(3)},${p.yPct.toFixed(3)}`).join(' ');
}
function pctToPxString(pctStr, natW, natH){
    const pts = parsePoints(pctStr);
    return pts.map(p => {
        const x = (p.x * natW) / 100;
        const y = (p.y * natH) / 100;
        return `${x.toFixed(2)},${y.toFixed(2)}`;
    }).join(' ');
}
function ensurePctStored(poly){
    const { w, h } = getNaturalSize();
    if (!poly) return;
    if (poly.dataset.pointsPct && poly.dataset.pointsPct.trim()) return;
    const px = parsePoints(poly.getAttribute('points'));
    if (!px.length) return;
    const pct = toPctPoints(px, w, h);
    poly.dataset.pointsPct = pctString(pct);
}
function applyPctPointsToAll(){
    if (!zoneSvg) return;
    const { w, h } = getNaturalSize();
    zoneSvg.querySelectorAll('polygon.zone').forEach(poly => {
        ensurePctStored(poly);
        if (poly.dataset.pointsPct && poly.dataset.pointsPct.trim()) {
            const pxStr = pctToPxString(poly.dataset.pointsPct, w, h);
            if (pxStr) poly.setAttribute('points', pxStr);
        }
    });
}

// ===== ??꾨샍 =====
function showTooltip(e, name) {
    if (!tooltip) return;
    tooltip.textContent = name || '';
    tooltip.style.display = 'block';
    positionTooltip(e);
    requestAnimationFrame(() => tooltip.classList.add('show'));
}
function hideTooltip() {
    if (!tooltip) return;
    tooltip.classList.remove('show');
    setTimeout(() => { tooltip.style.display = 'none'; }, 140);
}
function positionTooltip(e) {
    if (!tooltip || tooltip.style.display === 'none') return;
    const r = scene.getBoundingClientRect();
    tooltip.style.left = (e.clientX - r.left + 14) + 'px';
    tooltip.style.top  = (e.clientY - r.top  + 18) + 'px';
}

// ===== ??밸씜(筌??醫뤾문) =====
function closePopup() {
    if (!zonePopup) return;
    zonePopup.style.display = 'none';
    zonePopup.innerHTML = '';
}
function openPopupNearEvent(e, zoneName, floorsCsv) {
    if (!zonePopup) return;
    const floors = (floorsCsv || '').split(',').map(s => s.trim()).filter(Boolean);
    zonePopup.innerHTML = `
        <div class="zp-title">
            <span>?猷?${zoneName || '?닌딅열'}</span>
            <button class="zp-close" type="button" aria-label="??る┛">??/button>
        </div>
        <div class="zp-floors">
            ${floors.map(f => `<button type="button" class="zp-floor" data-floor="${f}">${f}</button>`).join('')}
        </div>
    `;
    const r = scene.getBoundingClientRect();
    zonePopup.style.left = (e.clientX - r.left + 18) + 'px';
    zonePopup.style.top  = (e.clientY - r.top  + 18) + 'px';
    zonePopup.style.display = 'block';
    zonePopup.querySelector('.zp-close')?.addEventListener('click', closePopup);
    zonePopup.querySelectorAll('.zp-floor').forEach(btn => {
        btn.addEventListener('click', () => {
            const floor = btn.getAttribute('data-floor') || '';
            const b = (zoneName || '').trim();
            const f = (floor || '').trim();
            const url = `/maps/floor-v2?buildingName=${encodeURIComponent(b)}&floorName=${encodeURIComponent(f)}`;
            window.location.href = url;
        });
    });
}
function selectZone(el) {
    zoneSvg?.querySelectorAll('.zone.selected').forEach(p => p.classList.remove('selected'));
    el.classList.add('selected');
}
function bindZones() {
    if (!zoneSvg) return;
    zoneSvg.querySelectorAll('.zone').forEach(p => {
        p.addEventListener('mouseenter', (e) => {
            if (outdoorMode || window.__pickEnabled) return;
            showTooltip(e, p.dataset.name || '');
        });
        p.addEventListener('mousemove', (e) => {
            if (outdoorMode || window.__pickEnabled) return;
            positionTooltip(e);
        });
        p.addEventListener('mouseleave', () => {
            if (outdoorMode || window.__pickEnabled) return;
            hideTooltip();
        });
        p.addEventListener('click', (e) => {
            if (outdoorMode || window.__pickEnabled) return;
            e.stopPropagation();
            const name = (p.dataset.name || '').trim();
            const floors = p.dataset.floors || '';
            selectZone(p);
            infoTitle.textContent = `?猷?${name || '?닌딅열'}`;
            infoSub.innerHTML = '<strong>?醫뤾문???닌딅열</strong>';
            infoBody.textContent = floors ? `층 ${floors}` : '援ъ뿭???대┃?섏뿬 ?뺤씤?섏꽭??;
            openPopupNearEvent(e, name, floors);
        });
    });
    scene.addEventListener('click', () => closePopup());
    zonePopup?.addEventListener('click', (e) => e.stopPropagation());
}

// ===== ?關????곗넅????곕뱜 =====
function setOutdoorHint(message, type){
    if(!outdoorActionHint) return;
    outdoorActionHint.textContent = message || '';
    outdoorActionHint.classList.remove('error', 'success');
    if(type === 'error') outdoorActionHint.classList.add('error');
    if(type === 'success') outdoorActionHint.classList.add('success');
}
function setOutdoorToolMode(mode){
    outdoorAddMode = mode === 'add';
    outdoorMoveMode = mode === 'move';
    btnOutdoorAdd?.classList.toggle('is-active', outdoorAddMode);
    btnOutdoorMove?.classList.toggle('is-active', outdoorMoveMode);
    document.body.classList.toggle('move-armed', outdoorMoveMode);
}

// ===== ?關????곗넅???類ｋ궖 燁삳?諭?=====
function hideOutdoorInfo(clearSelection = true){
    if(outdoorSelect) outdoorSelect.classList.remove('show');
    if(clearSelection){
        currentOutdoorId = null;
        renderOutdoorMarkers();
    }
}
function fillOutdoorSelectInfo(h){
    if(!h) return;
    if(osId) osId.textContent = `ID ${h.hydrantId}`;
    if(osSerial) osSerial.textContent = h.serialNumber || '-';
    if(osType) osType.textContent = (h.hydrantType === 'Indoor' ? '?貫沅? : (h.hydrantType === 'Outdoor' ? '?關?? : (h.hydrantType || '-')));
    if(osOp) osOp.textContent = (h.operationType === 'Auto' ? '?癒?짗' : (h.operationType === 'Manual' ? '??롫짗' : (h.operationType || '-')));
    if(osCoord) osCoord.textContent = (h.x != null && h.y != null) ? `${Number(h.x).toFixed(2)}%, ${Number(h.y).toFixed(2)}%` : '-';
    if(osLoc) osLoc.textContent = h.locationDescription || '-';
}
function positionOutdoorSelect(markerEl){
    if(!outdoorSelect || !markerEl || !scene) return;
    const sceneRect = scene.getBoundingClientRect();
    const mkRect = markerEl.getBoundingClientRect();
    const cardW = outdoorSelect.offsetWidth || 300;
    const cardH = outdoorSelect.offsetHeight || 250;
    let left = mkRect.right - sceneRect.left + 12;
    let top = mkRect.top - sceneRect.top - 12;
    const maxLeft = sceneRect.width - cardW - 12;
    const maxTop = sceneRect.height - cardH - 12;
    if (left > maxLeft) left = Math.max(12, mkRect.left - sceneRect.left - cardW - 12);
    if (top > maxTop) top = maxTop;
    if (top < 12) top = 12;
    outdoorSelect.style.left = `${left}px`;
    outdoorSelect.style.top = `${top}px`;
    outdoorSelect.style.right = 'auto';
    outdoorSelect.style.bottom = 'auto';
}

// ===== ?關????곗넅??筌띾뜆鍮????쐭筌?=====
function renderOutdoorMarkers(){
    if(!outdoorMarkers) return;
    outdoorMarkers.innerHTML = '';
    outdoorByKey = new Map();
    if(!outdoorMode) return;
    const coverFrame = (() => {
        const sw = scene?.clientWidth || 0;
        const sh = scene?.clientHeight || 0;
        const nat = getNaturalSize();
        const iw = nat.w || 0;
        const ih = nat.h || 0;
        if (!sw || !sh || !iw || !ih) return null;
        const scale = Math.max(sw / iw, sh / ih);
        const w = iw * scale;
        const h = ih * scale;
        const ox = (sw - w) / 2;
        const oy = (sh - h) / 2;
        return { w, h, ox, oy };
    })();
    const iconUrl = '/images/outdoorfirehydrant.PNG';
    (outdoorHydrants || []).forEach(h => {
        if(h.x == null || h.y == null) return;
        const key = `${Number(h.x).toFixed(2)},${Number(h.y).toFixed(2)}`;
        if(!outdoorByKey.has(key)) outdoorByKey.set(key, []);
        outdoorByKey.get(key).push(h);
    });
    outdoorByKey.forEach((list, key) => {
        const first = list[0];
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'hmarker';
        if(currentOutdoorId && list.some(x => x.hydrantId === currentOutdoorId)){
            btn.classList.add('is-selected');
        }
        if (coverFrame){
            const px = coverFrame.ox + (coverFrame.w * (Number(first.x) / 100));
            const py = coverFrame.oy + (coverFrame.h * (Number(first.y) / 100));
            btn.style.left = `${px}px`;
            btn.style.top = `${py}px`;
        } else {
            btn.style.left = first.x + '%';
            btn.style.top = first.y + '%';
        }
        btn.dataset.key = key;
        btn.innerHTML = `<img class="icon" src="${iconUrl}" alt="">` + (list.length > 1 ? `<span class="badge">+${list.length}</span>` : '');
        btn.addEventListener('click', (e)=>{
            if (window.__mapDragLock || isPanning || panMoved) return;
            e.stopPropagation();
            if(outdoorAddMode){
                alert('????곗넅?袁⑹뱽 ???袁⑺뒄???곕떽???몃빍??');
                setOutdoorToolMode(null);
                setOutdoorHint('?곕떽? 疫꿸퀡??? ?온????륁뵠筌왖?癒?퐣 ??곸뒠??雅뚯눘苑??');
                return;
            }
            const items = outdoorByKey.get(key) || [];
            const selected = items.find(h => h.hydrantId === currentOutdoorId);
            const chosen = selected || items[0] || null;
            currentOutdoorId = chosen?.hydrantId ?? null;
            if(outdoorMoveMode){
                hideOutdoorInfo(false);
                renderOutdoorMarkers();
                if(currentOutdoorId){
                    setOutdoorHint('??猷?????袁⑺뒄??筌왖?袁⑸퓠???????뤾쉭??');
                } else {
                    setOutdoorHint('?믪눘? ?關????곗넅??筌띾뜆鍮긺몴??醫뤾문??뤾쉭??', 'error');
                }
                return;
            }
            if(outdoorSelectList){
                outdoorSelectList.innerHTML = '';
                items.forEach(h => {
                    const opt = document.createElement('option');
                    opt.value = String(h.hydrantId);
                    const label = h.serialNumber ? `${h.serialNumber} (ID ${h.hydrantId})` : `ID ${h.hydrantId}`;
                    opt.textContent = label;
                    outdoorSelectList.appendChild(opt);
                });
                outdoorSelectList.value = currentOutdoorId ? String(currentOutdoorId) : '';
                fillOutdoorSelectInfo(chosen);
            }
            if(outdoorSelect){
                outdoorSelect.classList.add('show');
                positionOutdoorSelect(btn);
            }
            renderOutdoorMarkers();
        });
        outdoorMarkers.appendChild(btn);
    });
}

// ===== SVG ?ル슦紐?癰궰??=====
function getSvgPoint(clientX, clientY){
    if (!zoneSvg) return { xPct: 0, yPct: 0 };
    const pt = zoneSvg.createSVGPoint();
    pt.x = clientX; pt.y = clientY;
    const ctm = zoneSvg.getScreenCTM();
    if(!ctm) return { xPct: 0, yPct: 0 };
    const loc = pt.matrixTransform(ctm.inverse());
    const { w, h } = getNaturalSize();
    const xPct = Math.max(0, Math.min(100, (loc.x / w) * 100));
    const yPct = Math.max(0, Math.min(100, (loc.y / h) * 100));
    return { xPct, yPct };
}

// ===== Zoom/Pan =====
let mapScale = 1, mapTx = 0, mapTy = 0;
let isPanning = false, panMoved = false;
let panStart = { x: 0, y: 0, tx: 0, ty: 0 };
if (typeof window.__mapDragLock === 'undefined') window.__mapDragLock = false;

function applyMapTransform(){
    if (!mapStage) return;
    mapStage.style.transform = `translate(${mapTx}px, ${mapTy}px) scale(${mapScale})`;
}
function clampMap(){
    if (!scene || !mapStage) return;
    const rect = scene.getBoundingClientRect();
    const w = rect.width, h = rect.height;
    const scaledW = w * mapScale, scaledH = h * mapScale;
    const minTx = Math.min(0, w - scaledW);
    const minTy = Math.min(0, h - scaledH);
    mapTx = Math.max(minTx, Math.min(0, mapTx));
    mapTy = Math.max(minTy, Math.min(0, mapTy));
}
function resetMapTransform(smooth){
    mapScale = 1; mapTx = 0; mapTy = 0;
    if (mapStage && smooth){
        mapStage.style.transition = 'transform 220ms ease';
        applyMapTransform();
        setTimeout(() => { if(mapStage) mapStage.style.transition = ''; }, 240);
    } else { applyMapTransform(); }
}
function zoomAt(cx, cy, newScale){
    const oldScale = mapScale;
    mapScale = Math.max(1, Math.min(3, newScale));
    const scaleRatio = mapScale / oldScale;
    mapTx = cx - (cx - mapTx) * scaleRatio;
    mapTy = cy - (cy - mapTy) * scaleRatio;
    clampMap();
    applyMapTransform();
}

// ===== ?關??筌뤴뫀諭??袁れ넎 =====
function setOutdoorMode(on){
    outdoorMode = !!on;
    setOutdoorToolMode(null);
    if (outdoorPanel) outdoorPanel.classList.toggle('show', outdoorMode);
    document.body.classList.toggle('outdoor-mode', outdoorMode);
    if (outdoorMode){
        screen.classList.remove('menu-open');
        document.querySelector('.top-left')?.classList.add('fade-out');
        closePopup();
    } else {
        document.querySelector('.top-left')?.classList.remove('fade-out');
        hideOutdoorInfo();
        resetMapTransform(true);
    }
    if (isAdmin) {
        setOutdoorHint('筌띾뜆鍮긺몴??醫뤾문??롢늺 ?癒?/?紐꾩춿/??猷??????됰뮸??덈뼄.');
    } else {
        setOutdoorHint('??곗뺘 ????癒?뮉 ?癒?筌?揶쎛?館鍮??덈뼄.', 'error');
    }
    renderOutdoorMarkers();
}
window.setOutdoorMode = setOutdoorMode;

if (outdoorToggle){
    const keep = sessionStorage.getItem('outdoorMode');
    if (keep === '1'){
        outdoorToggle.checked = true;
        setOutdoorMode(true);
    } else {
        outdoorToggle.checked = false;
        setOutdoorMode(false);
    }
    outdoorToggle.addEventListener('change', (e)=> {
        sessionStorage.setItem('outdoorMode', e.target.checked ? '1' : '0');
        setOutdoorMode(e.target.checked);
    });
}

// 踰꾪듉 ?대깽??btnOutdoorAdd?.addEventListener('click', ()=>{
    if(!isAdmin){ alert('?온?귐딆쁽筌??곕떽???????됰뮸??덈뼄.'); return; }
    setOutdoorToolMode('add');
    setOutdoorHint('筌왖?袁⑹벥 ?곕떽????袁⑺뒄???????뤾쉭??');
});
btnOutdoorMove?.addEventListener('click', ()=>{
    if(!isAdmin){ alert('?온?귐딆쁽筌???猷??????됰뮸??덈뼄.'); return; }
    if(outdoorMoveMode){
        setOutdoorToolMode(null);
        setOutdoorHint('??猷?筌뤴뫀諭띄몴??ル굝利??됰뮸??덈뼄.');
        return;
    }
    setOutdoorToolMode('move');
    hideOutdoorInfo(false);
    setOutdoorHint('??猷?筌뤴뫀諭?ON: 筌띾뜆鍮??醫뤾문 ???袁⑺뒄???????뤾쉭?? ?ル굝利????猷?甕곌쑵????쇰뻻 ????');
});
btnOutdoorEdit?.addEventListener('click', ()=>{
    if(!isAdmin){ alert('?온?귐딆쁽筌??紐꾩춿??????됰뮸??덈뼄.'); return; }
    if(!currentOutdoorId){ setOutdoorHint('?믪눘? 筌띾뜆鍮긺몴??醫뤾문??뤾쉭??', 'error'); return; }
    window.location.href = `/hydrants.html?edit=${currentOutdoorId}`;
});
btnOutdoorSelectEdit?.addEventListener('click', ()=>{
    if(!isAdmin){ alert('?온?귐딆쁽筌??紐꾩춿??????됰뮸??덈뼄.'); return; }
    if(!currentOutdoorId){ setOutdoorHint('?믪눘? 筌띾뜆鍮긺몴??醫뤾문??뤾쉭??', 'error'); return; }
    window.location.href = `/hydrants.html?edit=${currentOutdoorId}`;
});

outdoorSelectList?.addEventListener('change', (e)=>{
    const v = parseInt(e.target.value || '0', 10);
    currentOutdoorId = v > 0 ? v : null;
    if(currentOutdoorId){
        const h = (outdoorHydrants || []).find(x => x.hydrantId === currentOutdoorId);
        fillOutdoorSelectInfo(h);
        renderOutdoorMarkers();
    }
});
btnOutdoorSelectInspect?.addEventListener('click', ()=>{
    if(currentOutdoorId) openInspectModal(currentOutdoorId);
});
btnOutdoorSelectClose?.addEventListener('click', ()=> hideOutdoorInfo(false));
btnOutdoorSelectClose2?.addEventListener('click', ()=> hideOutdoorInfo(false));

// 留덉슦????확대/異뺤냼
scene.addEventListener('wheel', (e) => {
    if (!outdoorMode) return;
    e.preventDefault();
    const rect = scene.getBoundingClientRect();
    const cx = e.clientX - rect.left;
    const cy = e.clientY - rect.top;
    const delta = e.deltaY > 0 ? -0.1 : 0.1;
    zoomAt(cx, cy, mapScale + delta);
}, { passive: false });

// 筌띾뜆?????뺤삋域???ㅻ뻼
scene.addEventListener('mousedown', (e) => {
    if (!outdoorMode) return;
    if (e.button !== 0) return;
    if (e.target && e.target.closest && e.target.closest('.outdoor-panel, .outdoor-toggle, .info-panel, #pickPanel, #zonePopup')) return;
    isPanning = true;
    panMoved = false;
    window.__mapDragLock = true;
    document.body.style.userSelect = 'none';
    panStart = { x: e.clientX, y: e.clientY, tx: mapTx, ty: mapTy };
    e.preventDefault();
});
window.addEventListener('mousemove', (e) => {
    if (!isPanning) return;
    const dx = e.clientX - panStart.x;
    const dy = e.clientY - panStart.y;
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) panMoved = true;
    mapTx = panStart.tx + dx;
    mapTy = panStart.ty + dy;
    clampMap();
    applyMapTransform();
});
window.addEventListener('mouseup', () => {
    if (!isPanning) return;
    isPanning = false;
    window.__mapDragLock = false;
    document.body.style.userSelect = '';
});

// 筌왖??????(?곕떽?/??猷?筌뤴뫀諭?
scene.addEventListener('click', async (e)=>{
    if(!outdoorMode) return;
    if (window.__mapDragLock || panMoved) { panMoved = false; return; }
    if(outdoorAddMode){
        const p = getSvgPoint(e.clientX, e.clientY);
        alert(`??곗넅???곕떽? ?袁⑺뒄: X=${p.xPct.toFixed(2)}%, Y=${p.yPct.toFixed(2)}%\n??곗넅???온????륁뵠筌왖?癒?퐣 ?곕떽???雅뚯눘苑??`);
        setOutdoorToolMode(null);
        setOutdoorHint('?곕떽? 疫꿸퀡??? ??곗넅???온????륁뵠筌왖????곸뒠??雅뚯눘苑??');
        return;
    }
    if(outdoorMoveMode){
        if(!isAdmin){ setOutdoorToolMode(null); setOutdoorHint('?온?귐딆쁽 亦낅슦釉???袁⑹뒄??몃빍??', 'error'); return; }
        if(!currentOutdoorId){ setOutdoorHint('?믪눘? 筌띾뜆鍮긺몴??醫뤾문??뤾쉭??', 'error'); return; }
        const p = getSvgPoint(e.clientX, e.clientY);
        try {
            const res = await apiFetch(`/fire-api/hydrants/${currentOutdoorId}`);
            if (!res || !res.ok) throw new Error('??곗넅???類ｋ궖 嚥≪뮆諭???쎈솭');
            const json = await res.json();
            const data = json.data;
            const saveRes = await apiFetch('/fire-api/hydrants', {
                method: 'PUT',
                body: JSON.stringify({
                    hydrantId: currentOutdoorId,
                    hydrantType: data.hydrantType || 'Outdoor',
                    operationType: data.operationType || 'Manual',
                    buildingId: data.buildingId,
                    floorId: data.floorId,
                    x: p.xPct,
                    y: p.yPct,
                    locationDescription: data.locationDescription || ''
                })
            });
            if (!saveRes || !saveRes.ok) throw new Error('??猷???????쎈솭');
            const idx = outdoorHydrants.findIndex(h => h.hydrantId === currentOutdoorId);
            if(idx >= 0){ outdoorHydrants[idx].x = p.xPct; outdoorHydrants[idx].y = p.yPct; }
            renderOutdoorMarkers();
            setOutdoorHint('??猷??????袁⑥┷.', 'success');
        } catch(err) {
            alert('??猷???????쎈솭: ' + (err?.message || err));
            setOutdoorHint('??猷???????쎈솭: ' + (err?.message || err), 'error');
        }
    } else {
        hideOutdoorInfo(false);
    }
});

// ===== ?癒? 筌뤴뫀??=====
function openInspectModal(hydrantId) {
    currentInspectHydrantId = hydrantId;
    const h = outdoorHydrants.find(x => x.hydrantId === hydrantId);
    const infoEl = document.getElementById('inspectHydrantInfo');
    if (infoEl && h) {
        infoEl.textContent = `${h.serialNumber || ''} (ID: ${h.hydrantId})`;
    }
    document.getElementById('resultOk').checked = true;
    document.getElementById('faultReasonInput').value = '';
    document.getElementById('faultReasonBox').style.display = 'none';
    document.getElementById('inspectModal').classList.add('show');
}
function closeInspectModal() {
    document.getElementById('inspectModal').classList.remove('show');
    currentInspectHydrantId = null;
}
async function submitInspect() {
    if (!currentInspectHydrantId) return;
    const isFaulty = document.querySelector('input[name="inspectResult"]:checked')?.value === 'true';
    const faultReason = isFaulty ? document.getElementById('faultReasonInput').value : '';
    try {
        const params = new URLSearchParams({ isFaulty: String(isFaulty) });
        if (faultReason) params.append('faultReason', faultReason);
        const res = await apiFetch(`/fire-api/hydrants/${currentInspectHydrantId}/inspect?${params.toString()}`, { method: 'POST' });
        if (!res || !res.ok) throw new Error('?癒? ??????쎈솭');
        closeInspectModal();
        hideOutdoorInfo(false);
        setOutdoorHint('?癒??????貫由??됰뮸??덈뼄.', 'success');
    } catch(err) {
        alert('?癒? ??????쎈솭: ' + (err?.message || err));
    }
}

// 寃고븿 ?щ? 蹂寃?document.querySelectorAll('input[name="inspectResult"]').forEach(radio => {
    radio.addEventListener('change', () => {
        const isFaulty = document.querySelector('input[name="inspectResult"]:checked')?.value === 'true';
        document.getElementById('faultReasonBox').style.display = isFaulty ? '' : 'none';
    });
});

// ===== ?ル슦紐??怨뚮┛ ??Pick) =====
const Pick = (() => {
    let enabled = false;
    let pointsPct = [];
    const panel = document.getElementById('pickPanel');
    const out = document.getElementById('pickOut');
    const canvas = document.getElementById('pickCanvas');
    const ctx = canvas?.getContext('2d');

    function syncCanvas(){
        if (!canvas || !scene) return;
        const r = scene.getBoundingClientRect();
        canvas.width = Math.max(1, Math.round(r.width));
        canvas.height = Math.max(1, Math.round(r.height));
        redraw();
    }
    function clientToSvg(clientX, clientY){
        if (!zoneSvg) return { x: 0, y: 0 };
        const pt = zoneSvg.createSVGPoint();
        pt.x = clientX; pt.y = clientY;
        const ctm = zoneSvg.getScreenCTM();
        if (!ctm) return { x: 0, y: 0 };
        const loc = pt.matrixTransform(ctm.inverse());
        return { x: loc.x, y: loc.y };
    }
    function svgToPct(x, y){
        const { w, h } = getNaturalSize();
        const cx = Math.min(w, Math.max(0, x));
        const cy = Math.min(h, Math.max(0, y));
        return { xPct: (cx / w) * 100, yPct: (cy / h) * 100 };
    }
    function pctToSvg(p){
        const { w, h } = getNaturalSize();
        return { x: (p.xPct / 100) * w, y: (p.yPct / 100) * h };
    }
    function svgToCanvas(x, y){
        if (!zoneSvg || !canvas) return { x: 0, y: 0 };
        const pt = zoneSvg.createSVGPoint();
        pt.x = x; pt.y = y;
        const ctm = zoneSvg.getScreenCTM();
        if (!ctm) return { x: 0, y: 0 };
        const p2 = pt.matrixTransform(ctm);
        const r = canvas.getBoundingClientRect();
        return { x: p2.x - r.left, y: p2.y - r.top };
    }
    function redraw(){
        if (!ctx || !canvas) return;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        if (!pointsPct.length) return;
        ctx.lineWidth = 3;
        ctx.strokeStyle = 'rgba(255,0,0,0.95)';
        const firstSvg = pctToSvg(pointsPct[0]);
        const first = svgToCanvas(firstSvg.x, firstSvg.y);
        ctx.beginPath();
        ctx.moveTo(first.x, first.y);
        for (let i = 1; i < pointsPct.length; i++) {
            const sp = pctToSvg(pointsPct[i]);
            const cp = svgToCanvas(sp.x, sp.y);
            ctx.lineTo(cp.x, cp.y);
        }
        ctx.stroke();
        for (const p of pointsPct) {
            const sp = pctToSvg(p);
            const cp = svgToCanvas(sp.x, sp.y);
            ctx.beginPath();
            ctx.arc(cp.x, cp.y, 5, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255,0,0,0.95)';
            ctx.fill();
            ctx.strokeStyle = 'rgba(255,255,255,0.95)';
            ctx.lineWidth = 2;
            ctx.stroke();
        }
    }
    function formatOut(mousePct){
        if (!out) return;
        const { w, h } = getNaturalSize();
        const pctLine = pointsPct.map(p => `${p.xPct.toFixed(3)},${p.yPct.toFixed(3)}`).join(' ');
        const pxLine = pointsPct.map(p => {
            const x = (p.xPct/100)*w;
            const y = (p.yPct/100)*h;
            return `${Math.round(x)},${Math.round(y)}`;
        }).join(' ');
        const polyTag = `<polygon class="zone"\n  data-zone="C"\n  data-name="?닌딅열筌?\n  data-floors="1筌?2筌?\n  data-points-pct="${pctLine}" />`;
        const m = mousePct
            ? `\n?袁⑹삺 筌띾뜆???%): ${mousePct.xPct.toFixed(3)},${mousePct.yPct.toFixed(3)}  |  ?癒?궚(px): ${Math.round((mousePct.xPct/100)*w)},${Math.round((mousePct.yPct/100)*h)}`
            : '';
        out.value = `?癒?궚 ???筌왖: ${w} x ${h}\n??揶쏆뮇?? ${pointsPct.length}\n\n[% points]\n${pctLine}\n\n[?癒?궚(px) 筌〓㈇??\n${pxLine}\n\n${polyTag}${m}`;
    }
    function onClick(e){
        if (!enabled) return;
        if (panel && e.target && e.target.closest && e.target.closest('#pickPanel')) return;
        if (e.shiftKey){ undo(); return; }
        const sv = clientToSvg(e.clientX, e.clientY);
        const pct = svgToPct(sv.x, sv.y);
        pointsPct.push(pct);
        redraw();
        formatOut(pct);
    }
    function onDblClick(e){
        if (outdoorMode) return;
        if (!enabled) return;
        e.preventDefault();
        finish();
    }
    function onMove(e){
        if (!enabled) return;
        const sv = clientToSvg(e.clientX, e.clientY);
        const pct = svgToPct(sv.x, sv.y);
        formatOut(pct);
    }
    function start(){
        enabled = true;
        window.__pickEnabled = true;
        if (panel) panel.style.display = 'block';
        syncCanvas();
        formatOut();
    }
    function end(){
        enabled = false;
        window.__pickEnabled = false;
        if (panel) panel.style.display = 'none';
    }
    function clear(){ pointsPct = []; redraw(); formatOut(); }
    function undo(){ pointsPct.pop(); redraw(); formatOut(); }
    function finish(){
        if (!ctx || pointsPct.length < 3) { formatOut(); return; }
        ctx.save();
        ctx.globalAlpha = 0.18;
        ctx.fillStyle = 'rgba(255,0,0,1)';
        ctx.beginPath();
        const firstSvg = pctToSvg(pointsPct[0]);
        const first = svgToCanvas(firstSvg.x, firstSvg.y);
        ctx.moveTo(first.x, first.y);
        for (let i = 1; i < pointsPct.length; i++) {
            const sp = pctToSvg(pointsPct[i]);
            const cp = svgToCanvas(sp.x, sp.y);
            ctx.lineTo(cp.x, cp.y);
        }
        ctx.closePath();
        ctx.fill();
        ctx.restore();
        redraw();
        formatOut();
    }
    async function copy(){
        if (!out) return;
        try { await navigator.clipboard.writeText(out.value); } catch {}
    }
    if (zoneSvg){
        zoneSvg.addEventListener('click', onClick);
        zoneSvg.addEventListener('dblclick', onDblClick);
        zoneSvg.addEventListener('mousemove', onMove);
    }
    window.addEventListener('resize', () => { syncCanvas(); setOverlayViewBox(); });
    return { start, end, clear, undo, finish, copy };
})();
window.Pick = Pick;

// ===== ?λ뜃由??=====
window.addEventListener('load', () => {
    if (mapImg && !mapImg.complete) {
        mapImg.addEventListener('load', () => {
            setOverlayViewBox();
            bindZones();
            renderOutdoorMarkers();
        }, { once: true });
    } else {
        setOverlayViewBox();
        bindZones();
        renderOutdoorMarkers();
    }
    init();
});

