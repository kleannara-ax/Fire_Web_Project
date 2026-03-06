// ===== JWT & 怨듭슜 API =====
const API = (() => {
  const getToken = () => localStorage.getItem('fireweb_token');
  const getUser  = () => { try{ return JSON.parse(localStorage.getItem('fireweb_user')||'null'); }catch{ return null; } };
  const isAdmin  = () => { const u = getUser(); return u && u.role === 'ADMIN'; };

  async function req(url, opts = {}) {
    const token = getToken();
    const headers = { ...(opts.headers||{}) };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    if (!(opts.body instanceof FormData) && opts.body && typeof opts.body === 'object') {
      headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(opts.body);
    }
    const res = await fetch(url, { ...opts, headers });
    if (res.status === 401) { window.location.href = '/login.html'; return null; }
    return res;
  }
  return { req, getToken, getUser, isAdmin };
})();

// ===== ?곹깭 =====
let allItems = [];
let currentStatusType = null;
let currentExtinguisherId = null;
let optionBuildings = [];
let optionFloors = [];
const isAdmin = API.isAdmin();

// ===== 토스트=====
function showToast(msg, type='success') {
  const id = 'fwToastBox';
  let box = document.getElementById(id);
  if (!box) {
    box = document.createElement('div');
    box.id = id;
    box.className = `alert alert-${type} shadow`;
    box.style.cssText = 'margin:0; opacity:0; transition:opacity 500ms ease;';
    document.getElementById('fwToast').appendChild(box);
  }
  box.className = `alert alert-${type} shadow`;
  box.textContent = msg;
  document.getElementById('fwToast').style.cssText = 'position:fixed;top:16px;right:16px;z-index:2000;';
  requestAnimationFrame(() => { box.style.opacity = '1'; });
  setTimeout(() => { box.style.opacity = '0'; }, 2200);
  setTimeout(() => { const w = document.getElementById('fwToast'); if(w) w.innerHTML=''; }, 3200);
}

// ===== 내비게이션 계정 영역 렌더링(_Layout.cshtml) =====
function renderNav() {
  const user = API.getUser();
  const area = document.getElementById('navAccountArea');
  if (!area) return;
  if (user) {
    area.innerHTML = `
      <li class="nav-item dropdown">
        <a class="nav-link dropdown-toggle fw-semibold account-link" href="#" role="button" data-bs-toggle="dropdown">
          ${user.displayName || user.username || '사용자'}
        </a>
        <ul class="dropdown-menu dropdown-menu-end">
          <li><a class="dropdown-item" href="/account/index.html">내 정보</a></li>
          ${isAdmin ? '<li><a class="dropdown-item" href="/account/users.html">계정관리</a></li>' : ''}
          <li><hr class="dropdown-divider" /></li>
          <li class="px-3 pb-2"><button type="button" class="btn btn-sm btn-danger w-100" onclick="logout()">로그아웃</button></li>
        </ul>
      </li>`;
  } else {
    area.innerHTML = '<li class="nav-item"><a class="btn btn-sm btn-outline-light" href="/login.html">로그인</a></li>';
  }
}

function logout() {
  localStorage.removeItem('fireweb_token');
  localStorage.removeItem('fireweb_user');
  window.location.href = '/login.html';
}

// ===== 건물/층?듭뀡 로드 =====
async function loadBuildingOptions() {
  let buildings = [];
  let floors = [];
  try {
    const br = await API.req('/fire-api/qr/buildings');
    const bj = br && br.ok ? await br.json().catch(()=>null) : null;
    buildings = Array.isArray(bj?.data) ? bj.data : [];
    const fr = await API.req('/fire-api/qr/floors');
    const fj = fr && fr.ok ? await fr.json().catch(()=>null) : null;
    floors = Array.isArray(fj?.data) ? fj.data : [];
  } catch {}

  // Fallback: QR API 실패 시 현재 목록 데이터에서 건물/층 후보 구성
  if (!buildings.length || !floors.length) {
    const bMap = new Map();
    const fMap = new Map();
    (allItems || []).forEach(it => {
      if (it?.buildingId != null && !bMap.has(String(it.buildingId))) {
        bMap.set(String(it.buildingId), { buildingId: it.buildingId, buildingName: it.buildingName || String(it.buildingId) });
      }
      if (it?.floorId != null && !fMap.has(String(it.floorId))) {
        fMap.set(String(it.floorId), { floorId: it.floorId, floorName: it.floorName || String(it.floorId) });
      }
    });
    if (!buildings.length) buildings = Array.from(bMap.values());
    if (!floors.length) floors = Array.from(fMap.values());
  }

  optionBuildings = buildings;
  optionFloors = floors;

  const bSel = document.getElementById('filterBuildingId');
  const fSel = document.getElementById('filterFloorId');
  bSel.innerHTML = '<option value="">-- 건물 전체 --</option>' +
    buildings.map(x => `<option value="${x.buildingId}">${x.buildingName}</option>`).join('');
  fSel.innerHTML = '<option value="">-- 층 전체 --</option>' +
    floors.map(x => `<option value="${x.floorId}">${x.floorName}</option>`).join('');

  const ebSel = document.getElementById('extBuildingSel');
  const efSel = document.getElementById('extFloorSel');
  if (ebSel) {
    ebSel.innerHTML = '<option value="">-- 건물 선택 --</option>' +
      buildings.map(x => `<option value="${x.buildingId}">${x.buildingName}</option>`).join('');
  }
  if (efSel) {
    efSel.innerHTML = '<option value="">-- 층 선택 --</option>' +
      floors.map(x => `<option value="${x.floorId}">${x.floorName}</option>`).join('');
  }

}

// ===== 소화기목록 로드 =====
async function loadExtinguishers(q='', buildingId='', floorId='') {
  const params = new URLSearchParams({ size: 200, page: 0 });
  if (q) params.set('q', q);
  if (buildingId) params.set('buildingId', buildingId);
  if (floorId) params.set('floorId', floorId);
  const res = await API.req(`/fire-api/extinguishers?${params}`);
  if (!res) return;
  const json = await res.json();
  if (json.ok && json.data) {
    allItems = json.data.content || [];
  } else {
    allItems = [];
  }
  await loadBuildingOptions();
  renderTable(allItems);
  updateBucketCounts();
}

// ===== 踰꾪궥 移댁슫??怨꾩궛 (?좎쭨 湲곕컲) =====
function calcBuckets(items) {
  const today = new Date(); today.setHours(0,0,0,0);
  let inspectCount = 0, plannedCount = 0, urgentCount = 0;
  items.forEach(item => {
    const lastDate = item.lastInspectionDate ? new Date(item.lastInspectionDate) : null;
    const dueDate  = item.replacementDueDate ? new Date(item.replacementDueDate) : null;
    const isFaulty = item.lastIsFaulty === true;

    // 점검 필요: 최종 점검 후 1개월 이상 지난 경우
    const needInspect = !lastDate || (new Date(lastDate.getTime() + 30*24*3600*1000) <= today);
    if (needInspect) inspectCount++;

    // 교체 필요: 비정상이거나 교체예정일이 30일 이내/지남
    const replaceNeed = isFaulty || (dueDate && (dueDate < today || dueDate <= new Date(today.getTime() + 30*24*3600*1000)));
    if (replaceNeed) urgentCount++;

    // 교체예정: 6개월 이내이고 교체필요 아닌 경우
    const replacePlanned = dueDate && dueDate >= today && dueDate <= new Date(today.getTime() + 180*24*3600*1000) && !replaceNeed;
    if (replacePlanned) plannedCount++;
  });
  return { inspectCount, plannedCount, urgentCount };
}

function updateBucketCounts() {
  const { inspectCount, plannedCount, urgentCount } = calcBuckets(allItems);
  document.getElementById('totalCount').textContent = allItems.length;
  document.getElementById('countInspect').textContent = `(${inspectCount})`;
  document.getElementById('countPlanned').textContent = `(${plannedCount})`;
  document.getElementById('countUrgent').textContent = `(${urgentCount})`;
}

// ===== 메인 테이블 렌더링(_ExtListTable.cshtml 湲곕컲) =====
function renderTable(items) {
  const wrap = document.getElementById('mainTableWrap');
  if (!items.length) {
    wrap.innerHTML = '<div class="text-center text-muted py-4">조회 결과가 없습니다.</div>';
    return;
  }
  let rows = '';
  items.forEach((item) => {
    const lastInspDate = item.lastInspectionDate ? item.lastInspectionDate.slice(0,10) : '';
    const lastInspector = item.lastInspectorName || '';
    const installDate = item.installDate ? item.installDate.slice(0,10) : '-';
    const replaceDue = item.replacementDueDate ? item.replacementDueDate.slice(0,10) : '-';
    rows += `
      <tr class="clickable-row js-detail-row" data-id="${item.extinguisherId}">
        <td style="text-align:center;">${item.serialNumber||'-'}</td>
        <td class="text-truncate" title="${item.buildingName||''}">${item.buildingName||'-'}</td>
        <td class="text-truncate" title="${item.floorName||''}">${item.floorName||'-'}</td>
        <td class="text-truncate" title="${item.extinguisherType||''}">${item.extinguisherType||'-'}</td>
        <td>${installDate}</td>
        <td>${replaceDue}</td>
        <td>${lastInspDate || '-'}</td>
        <td class="text-truncate" title="${lastInspector}">${lastInspector || '-'}</td>
        <td>
          ${item.lastInspectionDate==null?'<span class="text-muted">-</span>':(item.lastIsFaulty===true?'<span class="fw-status fw-bad">비정상</span>':'<span class="fw-status fw-ok">정상</span>')}
        </td>
        <td class="text-truncate" title="${item.lastIsFaulty===true?(item.lastFaultReason||'-'):'-'}">
          ${item.lastIsFaulty===true?(item.lastFaultReason||'-'):'-'}
        </td>
        <td style="text-align:center;">${item.quantity||1}</td>
        <td class="text-truncate" title="${item.note||''}">${item.note||'-'}</td>
        <td class="text-center">
          <div class="ext-actions">
            ${isAdmin ? `<button type="button" class="btn btn-sm btn-fw-edit js-ext-edit" data-id="${item.extinguisherId}">편집</button>` : ''}
            <button type="button" class="btn btn-sm btn-fw-inspect js-inspect" data-id="${item.extinguisherId}">점검</button>
          </div>
        </td>
      </tr>`;
  });
  wrap.innerHTML = `
    <div class="fw-table-wrap"><div class="table-responsive">
      <table class="table table-hover mb-0" style="table-layout:fixed;">
        <thead class="table-dark">
          <tr>
            <th style="width:90px;text-align:center;">소화기 ID</th>
            <th style="width:140px;">건물</th>
            <th style="width:110px;">층</th>
            <th style="width:150px;">종류</th>
            <th style="width:110px;">제조일</th>
            <th style="width:120px;">교체예정일</th>
            <th style="width:110px;">최종 점검일</th>
            <th style="width:110px;">점검자</th>
            <th style="width:90px;">고장유무</th>
            <th style="width:170px;">고장 사유</th>
            <th style="width:70px;text-align:center;">수량</th>
            <th style="width:200px;">비고</th>
            <th style="width:150px;text-align:center;">관리</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div></div>`;
}

// ===== 상태 목록(statusPanel) 렌더링 =====
function renderStatusPanel(type) {
  const today = new Date(); today.setHours(0,0,0,0);
  let filtered = [];
  if (type === 'inspect') {
    filtered = allItems.filter(item => {
      const lastDate = item.lastInspectionDate ? new Date(item.lastInspectionDate) : null;
      return !lastDate || (new Date(lastDate.getTime() + 30*24*3600*1000) <= today);
    });
  } else if (type === 'planned') {
    filtered = allItems.filter(item => {
      const dueDate = item.replacementDueDate ? new Date(item.replacementDueDate) : null;
      const isFaulty = item.lastIsFaulty === true;
      const replaceNeed = isFaulty || (dueDate && (dueDate < today || dueDate <= new Date(today.getTime() + 30*24*3600*1000)));
      return dueDate && dueDate >= today && dueDate <= new Date(today.getTime() + 180*24*3600*1000) && !replaceNeed;
    });
  } else if (type === 'urgent') {
    filtered = allItems.filter(item => {
      const dueDate = item.replacementDueDate ? new Date(item.replacementDueDate) : null;
      const isFaulty = item.lastIsFaulty === true;
      return isFaulty || (dueDate && (dueDate < today || dueDate <= new Date(today.getTime() + 30*24*3600*1000)));
    });
  }
  const body = document.getElementById('statusPanelBody');
  if (!filtered.length) {
    body.innerHTML = '<div class="text-center text-muted py-3">해당 항목이 없습니다.</div>';
    return;
  }
  let rows = filtered.map(item => `
    <tr class="clickable-row js-detail-row" data-id="${item.extinguisherId}">
      <td style="text-align:center;">${item.serialNumber||'-'}</td>
      <td class="text-truncate" title="${item.buildingName||''}">${item.buildingName||'-'}</td>
      <td class="text-truncate" title="${item.floorName||''}">${item.floorName||'-'}</td>
      <td class="text-truncate" title="${item.extinguisherType||''}">${item.extinguisherType||'-'}</td>
      <td>${item.installDate ? item.installDate.slice(0,10) : '-'}</td>
      <td>${item.replacementDueDate ? item.replacementDueDate.slice(0,10) : '-'}</td>
      <td>${item.lastInspectionDate ? item.lastInspectionDate.slice(0,10) : '-'}</td>
      <td class="text-truncate" title="${item.lastInspectorName||''}">${item.lastInspectorName||'-'}</td>
      <td>${item.lastInspectionDate
        ? (item.lastIsFaulty===true?'<span class="fw-status fw-bad">비정상</span>':'<span class="fw-status fw-ok">정상</span>')
        : '<span class="text-muted">-</span>'}</td>
      <td class="text-truncate" title="${item.lastIsFaulty===true?(item.lastFaultReason||'-'):'-'}">${item.lastIsFaulty===true?(item.lastFaultReason||'-'):'-'}</td>
      <td style="text-align:center;">${item.quantity||1}</td>
      <td class="text-truncate" title="${item.note||''}">${item.note||'-'}</td>
      <td class="text-center">
        <div class="ext-actions">
          ${isAdmin ? `<button type="button" class="btn btn-sm btn-fw-edit js-ext-edit" data-id="${item.extinguisherId}">편집</button>` : ''}
          <button type="button" class="btn btn-sm btn-fw-inspect js-inspect" data-id="${item.extinguisherId}">점검</button>
        </div>
      </td>
    </tr>`).join('');
  body.innerHTML = `
    <div class="fw-table-wrap"><div class="table-responsive">
      <table class="table table-hover mb-0" style="table-layout:fixed;">
        <thead class="table-dark"><tr>
          <th style="width:90px;text-align:center;">소화기 ID</th>
          <th style="width:140px;">건물</th>
          <th style="width:110px;">층</th>
          <th style="width:150px;">종류</th>
          <th style="width:110px;">제조일</th>
          <th style="width:120px;">교체예정일</th>
          <th style="width:110px;">최종 점검일</th>
          <th style="width:110px;">점검자</th>
          <th style="width:90px;">고장유무</th>
          <th style="width:170px;">고장 사유</th>
          <th style="width:70px;text-align:center;">수량</th>
          <th style="width:200px;">비고</th>
          <th style="width:150px;text-align:center;">관리</th>
        </tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div></div>`;
}

function openStatus(type) {
  const panelEl = document.getElementById('statusPanel');
  if (!panelEl) return;
  const collapse = bootstrap.Collapse.getOrCreateInstance(panelEl);
  const titleMap = { inspect:'점검필요 목록', planned:'교체예정 목록', urgent:'교체필요 목록' };
  if (panelEl.classList.contains('show') && currentStatusType === type) {
    collapse.hide(); currentStatusType = null; return;
  }
  currentStatusType = type;
  const statusTitleEl = document.getElementById('statusPanelTitle');
  if (statusTitleEl) statusTitleEl.textContent = titleMap[type] || '상태 목록';
  renderStatusPanel(type);
  collapse.show();
}

// ===== 상세 모달 (_detailsModal.cshtml 湲곕컲) =====
function resolveDetailPlanImagePathExt(buildingName, floorName){
  const b = String(buildingName || '').replace(/\s+/g,'').toLowerCase().replace(/[.,_\-]/g,'');
  const fRaw = String(floorName || '').replace(/\s+/g,'').toLowerCase();
  const f = (fRaw === 'b1' || fRaw.includes('지하') || fRaw.includes('b1')) ? 'b1'
    : (fRaw.includes('1') ? '1f'
    : (fRaw.includes('2') ? '2f'
    : (fRaw.includes('3') ? '3f' : fRaw)));
  if (b.includes('복지관') || b.includes('bokji')) {
    if (f === 'b1') return '/images/bokji_B1.png';
    if (f === '1f') return '/images/bokji_1F.png';
    if (f === '2f') return '/images/bokji_2F.png';
    if (f === '3f') return '/images/bokji_3F.png';
  }
  if ((b.includes('관리동') || b.includes('gwanri')) && f === '1f') return '/images/gwanri_1F.png';
  if (b.includes('제지12호기') || b.includes('jeji12')) {
    if (f === '1f') return '/images/jeji1,2_1F.PNG';
    if (f === '2f') return '/images/jeji1,2_2F.PNG';
  }
  if (b.includes('제지3호기') || b.includes('jeji3')) {
    if (f === '1f') return '/images/jeji3_1F.PNG';
    if (f === '2f') return '/images/jeji3_2F.PNG';
  }
  if (b.includes('패드동') || b.includes('pad')) {
    if (f === '1f') return '/images/pad_1F.PNG';
    if (f === '2f') return '/images/pad_2F.PNG';
  }
  if (b.includes('심면펄퍼') || b.includes('palpa') || b.includes('pulper')) {
    if (f === '1f') return '/images/palpa_1F.PNG';
    if (f === '2f') return '/images/palpa_2F.PNG';
  }
  if (b.includes('화장지36호기') || b.includes('tissue13') || b.includes('tissue36')) {
    if (f === '1f') return '/images/tissue1,3_1F.PNG';
    if (f === '2f') return '/images/tissue1,3_2F.PNG';
  }
  if (b.includes('화장지45호기') || b.includes('tissue45')) {
    if (f === 'b1') return '/images/tissue4,5_B1.PNG';
    if (f === '1f') return '/images/tissue4,5_1F.PNG';
    if (f === '2f') return '/images/tissue4,5_2F.PNG';
    if (f === '3f') return '/images/tissue4,5_3F.PNG';
  }
  if ((b.includes('기저귀동') || b.includes('diaper')) && f === '1f') return '/images/diaper_1F.png';
  if (b.includes('옥외') || b.includes('outdoor')) return '/images/drone_photo.JPG';
  return '';
}

async function fetchDetailPlanImagePathExt(d){
  const b = String(d?.buildingId ?? '').trim();
  const f = String(d?.floorId ?? '').trim();
  if (b && f) {
    try {
      const qs = new URLSearchParams({ buildingId: b, floorId: f });
      const r = await API.req(`/fire-api/maps/floor-data?${qs.toString()}`);
      if (r && r.ok) {
        const j = await r.json().catch(() => null);
        const p = j?.data?.planImagePath || '';
        if (p) return p;
      }
    } catch {}
  }
  return resolveDetailPlanImagePathExt(d?.buildingName, d?.floorName);
}

function renderDetailPlanSingleMarker(wrapId, imgId, layerId, markerIcon, x, y){
  const wrap = document.getElementById(wrapId);
  const img = document.getElementById(imgId);
  const layer = document.getElementById(layerId);
  if (!wrap || !img || !layer) return;
  const nx = Number(x), ny = Number(y);
  const hasCoord = Number.isFinite(nx) && Number.isFinite(ny);

  const draw = () => {
    layer.innerHTML = '';
    const rect = wrap.getBoundingClientRect();
    const cw = rect.width || 0, ch = rect.height || 0;
    const iw = img.naturalWidth || 0, ih = img.naturalHeight || 0;
    if (!cw || !ch || !iw || !ih) return;
    const scale = Math.min(cw / iw, ch / ih);
    const w = iw * scale, h = ih * scale;
    const ox = (cw - w) / 2, oy = (ch - h) / 2;
    img.style.cssText = `position:absolute;left:${ox}px;top:${oy}px;width:${w}px;height:${h}px;transform:none;object-fit:contain;display:block;`;
    if (!hasCoord) return;
    const markerSize = Math.max(10, Math.min(34, Math.round(w * 0.032)));
    const left = ox + w * (Math.max(0, Math.min(100, nx)) / 100);
    const top = oy + h * (Math.max(0, Math.min(100, ny)) / 100);
    const m = document.createElement('div');
    m.style.cssText = `position:absolute;left:${left}px;top:${top}px;width:${markerSize}px;height:${markerSize}px;transform:translate(-50%,-50%);pointer-events:none;filter:drop-shadow(0 0 6px rgba(0,0,0,.65));`;
    m.innerHTML = `<img src="${markerIcon}" alt="" style="width:100%;height:100%;object-fit:contain;display:block;">`;
    layer.appendChild(m);
  };
  if (img.complete) draw();
  img.onload = draw;
  setTimeout(draw, 0);
}

async function openDetails(id) {
  const res = await API.req(`/fire-api/extinguishers/${id}`);
  if (!res) return;
  const json = await res.json();
  if (!json.ok || !json.data) { alert('상세 정보를 불러오지 못했습니다.'); return; }
  const d = json.data;
  const planImagePath = await fetchDetailPlanImagePathExt(d);
  const qrId = (d.serialNumber || '').trim();
  const qrUrl = qrId ? `/fire-api/qr/image?type=ext&id=${encodeURIComponent(qrId)}` : '';

  // 점검 이력
  let inspRows = '';
  if (d.inspections && d.inspections.length) {
    inspRows = d.inspections.slice(0,12).map(r => `
      <tr>
        <td>${r.inspectionDate ? r.inspectionDate.slice(0,10) : '-'}</td>
        <td>${r.inspectorName || '-'}</td>
      </tr>`).join('');
  } else {
    inspRows = '<tr><td colspan="2" class="text-muted text-center">점검 이력이 없습니다.</td></tr>';
  }

  const lastStatus = !d.lastInspectionDate
    ? '<span class="text-muted">-</span>'
    : (d.lastIsFaulty
       ? `<span class="fw-status fw-bad">비정상</span> <span class="text-muted">${d.lastFaultReason||'사유 없음'}</span>`
       : '<span class="fw-status fw-ok">정상</span>');

  document.getElementById('detailsModalBody').innerHTML = `
    <div class="container-fluid">
      <div class="row g-4">
        <div class="col-md-6">
          <div class="card border-0 shadow-sm h-100">
            <div class="card-body">
              <h6 class="card-title text-primary mb-3">기본 정보</h6>
              <div class="info-row mb-3">
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">건물</span>
                  <strong>${d.buildingName||'-'}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">층</span>
                  <strong>${d.floorName||'-'}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">종류</span>
                  <strong>${d.extinguisherType||'-'}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">수량</span>
                  <strong>${d.quantity||1} 개</strong>
                </div>
              </div>
              <hr class="my-3">
              <h6 class="card-title text-success mb-3">설치 정보</h6>
              <div class="info-row mb-3">
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">제조일</span>
                  <strong>${d.installDate ? d.installDate.slice(0,10) : '-'}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">교체예정일</span>
                  <strong>${d.replacementDueDate ? d.replacementDueDate.slice(0,10) : '-'}</strong>
                </div>
              </div>
              <hr class="my-3">
              <h6 class="card-title text-warning mb-3">점검 정보</h6>
              <div class="mb-2">
                <small class="text-muted">최종 점검</small>
                <div>
                  ${d.lastInspectionDate
                    ? `<strong>${d.lastInspectionDate.slice(0,10)}</strong>${d.lastInspectorName?` <span class="text-muted">/ ${d.lastInspectorName}</span>`:''}`
                    : '<span class="text-muted">-</span>'}
                </div>
              </div>
              <div class="mb-3">
                <small class="text-muted">점검 결과</small>
                <div>${lastStatus}</div>
              </div>
              <table class="table table-sm table-bordered mb-0">
                <thead class="table-light">
                  <tr><th style="width:50%;">점검일</th><th style="width:50%;">점검자</th></tr>
                </thead>
                <tbody>${inspRows}</tbody>
              </table>
              <small class="text-muted d-block mt-1">최근 12건까지 표시됩니다.</small>
              ${d.note ? `<hr class="my-3"><h6 class="card-title text-info mb-3">비고</h6><div class="alert alert-info mb-0"><small>${d.note}</small></div>` : ''}
            </div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="card border-0 shadow-sm h-100">
            <div class="card-body d-flex flex-column" style="min-height:400px;gap:14px;">
              <div>
                <div class="fw-semibold mb-2">이미지</div>
                ${d.imagePath
                  ? `<div class="text-center"><img src="${d.imagePath}" alt="소화기 이미지" class="img-fluid rounded shadow js-zoomable" style="max-width:100%;max-height:260px;object-fit:contain;" /><p class="text-muted mt-2 mb-0"><small>소화기 사진</small></p></div>`
                  : '<div class="text-center text-muted"><div style="font-size:3rem;opacity:.3;">🧯</div><p class="mt-2 mb-0">등록된 이미지가 없습니다</p></div>'}
              </div>
              <div>
                <div class="fw-semibold mb-2">도면</div>
                ${planImagePath
                  ? `<div id="extDetailPlanWrap" class="position-relative border rounded bg-light" style="height:260px;overflow:hidden;">
                       <img id="extDetailPlanImg" src="${planImagePath}" alt="도면" class="js-zoomable" data-marker-x="${d.x ?? ''}" data-marker-y="${d.y ?? ''}" data-marker-icon="/images/Extinguisher.PNG" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);max-width:100%;max-height:100%;width:auto;height:auto;display:block;" />
                       <div id="extDetailPlanMarkerLayer" style="position:absolute;inset:0;pointer-events:none;z-index:3;"></div>
                     </div>`
                  : '<div class="text-muted small border rounded p-3 bg-light">도면 정보가 없습니다.</div>'}
              </div>
              <div>
                <div class="fw-semibold mb-2">QR코드</div>
                ${qrUrl
                  ? `<div class="text-center"><img src="${qrUrl}" alt="소화기 QR" class="img-fluid rounded shadow js-zoomable" style="max-width:220px;max-height:220px;object-fit:contain;" /><p class="text-muted mt-2 mb-0"><small>${qrId}</small></p></div>`
                  : '<div class="text-muted small border rounded p-3 bg-light">QR 정보가 없습니다.</div>'}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <style>.info-row{font-size:.95rem;}.js-zoomable{cursor:zoom-in;}</style>`;

  bootstrap.Modal.getOrCreateInstance(document.getElementById('detailsModal')).show();
  if (planImagePath) {
    renderDetailPlanSingleMarker('extDetailPlanWrap', 'extDetailPlanImg', 'extDetailPlanMarkerLayer', '/images/Extinguisher.PNG', d.x, d.y);
  }
}

// ===== _InspectModalScripts.cshtml 기반 =====
(() => {
  if (window.__inspectModalInit) return;
  window.__inspectModalInit = true;

  let currentExtinguisherId = null;

  const updateFaultReasonVisibility = () => {
    const isBad = document.getElementById('inspectBad')?.checked === true;
    const wrap = document.getElementById('faultReasonWrap');
    if (wrap) wrap.style.display = isBad ? '' : 'none';
    if (!isBad) {
      const t = document.getElementById('faultReasonInput');
      if (t) t.value = '';
    }
  };

  const setAiHint = (text, kind) => {
    const el = document.getElementById('aiAnalyzeHint');
    if (!el) return;
    if (!text) {
      el.style.display = 'none';
      el.textContent = '';
      el.className = 'small mt-2';
      return;
    }
    el.style.display = '';
    el.textContent = text;
    el.className = 'small mt-2 ' + (kind === 'ok' ? 'text-success'
      : kind === 'warn' ? 'text-warning'
      : kind === 'bad' ? 'text-danger'
      : 'text-muted');
  };

  // expose for pages
  window.openInspectModal = (e, id) => {
    if (e) e.stopPropagation?.();
    currentExtinguisherId = id;

    const input = document.getElementById('inspectPhotoInput');
    if (input) input.value = '';

    // AI 안내 초기화
    setAiHint('', '');

    // 점검 결과 초기화
    const ok = document.getElementById('inspectOk');
    if (ok) ok.checked = true;
    updateFaultReasonVisibility();

    const modalEl = document.getElementById('inspectModal');
    if (modalEl) bootstrap.Modal.getOrCreateInstance(modalEl).show();
  };

  document.getElementById('inspectOk')?.addEventListener('change', updateFaultReasonVisibility);
  document.getElementById('inspectBad')?.addEventListener('change', updateFaultReasonVisibility);

  // ✅ AI 분석 버튼 클릭 시에만 호출
  document.getElementById('aiAnalyzeBtn')?.addEventListener('click', async () => {
    const file = document.getElementById('inspectPhotoInput')?.files?.[0];
    if (!file) { alert('AI 분석을 하려면 먼저 사진을 선택하세요.'); return; }

    const btn = document.getElementById('aiAnalyzeBtn');
    if (btn) btn.disabled = true;

    setAiHint('AI가 사진을 분석 중입니다...', '');
    try {
      const fd = new FormData();
      fd.append('inspectionPhoto', file);
      const r = await API.req('/fire-api/extinguishers/analyze', { method: 'POST', body: fd });
      if (!r || !r.ok) throw new Error('AI 분석 실패');
      const res = await r.json();

      if (res.isFaulty === true) {
        document.getElementById('inspectBad').checked = true;
        updateFaultReasonVisibility();
        const reason = (res.reason || '').trim();
        if (reason) document.getElementById('faultReasonInput').value = reason;
        const conf = (typeof res.confidence === 'number') ? ` · 신뢰도 ${(res.confidence * 100).toFixed(0)}%` : '';
        setAiHint(`AI 판정: 비정상${reason ? ` (${reason})` : ''}${conf}`, 'warn');
      } else {
        document.getElementById('inspectOk').checked = true;
        updateFaultReasonVisibility();
        const conf = (typeof res.confidence === 'number') ? ` · 신뢰도 ${(res.confidence * 100).toFixed(0)}%` : '';
        setAiHint(`AI 판정: 정상${conf}`, 'ok');
      }
    } catch (err) {
      setAiHint('AI 분석을 사용할 수 없습니다. (수동으로 선택/입력하세요)', 'bad');
      console.error(err);
    } finally {
      if (btn) btn.disabled = false;
    }
  });

  document.getElementById('confirmInspectBtn')?.addEventListener('click', async () => {
    if (!currentExtinguisherId) return;
    const today = new Date().toISOString().slice(0,10);
    const picked = (allItems || []).find(x => Number(x.extinguisherId) === Number(currentExtinguisherId));
    const last = picked?.lastInspectionDate ? String(picked.lastInspectionDate).slice(0,10) : '';
    if (last === today) {
      alert('오늘 이미 점검 완료된 소화기입니다.');
      return;
    }

    const isFaulty = document.getElementById('inspectBad')?.checked === true;
    const faultReason = (document.getElementById('faultReasonInput')?.value || '').trim();
    if (isFaulty && faultReason.length === 0) {
      alert('비정상인 경우 고장 사유를 입력해야 합니다.');
      document.getElementById('faultReasonInput')?.focus();
      return;
    }

    const btn = document.getElementById('confirmInspectBtn');
    if (btn) btn.disabled = true;

    try {
      const body = { extinguisherId: currentExtinguisherId, isFaulty, faultReason };
      const r = await API.req('/fire-api/extinguishers/inspect', { method: 'POST', body });

      if (!r || !r.ok) {
        const t = await r?.text().catch(() => '');
        alert(t || `점검 처리 실패(${r?.status})`);
        return;
      }

      const data = await r.json().catch(() => null);
      if (data && data.ok === false) {
        alert(data?.message || '점검 처리 실패');
        return;
      }

      const modalEl = document.getElementById('inspectModal');
      if (modalEl) bootstrap.Modal.getOrCreateInstance(modalEl).hide();

      const toastEl = document.getElementById('inspectToast');
      if (toastEl) bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 2000 }).show();

      // ✅ pages can react
      document.dispatchEvent(new CustomEvent('inspection:completed', {
        detail: { extinguisherId: currentExtinguisherId }
      }));

    } catch (err) {
      alert('점검 처리 실패: ' + (err?.message || err));
    } finally {
      if (btn) btn.disabled = false;
      currentExtinguisherId = null;
    }
  });
})();

// ===== 소화기등록/수정 모달 (_ExtinguisherUpsertModalScripts.cshtml 기반) =====
(function(){
  const modalEl = document.getElementById('extUpsertModal');
  const formEl  = document.getElementById('extUpsertForm');
  const saveBtn = document.getElementById('extSaveBtn');
  if(!modalEl || !formEl || !saveBtn) return;
  const forceExtTypeSelect = () => {
    const oldEl = document.getElementById('extType');
    if (!oldEl || oldEl.tagName === 'SELECT') return;
    const sel = document.createElement('select');
    sel.className = 'form-select';
    sel.name = 'ExtinguisherType';
    sel.id = 'extType';
    sel.required = true;
    sel.innerHTML = [
      '<option value="">-- 종류 선택 --</option>',
      '<option value="분말소화기 3kg">분말소화기 3kg</option>',
      '<option value="분말소화기 20kg">분말소화기 20kg</option>',
      '<option value="이산화탄소소화기">이산화탄소소화기</option>'
    ].join('');
    const prev = String(oldEl.value || '');
    oldEl.replaceWith(sel);
    if (prev) sel.value = prev;
  };
  forceExtTypeSelect();
  const buildingSel = document.getElementById('extBuildingSel');
  const floorSel = document.getElementById('extFloorSel');
  const mapCanvas = document.getElementById('extMapCanvas');
  const mapSection = document.getElementById('extMapSection');
  const planImg = document.getElementById('extPlanImg');
  const markerLayer = document.getElementById('extMarkerLayer');
  const EXT_ICON_URL = '/images/Extinguisher.PNG';
  const coordXEl = document.getElementById('extCoordX');
  const coordYEl = document.getElementById('extCoordY');
  const histBody = document.getElementById('extInspectHistoryBody');
  let currentEditingExtId = 0;
  let selectedCoord = null;

  function setVal(id, v){
    const el = document.getElementById(id);
    if(el) el.value = (v ?? '').toString();
  }
  function norm(v){ return String(v || '').replace(/\s+/g,'').toLowerCase(); }
  function normFloor(v){
    const s = norm(v).replace(/[()]/g, '');
    if (!s) return '';
    if (s === 'b1' || s.includes('지하1') || s.includes('지하') || s.includes('地下1') || s.includes('地下')) return 'b1';
    const m = s.match(/([1-9])\s*f?$/);
    if (m) return `${m[1]}f`;
    if (s.includes('1')) return '1f';
    if (s.includes('2')) return '2f';
    if (s.includes('3')) return '3f';
    if (s.includes('4')) return '4f';
    return s;
  }
  function normBuilding(v){
    return norm(v).replace(/[.,_\-]/g,'');
  }
  function parseNum(v){
    const n = parseFloat(String(v ?? ''));
    return Number.isFinite(n) ? n : null;
  }
  function mapTypeValue(v){
    const raw = String(v || '').trim();
    const n = raw.replace(/\s+/g, '').toLowerCase();
    if (n === '분말소화기3kg') return '분말소화기 3kg';
    if (n === '분말소화기20kg') return '분말소화기 20kg';
    if (n === '이산화탄소소화기') return '이산화탄소소화기';
    return raw;
  }
  function setCoord(x, y){
    const rx = Number(x).toFixed(2);
    const ry = Number(y).toFixed(2);
    setVal('extX', rx);
    setVal('extY', ry);
    if (coordXEl) coordXEl.textContent = rx;
    if (coordYEl) coordYEl.textContent = ry;
    selectedCoord = { x: Number(rx), y: Number(ry) };
    renderSelectedMarker();
  }
  function clearCoord(){
    setVal('extX', '');
    setVal('extY', '');
    if (coordXEl) coordXEl.textContent = '-';
    if (coordYEl) coordYEl.textContent = '-';
    selectedCoord = null;
    renderSelectedMarker();
  }
  function computeFit(){
    const rect = mapCanvas?.getBoundingClientRect?.();
    const cw = rect?.width || 0;
    const ch = rect?.height || 0;
    const iw = planImg?.naturalWidth || 0;
    const ih = planImg?.naturalHeight || 0;
    if (!cw || !ch || !iw || !ih) return null;
    const scale = Math.min(cw / iw, ch / ih);
    const w = iw * scale;
    const h = ih * scale;
    return { ox:(cw - w) / 2, oy:(ch - h) / 2, w, h };
  }
  function layoutPlan(f){
    if (!planImg || !f) return;
    planImg.style.cssText = `position:absolute;left:${f.ox}px;top:${f.oy}px;width:${f.w}px;height:${f.h}px;transform:none;object-fit:contain;display:block;`;
  }
  function toPx(x, y, f){
    if (!f) return null;
    return { left: f.ox + f.w * (x / 100), top: f.oy + f.h * (y / 100) };
  }
  function renderSelectedMarker(){
    if (!markerLayer) return;
    markerLayer.innerHTML = '';
    const f = computeFit();
    if (!f) return;
    layoutPlan(f);
    const x = (selectedCoord && Number.isFinite(selectedCoord.x)) ? selectedCoord.x : parseNum(document.getElementById('extX')?.value);
    const y = (selectedCoord && Number.isFinite(selectedCoord.y)) ? selectedCoord.y : parseNum(document.getElementById('extY')?.value);
    if (x == null || y == null) return;
    const pos = toPx(Number(x), Number(y), f);
    if (!pos) return;
    const marker = document.createElement('div');
    marker.style.cssText = [
      'position:absolute',
      `left:${pos.left}px`,
      `top:${pos.top}px`,
      'transform:translate(-50%,-50%)',
      'width:34px',
      'height:34px',
      'pointer-events:none',
      'border-radius:50%',
      'background:rgba(220,53,69,.18)'
    ].join(';');
    marker.innerHTML = `<img src="${EXT_ICON_URL}" alt="extinguisher-marker" style="width:100%;height:100%;object-fit:contain;display:block;filter:drop-shadow(0 0 4px rgba(0,0,0,.35));" onerror="this.style.display='none';" />`;
    markerLayer.appendChild(marker);
  }
  function selectOptionSmart(sel, id, name){
    if (!sel) return;
    const sid = String(id ?? '').trim();
    if (sid) {
      const byValue = Array.from(sel.options || []).find(x => String(x.value).trim() === sid);
      if (byValue) { sel.value = sid; return; }
    }
    const n = norm(name);
    if (!n) return;
    const byText = Array.from(sel.options || []).find(x => norm(x.textContent) === n);
    if (byText) { sel.value = byText.value; return; }
    const floorAlias = normFloor(name);
    if (floorAlias) {
      const byFloor = Array.from(sel.options || []).find(x => normFloor(x.textContent) === floorAlias);
      if (byFloor) { sel.value = byFloor.value; return; }
    }
    const byContains = Array.from(sel.options || []).find(x => norm(x.textContent).includes(n) || n.includes(norm(x.textContent)));
    if (byContains) sel.value = byContains.value;
  }
  function resolvePlanImagePathByName(buildingName, floorName){
    const b = normBuilding(buildingName);
    const f = normFloor(floorName);
    if (b.includes('복지관') || b.includes('bokji')) {
      if (f === 'b1') return '/images/bokji_B1.png';
      if (f === '1f') return '/images/bokji_1F.png';
      if (f === '2f') return '/images/bokji_2F.png';
      if (f === '3f') return '/images/bokji_3F.png';
    }
    if ((b.includes('관리동') || b.includes('gwanri')) && f === '1f') return '/images/gwanri_1F.png';
    if (b.includes('제지12호기') || b.includes('jeji12')) {
      if (f === '1f') return '/images/jeji1,2_1F.PNG';
      if (f === '2f') return '/images/jeji1,2_2F.PNG';
    }
    if (b.includes('제지3호기') || b.includes('jeji3')) {
      if (f === '1f') return '/images/jeji3_1F.PNG';
      if (f === '2f') return '/images/jeji3_2F.PNG';
    }
    if (b.includes('패드동') || b.includes('pad')) {
      if (f === '1f') return '/images/pad_1F.PNG';
      if (f === '2f') return '/images/pad_2F.PNG';
    }
    if (b.includes('심면펄퍼') || b.includes('palpa') || b.includes('pulper')) {
      if (f === '1f') return '/images/palpa_1F.PNG';
      if (f === '2f') return '/images/palpa_2F.PNG';
    }
    if (b.includes('화장지36호기') || b.includes('tissue13') || b.includes('tissue36')) {
      if (f === '1f') return '/images/tissue1,3_1F.PNG';
      if (f === '2f') return '/images/tissue1,3_2F.PNG';
    }
    if (b.includes('화장지45호기') || b.includes('tissue45')) {
      if (f === 'b1') return '/images/tissue4,5_B1.PNG';
      if (f === '1f') return '/images/tissue4,5_1F.PNG';
      if (f === '2f') return '/images/tissue4,5_2F.PNG';
      if (f === '3f') return '/images/tissue4,5_3F.PNG';
    }
    if ((b.includes('기저귀동') || b.includes('diaper')) && f === '1f') return '/images/diaper_1F.png';
    if (b.includes('옥외') || b.includes('outdoor')) return '/images/drone_photo.JPG';
    if (b.includes('복지')) {
      if (f === 'b1') return '/images/bokji_B1.png';
      if (f.includes('1')) return '/images/bokji_1F.png';
      if (f.includes('2')) return '/images/bokji_2F.png';
      if (f.includes('3')) return '/images/bokji_3F.png';
    }
    if (b.includes('관리') && f === '1f') return '/images/gwanri_1F.png';
    return '';
  }
  async function loadPlanImage(forceBuildingId, forceFloorId){
    const b = String(forceBuildingId ?? (buildingSel?.value || '')).trim();
    const f = String(forceFloorId ?? (floorSel?.value || '')).trim();
    if (!b || !f || !planImg) {
      if (planImg) planImg.removeAttribute('src');
      clearCoord();
      return;
    }
    let mapUrl = '';
    try {
      const params = new URLSearchParams({ buildingId: b, floorId: f });
      const r = await API.req(`/fire-api/maps/floor-data?${params.toString()}`);
      if (r && r.ok) {
        const j = await r.json().catch(() => null);
        mapUrl = j?.data?.planImagePath || '';
      }
    } catch {}
    if (!mapUrl) {
      const bName = buildingSel?.selectedOptions?.[0]?.textContent || '';
      const fName = floorSel?.selectedOptions?.[0]?.textContent || '';
      mapUrl = resolvePlanImagePathByName(bName, fName);
    }
    if (!mapUrl) {
      planImg.removeAttribute('src');
      renderSelectedMarker();
      return;
    }
    planImg.onload = () => renderSelectedMarker();
    planImg.src = `${mapUrl}?v=${Date.now()}`;
  }
  function syncHiddenFromSelect(){
    setVal('extBuildingId', buildingSel?.value || '');
    setVal('extFloorId', floorSel?.value || '');
  }
  function renderHistory(inspections){
    if (!histBody) return;
    const addBtn = document.getElementById('extHistAddBtn');
    if (addBtn) addBtn.style.display = (isAdmin && currentEditingExtId > 0) ? '' : 'none';
    const rows = Array.isArray(inspections) ? inspections.slice(0, 12) : [];
    if (!rows.length) {
      histBody.innerHTML = '<tr><td colspan="5" class="text-muted text-center">점검 이력이 없습니다.</td></tr>';
      return;
    }
    histBody.innerHTML = rows.map(r => `
      <tr>
        <td style="text-align:center;">${isAdmin
          ? `<input type="date" class="form-control form-control-sm js-ext-hist-date" data-inspection-id="${r.inspectionId || ''}" value="${r.inspectionDate ? String(r.inspectionDate).slice(0,10) : ''}" />`
          : (r.inspectionDate ? String(r.inspectionDate).slice(0,10) : '-')
        }</td>
        <td class="text-truncate" style="text-align:center;">${r.inspectorName || '-'}</td>
        <td style="text-align:center;">${isAdmin
          ? `<select class="form-select form-select-sm js-ext-hist-result" data-inspection-id="${r.inspectionId || ''}">
               <option value="ok" ${r.isFaulty ? '' : 'selected'}>정상</option>
               <option value="bad" ${r.isFaulty ? 'selected' : ''}>비정상</option>
             </select>`
          : (r.isFaulty ? '<span class="fw-status fw-bad">비정상</span>' : '<span class="fw-status fw-ok">정상</span>')
        }</td>
        <td>${isAdmin
          ? `<input class="form-control form-control-sm js-ext-hist-reason" data-inspection-id="${r.inspectionId || ''}" value="${r.faultReason || ''}" ${r.isFaulty ? '' : 'disabled'} placeholder="고장 사유" />`
          : (r.isFaulty ? (r.faultReason || '-') : '-')
        }</td>
        <td style="text-align:center;">${isAdmin ? `<button type="button" class="btn btn-sm btn-outline-primary js-ext-hist-save" data-inspection-id="${r.inspectionId || ''}">수정</button>` : '-'}</td>
      </tr>`).join('');
  }

  window.openExtinguisherUpsertModal = async function(opts){
    const id = Number(opts?.id || 0);
    currentEditingExtId = id;
    const qs = new URLSearchParams(window.location.search);
    const hideMap = opts?.hideMap === true || qs.get('noMap') === '1';
    document.getElementById('extUpsertTitle').textContent = id ? '소화기 편집' : '소화기 추가';
    if (mapSection) mapSection.style.display = hideMap ? 'none' : '';
    formEl.reset();
    setVal('extId', id);
    setVal('extBuildingId', opts?.buildingId ?? '');
    setVal('extFloorId', opts?.floorId ?? '');
    setVal('extX', opts?.x ?? '');
    setVal('extY', opts?.y ?? '');
    renderHistory([]);

    if(!id){
      const fb = document.getElementById('filterBuildingId')?.value || '';
      const ff = document.getElementById('filterFloorId')?.value || '';
      if (buildingSel) {
        selectOptionSmart(buildingSel, (opts?.buildingId ?? fb ?? ''), (opts?.buildingName ?? ''));
      }
      if (floorSel) {
        selectOptionSmart(floorSel, (opts?.floorId ?? ff ?? ''), (opts?.floorName ?? ''));
      }
      syncHiddenFromSelect();
      setVal('extType','');
      setVal('extInstallDate', new Date().toISOString().slice(0,10));
      setVal('extQty', 1);
      setVal('extNote','');
      const file = document.getElementById('extPhoto');
      if(file) file.value='';
      if (opts?.x != null && opts?.y != null) setCoord(opts.x, opts.y);
      else clearCoord();
      if (!hideMap) await loadPlanImage();
      bootstrap.Modal.getOrCreateInstance(modalEl).show();
      return;
    }

    try{
      const res = await API.req(`/fire-api/extinguishers/${id}`);
      if(!res) return;
      const json = await res.json();
      const data = json.data;
      selectOptionSmart(buildingSel, data.buildingId, data.buildingName);
      selectOptionSmart(floorSel, data.floorId, data.floorName);
      syncHiddenFromSelect();
      setVal('extBuildingId', data.buildingId);
      setVal('extFloorId', data.floorId);
      setVal('extX', data.x ?? '');
      setVal('extY', data.y ?? '');
      const mappedType = mapTypeValue(data.extinguisherType);
      const extTypeEl = document.getElementById('extType');
      if (extTypeEl && mappedType && !Array.from(extTypeEl.options).some(o => o.value === mappedType)) {
        const opt = document.createElement('option');
        opt.value = mappedType;
        opt.textContent = mappedType;
        extTypeEl.appendChild(opt);
      }
      setVal('extType', mappedType);
      setVal('extInstallDate', data.installDate ? data.installDate.slice(0,10) : '');
      setVal('extQty', data.quantity ?? 1);
      setVal('extNote', data.note ?? '');
      renderHistory(data.inspections);
      const file = document.getElementById('extPhoto');
      if(file) file.value='';
      if (!hideMap) {
        await loadPlanImage(data.buildingId, data.floorId);
        if (data.x != null && data.y != null) setCoord(data.x, data.y);
        else clearCoord();
      }
      bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }catch(err){
      alert('소화기 로드 실패: '+(err?.message||err));
    }
  };

  saveBtn.addEventListener('click', async function(){
    saveBtn.disabled = true;
    try {
      const id = Number(document.getElementById('extId')?.value || 0);
      syncHiddenFromSelect();
      const body = {
        extinguisherId: id || undefined,
        buildingId: parseInt(document.getElementById('extBuildingId')?.value || '0'),
        floorId: parseInt(document.getElementById('extFloorId')?.value || '0'),
        extinguisherType: document.getElementById('extType')?.value || '',
        installDate: document.getElementById('extInstallDate')?.value || '',
        quantity: parseInt(document.getElementById('extQty')?.value || '1'),
        note: document.getElementById('extNote')?.value || '',
        x: document.getElementById('extX')?.value ? parseFloat(document.getElementById('extX').value) : null,
        y: document.getElementById('extY')?.value ? parseFloat(document.getElementById('extY').value) : null,
      };
      if (!body.buildingId || !body.floorId) { alert('건물과 층을 선택해주세요.'); return; }
      if (!body.extinguisherType) { alert('소화기 종류를 입력해 주세요.'); return; }
      if (!body.installDate) { alert('제조일을 입력해주세요.'); return; }

      const r = await API.req('/fire-api/extinguishers', { method:'POST', body });
      if (!r || !r.ok) { const t=await r?.text().catch(()=>''); alert('저장 실패: '+t); return; }
      bootstrap.Modal.getInstance(modalEl)?.hide();
      document.dispatchEvent(new CustomEvent('extinguisher:upsertCompleted'));
    } finally {
      saveBtn.disabled = false;
    }
  });

  histBody?.addEventListener('click', async (ev) => {
    const btn = ev.target.closest('.js-ext-hist-save');
    if (!btn) return;
    if (!isAdmin) return alert('관리자만 수정 가능합니다.');
    const inspectionIdRaw = (btn.getAttribute('data-inspection-id') || '').trim();
    const inspectionId = Number(inspectionIdRaw || '0');
    if (!currentEditingExtId) return;
    const rowDate = histBody.querySelector(`.js-ext-hist-date[data-inspection-id="${inspectionId}"]`);
    const rowResult = histBody.querySelector(`.js-ext-hist-result[data-inspection-id="${inspectionId}"]`);
    const rowReason = histBody.querySelector(`.js-ext-hist-reason[data-inspection-id="${inspectionId}"]`);
    const inspectionDate = rowDate?.value || '';
    const isFaulty = (rowResult?.value || 'ok') === 'bad';
    const faultReason = (rowReason?.value || '').trim();
    if (!inspectionDate) return alert('점검일을 입력해 주세요.');
    if (isFaulty && !faultReason) return alert('비정상인 경우 고장 사유를 입력해 주세요.');
    btn.disabled = true;
    try {
      let ur = null;
      if (inspectionId > 0) {
        ur = await API.req(`/fire-api/extinguishers/${currentEditingExtId}/inspections/${inspectionId}`, {
          method: 'PATCH',
          body: { inspectionDate, isFaulty, faultReason }
        });
      } else {
        ur = await API.req('/fire-api/extinguishers/inspect', {
          method: 'POST',
          body: { extinguisherId: currentEditingExtId, inspectionDate, isFaulty, faultReason }
        });
      }
      if (!ur || !ur.ok) {
        const t = await ur?.text().catch(()=>'');
        alert(t || '점검 이력 저장에 실패했습니다.');
        return;
      }
      const rr = await API.req(`/fire-api/extinguishers/${currentEditingExtId}`);
      const rj = rr && rr.ok ? await rr.json().catch(() => null) : null;
      renderHistory(rj?.data?.inspections || []);
      alert('점검 이력이 저장되었습니다.');
    } finally {
      btn.disabled = false;
    }
  });

  histBody?.addEventListener('change', (ev) => {
    const sel = ev.target.closest('.js-ext-hist-result');
    if (!sel) return;
    const id = sel.getAttribute('data-inspection-id') || '';
    const reason = histBody.querySelector(`.js-ext-hist-reason[data-inspection-id="${id}"]`);
    if (!reason) return;
    const bad = sel.value === 'bad';
    reason.disabled = !bad;
    if (!bad) reason.value = '';
  });

  document.getElementById('extHistAddBtn')?.addEventListener('click', () => {
    if (!isAdmin || !histBody) return;
    if (!currentEditingExtId) return alert('저장된 소화기에서만 점검 이력을 추가할 수 있습니다.');
    const today = new Date().toISOString().slice(0,10);
    const empty = histBody.querySelector('td[colspan="5"]');
    if (empty) histBody.innerHTML = '';
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td style="text-align:center;"><input type="date" class="form-control form-control-sm js-ext-hist-date" data-inspection-id="" value="${today}" /></td>
      <td style="text-align:center;">-</td>
      <td style="text-align:center;">
        <select class="form-select form-select-sm js-ext-hist-result" data-inspection-id="">
          <option value="ok" selected>정상</option>
          <option value="bad">비정상</option>
        </select>
      </td>
      <td><input class="form-control form-control-sm js-ext-hist-reason" data-inspection-id="" value="" disabled placeholder="고장 사유" /></td>
      <td style="text-align:center;"><button type="button" class="btn btn-sm btn-outline-primary js-ext-hist-save" data-inspection-id="">수정</button></td>`;
    histBody.prepend(tr);
  });

  buildingSel?.addEventListener('change', async () => {
    syncHiddenFromSelect();
    await loadPlanImage();
  });
  floorSel?.addEventListener('change', async () => {
    syncHiddenFromSelect();
    await loadPlanImage();
  });
  planImg?.addEventListener('load', renderSelectedMarker);
  window.addEventListener('resize', renderSelectedMarker);
  mapCanvas?.addEventListener('click', (e) => {
    const f = computeFit();
    if (!f) return;
    const rect = mapCanvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    if (px < f.ox || px > f.ox + f.w || py < f.oy || py > f.oy + f.h) return;
    const x = Math.max(0, Math.min(100, ((px - f.ox) / f.w) * 100));
    const y = Math.max(0, Math.min(100, ((py - f.oy) / f.h) * 100));
    setCoord(x, y);
  });
  modalEl?.addEventListener('shown.bs.modal', renderSelectedMarker);
})();
// ===== ?대깽???꾩엫 (?대┃) =====
document.addEventListener('click', async (ev) => {
  const inspBtn = ev.target.closest('.js-inspect');
  if (inspBtn) {
    ev.preventDefault();
    const id = Number(inspBtn.getAttribute('data-id')||'0');
    if (id) window.openInspectModal(ev, id);
    return;
  }

  const editBtn = ev.target.closest('.js-ext-edit');
  if (editBtn) {
    ev.preventDefault();
    const id = Number(editBtn.getAttribute('data-id')||'0');
    if (id) window.openExtinguisherUpsertModal({ id });
    return;
  }

  const row = ev.target.closest('tr.clickable-row[data-id]');
  if (row) {
    if (ev.target.closest('a,button')) return;
    const id = Number(row.getAttribute('data-id')||'0');
    if (id) openDetails(id);
    return;
  }

  const zoomable = ev.target.closest('.js-zoomable');
  if (zoomable) {
    const src = zoomable.getAttribute('data-zoom-src') || zoomable.getAttribute('src');
    if (src) {
      const target = document.getElementById('detailImageZoomTarget');
      const layer = document.getElementById('detailImageZoomMarkerLayer');
      const mx = parseFloat(zoomable.getAttribute('data-marker-x') || '');
      const my = parseFloat(zoomable.getAttribute('data-marker-y') || '');
      const icon = zoomable.getAttribute('data-marker-icon') || '/images/Extinguisher.PNG';
      const drawZoomMarker = () => {
        if (!layer) return;
        layer.innerHTML = '';
        if (!Number.isFinite(mx) || !Number.isFinite(my)) return;
        const stage = document.getElementById('detailImageZoomStage');
        if (!stage || !target) return;
        const rect = stage.getBoundingClientRect();
        const cw = rect.width || 0, ch = rect.height || 0;
        const iw = target.naturalWidth || 0, ih = target.naturalHeight || 0;
        if (!cw || !ch || !iw || !ih) return;
        const scale = Math.min(cw / iw, ch / ih);
        const w = iw * scale, h = ih * scale;
        const ox = (cw - w) / 2, oy = (ch - h) / 2;
        const markerSize = Math.max(10, Math.min(34, Math.round(w * 0.032)));
        const left = ox + w * (Math.max(0, Math.min(100, mx)) / 100);
        const top = oy + h * (Math.max(0, Math.min(100, my)) / 100);
        const m = document.createElement('div');
        m.style.cssText = `position:absolute;left:${left}px;top:${top}px;width:${markerSize}px;height:${markerSize}px;transform:translate(-50%,-50%);pointer-events:none;filter:drop-shadow(0 0 6px rgba(0,0,0,.65));`;
        m.innerHTML = `<img src="${icon}" alt="" style="width:100%;height:100%;object-fit:contain;display:block;">`;
        layer.appendChild(m);
      };
      if (target) {
        target.onload = drawZoomMarker;
        target.src = src;
      }
      bootstrap.Modal.getOrCreateInstance(document.getElementById('detailImageZoomModal')).show();
      setTimeout(drawZoomMarker, 0);
    }
  }
});

// ===== 踰꾪듉 ?대깽??=====
document.getElementById('btnSearch')?.addEventListener('click', () => {
  const q = document.getElementById('filterQ')?.value || '';
  const b = document.getElementById('filterBuildingId')?.value || '';
  const f = document.getElementById('filterFloorId')?.value || '';
  loadExtinguishers(q, b, f);
});
document.getElementById('filterQ')?.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') document.getElementById('btnSearch')?.click();
});
document.getElementById('btnReset')?.addEventListener('click', () => {
  document.getElementById('filterQ').value = '';
  document.getElementById('filterBuildingId').value = '';
  document.getElementById('filterFloorId').value = '';
  loadExtinguishers();
});
document.getElementById('btnStatusInspect')?.addEventListener('click', () => openStatus('inspect'));
document.getElementById('btnStatusPlanned')?.addEventListener('click', () => openStatus('planned'));
document.getElementById('btnStatusUrgent')?.addEventListener('click', () => openStatus('urgent'));
document.getElementById('btnExtAdd')?.addEventListener('click', () => window.openExtinguisherUpsertModal({ id: 0 }));

// 등록/점검 완료 ???덈줈怨좎묠
document.addEventListener('inspection:completed', () => loadExtinguishers(
  document.getElementById('filterQ')?.value||'',
  document.getElementById('filterBuildingId')?.value||'',
  document.getElementById('filterFloorId')?.value||''));
document.addEventListener('extinguisher:upsertCompleted', () => loadExtinguishers(
  document.getElementById('filterQ')?.value||'',
  document.getElementById('filterBuildingId')?.value||'',
  document.getElementById('filterFloorId')?.value||''));

function _norm(v) { return String(v || '').replace(/\s+/g,'').toLowerCase(); }
function _normBuildingCompat(v) { return _norm(v).replace(/[.,_\-]/g,''); }
function _normFloorCompat(v) {
  const s = _norm(v).replace(/[()]/g, '');
  if (!s) return '';
  if (s === 'b1' || s.includes('지하1') || s.includes('지하') || s.includes('地下1') || s.includes('地下')) return 'b1';
  if (s.includes('1')) return '1f';
  if (s.includes('2')) return '2f';
  if (s.includes('3')) return '3f';
  if (s.includes('4')) return '4f';
  return s;
}

// ===== 珥덇린??=====
(async function init() {
  const qs = new URLSearchParams(window.location.search);
  const embedEditMode = qs.get('embedEdit') === '1';
  const embedDetailsMode = qs.get('embedDetails') === '1';
  const embedInspectMode = qs.get('embedInspect') === '1';

  const token = localStorage.getItem('fireweb_token');
  if (!token) { window.location.href = '/login.html'; return; }
  window.FireWebNav?.mount?.();
  if (embedEditMode || embedDetailsMode || embedInspectMode) {
    const headerEl = document.querySelector('header');
    const mainEl = document.querySelector('main');
    if (headerEl) headerEl.style.display = 'none';
    if (mainEl) mainEl.style.display = 'none';
    // iframe 내부를 완전 투명하게 — 모달만 표시
    const embedStyle = document.createElement('style');
    embedStyle.textContent = [
      'html, body { background: transparent !important; }',
      '.modal-backdrop { display: none !important; }',
      '.modal { background: transparent !important; }',
      '#extUpsertModal .modal-header { background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color:#fff; border-bottom:0; }',
      '#extUpsertModal .modal-title { color:#fff; font-weight:700; }',
      '#extUpsertModal .btn-close { filter: invert(1) grayscale(100%) brightness(200%); }'
    ].join('\n');
    document.head.appendChild(embedStyle);
  }
  if (isAdmin) {
    const addBtn = document.getElementById('btnExtAdd');
    if (addBtn) addBtn.style.display = '';
  }
  await loadExtinguishers();

  const editId = parseInt(qs.get('edit') || '0', 10);
  if (isAdmin && editId > 0) {
    window.openExtinguisherUpsertModal({ id: editId });
    return;
  }

  const detailsId = parseInt(qs.get('details') || '0', 10);
  if (detailsId > 0) {
    openDetails(detailsId);
    return;
  }

  const inspectId = parseInt(qs.get('inspect') || '0', 10);
  if (inspectId > 0) {
    window.openInspectModal?.(null, inspectId);
    return;
  }

  const add = qs.get('add');
  if (isAdmin && add === '1') {
    const bIdQ = qs.get('buildingId') || '';
    const fIdQ = qs.get('floorId') || '';
    const bx = qs.get('buildingName') || '';
    const fx = qs.get('floorName') || '';
    const x = parseFloat(qs.get('x') || '');
    const y = parseFloat(qs.get('y') || '');

    let matched = null;
    if (bIdQ && fIdQ) {
      matched = allItems.find(it =>
        String(it.buildingId || '') === String(bIdQ) &&
        String(it.floorId || '') === String(fIdQ)
      );
    }
    if (!matched) {
      matched = allItems.find(it =>
        _normBuildingCompat(it.buildingName) === _normBuildingCompat(bx) &&
        _normFloorCompat(it.floorName) === _normFloorCompat(fx)
      );
    }
    window.openExtinguisherUpsertModal({
      id: 0,
      buildingId: matched?.buildingId || bIdQ || '',
      floorId: matched?.floorId || fIdQ || '',
      buildingName: matched?.buildingName || bx || '',
      floorName: matched?.floorName || fx || '',
      x: Number.isFinite(x) ? x : '',
      y: Number.isFinite(y) ? y : ''
    });
  }
})();

document.getElementById('extUpsertModal')?.addEventListener('hidden.bs.modal', () => {
  const qs = new URLSearchParams(window.location.search);
  if (qs.get('embedEdit') === '1') {
    try { window.parent?.postMessage('fireweb:ext-edit-close', '*'); } catch {}
  }
});

document.getElementById('detailsModal')?.addEventListener('hidden.bs.modal', () => {
  const qs = new URLSearchParams(window.location.search);
  if (qs.get('embedDetails') === '1') {
    try { window.parent?.postMessage('fireweb:ext-details-close', '*'); } catch {}
  }
});

document.getElementById('detailImageZoomTarget')?.addEventListener('click', () => {
  const m = document.getElementById('detailImageZoomModal');
  if (m) bootstrap.Modal.getOrCreateInstance(m).hide();
});

document.getElementById('inspectModal')?.addEventListener('hidden.bs.modal', () => {
  const qs = new URLSearchParams(window.location.search);
  if (qs.get('embedInspect') === '1') {
    try { window.parent?.postMessage('fireweb:ext-inspect-close', '*'); } catch {}
  }
});
