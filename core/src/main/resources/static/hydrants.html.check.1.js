// ===== JWT & 공용 API =====
const API = (() => {
  const getToken = () => localStorage.getItem('fireweb_token');
  const getUser  = () => { try{ return JSON.parse(localStorage.getItem('fireweb_user')||'null'); }catch{ return null; } };
  const isAdmin  = () => { const u=getUser(); return u && u.role === 'ADMIN'; };
  async function req(url, opts={}) {
    const token = getToken();
    const headers = {...(opts.headers||{})};
    if (token) headers['Authorization'] = 'Bearer ' + token;
    if (!(opts.body instanceof FormData) && opts.body && typeof opts.body==='object') {
      headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(opts.body);
    }
    const res = await fetch(url, {...opts, headers});
    if (res.status === 401) { window.location.href='/login.html'; return null; }
    return res;
  }
  return { req, getToken, getUser, isAdmin };
})();

let allItems = [];
let currentStatusType = null;
const isAdmin = API.isAdmin();

// ===== 토스트 =====
function showFwToast(msg, type='success') {
  const wrap = document.getElementById('fwToast');
  const id = 'fwToastBox';
  let box = document.getElementById(id);
  if (!box) { box=document.createElement('div'); box.id=id; wrap.appendChild(box); }
  box.className=`alert alert-${type} shadow`;
  box.style.cssText='margin:0;opacity:0;transition:opacity 500ms ease;';
  box.textContent=msg;
  wrap.style.cssText='position:fixed;top:16px;right:16px;z-index:2000;';
  requestAnimationFrame(()=>{ box.style.opacity='1'; });
  setTimeout(()=>{ box.style.opacity='0'; },2200);
  setTimeout(()=>{ wrap.innerHTML=''; },3200);
}

// ===== 내비게이션 계정 영역 렌더링 =====
function renderNav() {
  const user = API.getUser();
  const area = document.getElementById('navAccountArea');
  if (!area) return;
  if (user) {
    area.innerHTML=`
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
    area.innerHTML='<li class="nav-item"><a class="btn btn-sm btn-outline-light" href="/login.html">로그인</a></li>';
  }
}
function logout() {
  localStorage.removeItem('fireweb_token');
  localStorage.removeItem('fireweb_user');
  window.location.href = '/login.html';
}

// ===== 건물/층 옵션 로드 =====
async function loadBuildingFloorOptions() {
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

  // Fallback: QR API 실패 시 현재 목록 데이터에서 건물/층 정보 구성
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

  const bSel = document.getElementById('filterBuildingId');
  const fSel = document.getElementById('filterFloorId');
  bSel.innerHTML='<option value="">-- 건물 전체 --</option>'+buildings.map(x=>`<option value="${x.buildingId}">${x.buildingName}</option>`).join('');
  fSel.innerHTML='<option value="">-- 층 전체 --</option>'+floors.map(x=>`<option value="${x.floorId}">${x.floorName}</option>`).join('');

  const hb = document.getElementById('hydBuildingSel');
  const hf = document.getElementById('hydFloorSel');
  if (hb) hb.innerHTML='<option value="">-- 건물 선택 --</option>'+buildings.map(x=>`<option value="${x.buildingId}">${x.buildingName}</option>`).join('');
  if (hf) hf.innerHTML='<option value="">-- 층 선택 --</option>'+floors.map(x=>`<option value="${x.floorId}">${x.floorName}</option>`).join('');
}

async function ensureHydModalOptions() {
  const hb = document.getElementById('hydBuildingSel');
  const hf = document.getElementById('hydFloorSel');
  if (!hb || !hf) return;
  // allItems가 채워진 경우 항상 최신 데이터로 채움 (buildingId 정합성 보장)
  // allItems가 없을 때만 캐시 허용
  if (allItems && allItems.length > 0) {
    await loadBuildingFloorOptions();
    return;
  }
  if ((hb.options?.length || 0) > 1 && (hf.options?.length || 0) > 1) return;
  await loadBuildingFloorOptions();
}

// ===== 소화전목록 로드 =====
async function loadHydrants(q='', buildingId='', floorId='') {
  const params = new URLSearchParams({ size:200, page:0 });
  if (q) params.set('q',q);
  if (buildingId) params.set('buildingId',buildingId);
  if (floorId) params.set('floorId',floorId);
  const res = await API.req(`/fire-api/hydrants?${params}`);
  if (!res) return;
  const json = await res.json();
  if (json.ok && json.data) {
    allItems = json.data.content || [];
  } else {
    allItems = [];
  }
  await loadBuildingFloorOptions();
  renderTable(allItems);
  updateBucketCounts();
}

function updateBucketCounts() {
  const today = new Date(); today.setHours(0,0,0,0);
  let inspectCount=0, faultyCount=0;
  allItems.forEach(item => {
    const lastDate = item.lastInspectionDate ? new Date(item.lastInspectionDate) : null;
    if (!lastDate || new Date(lastDate.getTime()+30*24*3600*1000)<=today) inspectCount++;
    if (item.lastIsFaulty===true) faultyCount++;
  });
  document.getElementById('totalCount').textContent = allItems.length;
  document.getElementById('countInspect').textContent = `(${inspectCount})`;
  document.getElementById('countFaulty').textContent = `(${faultyCount})`;
}

// ===== 메인 테이블 렌더링 (_HydListTable.cshtml 기반) =====
function typeText(t) { return String(t||'').toLowerCase()==='outdoor'?'옥외':'옥내'; }
function opText(t) { return String(t||'').toLowerCase()==='auto'?'자동':'수동'; }

function renderTable(items) {
  const wrap = document.getElementById('mainTableWrap');
  if (!items.length) {
    wrap.innerHTML='<div class="text-center text-muted py-4">조회 결과가 없습니다.</div>';
    return;
  }
  let rows = items.map(x => {
    const loc = x.hydrantType==='Outdoor'
      ? (x.locationDescription||'-')
      : (x.x!=null&&x.y!=null ? `도면좌표 ${Number(x.x).toFixed(2)}%, ${Number(x.y).toFixed(2)}%` : '-');
    let statusBadge = '';
    if (x.lastInspectionDate==null) statusBadge='<span class="text-muted">-</span>';
    else if (x.lastIsFaulty===true) statusBadge='<span class="fw-status fw-bad">비정상</span>';
    else statusBadge='<span class="fw-status fw-ok">정상</span>';
    return `
      <tr class="clickable-row" data-id="${x.hydrantId}">
        <td class="text-truncate" style="text-align:center;">${x.serialNumber||'-'}</td>
        <td class="text-truncate">${typeText(x.hydrantType)}</td>
        <td class="text-truncate">${opText(x.operationType)}</td>
        <td class="text-truncate">${x.buildingName||'-'}</td>
        <td class="text-truncate">${x.floorName||'-'}</td>
        <td class="text-truncate">${loc}</td>
        <td>${x.lastInspectionDate?x.lastInspectionDate.slice(0,10):'-'}</td>
        <td class="text-truncate">${x.lastInspectorName||'-'}</td>
        <td>${statusBadge}</td>
        <td class="text-truncate">${x.lastIsFaulty===true?(x.lastFaultReason||'-'):'-'}</td>
        <td class="text-center">
          <div class="hyd-actions">
            ${isAdmin?`<button type="button" class="btn btn-sm btn-fw-edit js-hydrant-edit" data-id="${x.hydrantId}">수정</button>`:''}
            <button type="button" class="btn btn-sm btn-fw-inspect js-hydrant-inspect" data-id="${x.hydrantId}">점검</button>
          </div>
        </td>
      </tr>`;
  }).join('');

  wrap.innerHTML=`
    <div class="fw-table-wrap"><div class="table-responsive">
      <table class="table table-hover mb-0 hyd-list-table" style="table-layout:fixed;">
        <thead class="table-dark">
          <tr>
            <th style="width:140px;text-align:center;">소화전 ID</th>
            <th style="width:90px;">구분</th>
            <th style="width:90px;">방식</th>
            <th style="width:140px;">건물</th>
            <th style="width:110px;">층</th>
            <th style="width:170px;">위치</th>
            <th style="width:110px;">최종 점검일</th>
            <th style="width:110px;">점검자</th>
            <th style="width:90px;">정상/비정상</th>
            <th style="width:220px;">고장 사유</th>
            <th style="width:150px;text-align:center;">관리</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div></div>`;
}

// ===== 상태 목록 =====
function renderStatusPanel(type) {
  const today = new Date(); today.setHours(0,0,0,0);
  let filtered = [];
  if (type==='inspect') {
    filtered = allItems.filter(x => {
      const d = x.lastInspectionDate ? new Date(x.lastInspectionDate) : null;
      return !d || new Date(d.getTime()+30*24*3600*1000)<=today;
    });
  } else if (type==='faulty') {
    filtered = allItems.filter(x => x.lastIsFaulty===true);
  }
  const body = document.getElementById('statusPanelBody');
  if (!filtered.length) {
    body.innerHTML='<div class="text-center text-muted py-3">해당 항목이 없습니다.</div>'; return;
  }
  let rows = filtered.map((x) => `
    <tr class="clickable-row" data-id="${x.hydrantId}">
      <td class="text-truncate" style="text-align:center;">${x.serialNumber||'-'}</td>
      <td class="text-truncate">${typeText(x.hydrantType)}</td>
      <td class="text-truncate">${opText(x.operationType)}</td>
      <td class="text-truncate">${x.buildingName||'-'}</td>
      <td class="text-truncate">${x.floorName||'-'}</td>
      <td class="text-truncate">${x.hydrantType==='Outdoor'
        ? (x.locationDescription||'-')
        : (x.x!=null&&x.y!=null ? `도면좌표 ${Number(x.x).toFixed(2)}%, ${Number(x.y).toFixed(2)}%` : '-')}</td>
      <td>${x.lastInspectionDate?x.lastInspectionDate.slice(0,10):'-'}</td>
      <td class="text-truncate">${x.lastInspectorName||'-'}</td>
      <td>${x.lastInspectionDate==null?'<span class="text-muted">-</span>':(x.lastIsFaulty===true?'<span class="fw-status fw-bad">비정상</span>':'<span class="fw-status fw-ok">정상</span>')}</td>
      <td class="text-truncate">${x.lastIsFaulty===true?(x.lastFaultReason||'-'):'-'}</td>
      <td class="text-center">
        <div class="hyd-actions">
          ${isAdmin?`<button type="button" class="btn btn-sm btn-fw-edit js-hydrant-edit" data-id="${x.hydrantId}">수정</button>`:''}
          <button type="button" class="btn btn-sm btn-fw-inspect js-hydrant-inspect" data-id="${x.hydrantId}">점검</button>
        </div>
      </td>
    </tr>`).join('');
  body.innerHTML=`
    <div class="fw-table-wrap"><div class="table-responsive">
      <table class="table table-hover mb-0 hyd-list-table" style="table-layout:fixed;">
        <thead class="table-dark"><tr>
          <th style="width:140px;text-align:center;">소화전 ID</th>
          <th style="width:90px;">구분</th>
          <th style="width:90px;">방식</th>
          <th style="width:140px;">건물</th>
          <th style="width:110px;">층</th>
          <th style="width:170px;">위치</th>
          <th style="width:110px;">최종 점검일</th>
          <th style="width:110px;">점검자</th>
          <th style="width:90px;">정상/비정상</th>
          <th style="width:220px;">고장 사유</th>
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
  const titleMap = { inspect:'점검필요 목록', faulty:'비정상 목록' };
  if (panelEl.classList.contains('show') && currentStatusType===type) {
    collapse.hide(); currentStatusType=null; return;
  }
  currentStatusType = type;
  const statusTitleEl = document.getElementById('statusPanelTitle');
  if (statusTitleEl) statusTitleEl.textContent = titleMap[type]||'상태 목록';
  renderStatusPanel(type);
  collapse.show();
}

// ===== 소화전상세 모달 (FireHydrants/_detailsModal.cshtml 기반) =====
function resolveDetailPlanImagePathHyd(buildingName, floorName){
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

async function fetchDetailPlanImagePathHyd(d){
  const isOutdoor = String(d?.hydrantType || '').toLowerCase() === 'outdoor'
    || String(d?.buildingName || '').includes('옥외')
    || String(d?.floorName || '').includes('옥외');
  if (isOutdoor) return '/images/drone_photo.JPG';
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
  return resolveDetailPlanImagePathHyd(d?.buildingName, d?.floorName);
}

function renderHydDetailPlanSingleMarker(wrapId, imgId, layerId, markerIcon, x, y){
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

async function openHydrantDetails(id) {
  const res = await API.req(`/fire-api/hydrants/${id}`);
  if (!res) return;
  const json = await res.json();
  if (!json.ok || !json.data) { alert('상세 정보를 불러오지 못했습니다.'); return; }
  const d = json.data;
  const planImagePath = await fetchDetailPlanImagePathHyd(d);
  const qrId = (d.serialNumber || '').trim();
  const qrUrl = qrId ? `/fire-api/qr/image?type=hyd&id=${encodeURIComponent(qrId)}` : '';

  let inspRows = '';
  if (d.inspections && d.inspections.length) {
    inspRows = d.inspections.slice(0,12).map(r=>`
      <tr>
        <td>${r.inspectionDate?r.inspectionDate.slice(0,10):'-'}</td>
        <td>${r.inspectorName||'-'}</td>
      </tr>`).join('');
  } else {
    inspRows='<tr><td colspan="2" class="text-muted text-center">점검 이력이 없습니다.</td></tr>';
  }

  const lastStatus = !d.lastInspectionDate
    ? '<span class="text-muted">-</span>'
    : (d.lastIsFaulty
       ? `<span class="fw-status fw-bad">비정상</span> <span class="text-muted">${d.lastFaultReason||'사유 없음'}</span>`
       : '<span class="fw-status fw-ok">정상</span>');

  const coordStr = (d.x!=null&&d.y!=null) ? `${Number(d.x).toFixed(2)}%, ${Number(d.y).toFixed(2)}%` : '-';
  const hydTypeLabel = d.hydrantType==='Outdoor'?'옥외':'옥내';
  const opLabel = d.operationType==='Auto'?'자동':'수동';

  document.getElementById('hydrantDetailsModalBody').innerHTML = `
    <div class="container-fluid">
      <div class="row g-4">
        <div class="col-md-6">
          <div class="card border-0 shadow-sm h-100">
            <div class="card-body">
              <h6 class="card-title text-primary mb-3">기본 정보</h6>
              <div class="info-row mb-3">
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">일련번호</span>
                  <strong>${d.serialNumber||'-'}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">구분</span>
                  <strong>${hydTypeLabel}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">작동방식</span>
                  <strong>${opLabel}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">건물</span>
                  <strong>${d.buildingName||'-'}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">층</span>
                  <strong>${d.floorName||'-'}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">좌표</span>
                  <strong>${coordStr}</strong>
                </div>
                <div class="d-flex align-items-center mb-2">
                  <span class="badge bg-light text-dark me-2" style="width:100px;">위치(상세)</span>
                  <strong>${d.locationDescription||'-'}</strong>
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
            </div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="card border-0 shadow-sm h-100">
            <div class="card-body d-flex flex-column" style="min-height:400px;gap:14px;">
              <div>
                <div class="fw-semibold mb-2">이미지</div>
                ${d.imagePath
                  ? `<div class="text-center"><img src="${d.imagePath}" alt="소화전이미지" class="img-fluid rounded shadow js-zoomable" style="max-width:100%;max-height:260px;object-fit:contain;" /><p class="text-muted mt-2 mb-0"><small>소화전사진</small></p></div>`
                  : '<div class="text-center text-muted"><div style="font-size:3rem;opacity:.3;">🚒</div><p class="mt-2 mb-0">등록된 이미지가 없습니다</p></div>'}
              </div>
              <div>
                <div class="fw-semibold mb-2">도면</div>
                ${planImagePath
                  ? `<div id="hydDetailPlanWrap" class="position-relative border rounded bg-light" style="height:260px;overflow:hidden;">
                       <img id="hydDetailPlanImg" src="${planImagePath}" alt="도면" class="js-zoomable" data-marker-x="${d.x ?? ''}" data-marker-y="${d.y ?? ''}" data-marker-icon="${String(d.hydrantType || '').toLowerCase() === 'outdoor' ? '/images/outdoorfirehydrant.PNG' : '/images/indoorfirehydrant.PNG'}" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);max-width:100%;max-height:100%;width:auto;height:auto;display:block;" />
                       <div id="hydDetailPlanMarkerLayer" style="position:absolute;inset:0;pointer-events:none;z-index:3;"></div>
                     </div>`
                  : '<div class="text-muted small border rounded p-3 bg-light">도면 정보가 없습니다.</div>'}
              </div>
              <div>
                <div class="fw-semibold mb-2">QR코드</div>
                ${qrUrl
                  ? `<div class="text-center"><img src="${qrUrl}" alt="소화전 QR" class="img-fluid rounded shadow js-zoomable" style="max-width:220px;max-height:220px;object-fit:contain;" /><p class="text-muted mt-2 mb-0"><small>${qrId}</small></p></div>`
                  : '<div class="text-muted small border rounded p-3 bg-light">QR 정보가 없습니다.</div>'}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <style>.info-row{font-size:.95rem;}.js-zoomable{cursor:zoom-in;}</style>`;

  bootstrap.Modal.getOrCreateInstance(document.getElementById('hydrantDetailsModal')).show();
  if (planImagePath) {
    const markerIcon = String(d.hydrantType || '').toLowerCase() === 'outdoor'
      ? '/images/outdoorfirehydrant.PNG'
      : '/images/indoorfirehydrant.PNG';
    renderHydDetailPlanSingleMarker('hydDetailPlanWrap', 'hydDetailPlanImg', 'hydDetailPlanMarkerLayer', markerIcon, d.x, d.y);
  }
}

window.openHydrantDetails = openHydrantDetails;

// ===== _HydrantInspectSimpleModalScripts.cshtml 기반 =====
(function(){
  if (window.__hydrantInspectModalInit) return;
  window.__hydrantInspectModalInit = true;

  const toast = (message) => {
    const body = document.getElementById('hydrantInspectToastBody');
    if (body) body.textContent = message || '점검이 완료되었습니다.';
    const el = document.getElementById('hydrantInspectToast');
    if (el) bootstrap.Toast.getOrCreateInstance(el, { delay: 2000 }).show();
  };

  const syncChecklistResult = () => {
    const selected = Array.from(document.querySelectorAll('.js-hyd-check-item:checked'));
    const badItems = selected.filter(x => x.value === 'bad').map(x => x.getAttribute('data-item') || '');
    const isBad = badItems.length > 0;
    const okEl = document.getElementById('hydrantInspectOk');
    const badEl = document.getElementById('hydrantInspectBad');
    if (okEl) okEl.checked = !isBad;
    if (badEl) badEl.checked = isBad;
    const wrap = document.getElementById('hydrantFaultReasonWrap');
    if (wrap) wrap.style.display = isBad ? '' : 'none';
    const t = document.getElementById('hydrantFaultReasonInput');
    if (t) t.value = isBad ? badItems.join(', ') : '';
    return { isBad, faultReason: (t?.value || '').trim() };
  };
  document.querySelectorAll('.js-hyd-check-item').forEach(el => {
    el.addEventListener('change', syncChecklistResult);
  });

  window.openHydrantInspectModal = (e, hydrantId) => {
    if (e) e.stopPropagation?.();
    const id = Number(hydrantId||0);
    if (!id) { alert('HydrantId가 올바르지 않습니다.'); return; }
    const hid = document.getElementById('hydrantInspectId');
    if (hid) hid.value = String(id);
    document.querySelectorAll('.js-hyd-check-item[value=\"ok\"]').forEach(el => { el.checked = true; });
    document.querySelectorAll('.js-hyd-check-item[value=\"bad\"]').forEach(el => { el.checked = false; });
    syncChecklistResult();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('hydrantInspectModal')).show();
  };

  document.getElementById('hydrantInspectConfirmBtn')?.addEventListener('click', async () => {
    const id = Number(document.getElementById('hydrantInspectId')?.value||0);
    if (!id) return;
    const today = new Date().toISOString().slice(0,10);
    const picked = (allItems || []).find(x => Number(x.hydrantId) === id);
    const last = picked?.lastInspectionDate ? String(picked.lastInspectionDate).slice(0,10) : '';
    if (last === today) {
      alert('오늘 이미 점검 완료된 소화전입니다.');
      return;
    }
    const check = syncChecklistResult();
    const isFaulty = check.isBad;
    const faultReason = check.faultReason;
    if (isFaulty && !faultReason) {
      alert('비정상 체크 항목을 확인해 주세요.');
      return;
    }
    const btn = document.getElementById('hydrantInspectConfirmBtn');
    if (btn) btn.disabled=true;
    try {
      // 소화전 점검 API: POST /fire-api/hydrants/{id}/inspect?isFaulty=...&faultReason=...
      const params = new URLSearchParams({ isFaulty: String(isFaulty) });
      if (faultReason) params.append('faultReason', faultReason);
      const r = await API.req(`/fire-api/hydrants/${id}/inspect?${params}`, { method:'POST' });
      const text = await r?.text().catch(()=>'');
      let data=null;
      try { data = text ? JSON.parse(text) : null; } catch{}
      if (!r||!r.ok) { alert((data&&data.message)?data.message:(text||`점검 실패(${r?.status})`)); return; }
      if (data&&data.ok===false) { alert(data.message||'점검 실패'); return; }
      bootstrap.Modal.getOrCreateInstance(document.getElementById('hydrantInspectModal')).hide();
      toast('점검이 완료되었습니다.');
      document.dispatchEvent(new CustomEvent('hydrant:inspectionCompleted',{detail:{hydrantId:id}}));
      const qs = new URLSearchParams(window.location.search);
      if (qs.get('embedInspect') === '1') {
        try { window.parent?.postMessage('fireweb:hydrant-inspect-saved', '*'); } catch {}
      }
    } catch(err) {
      alert('점검 실패: '+(err?.message||err));
    } finally {
      if (btn) btn.disabled=false;
    }
  });
})();

// ===== _HydrantUpsertModalScripts.cshtml 기반 =====
(function(){
  const modalEl  = document.getElementById('hydUpsertModal');
  const saveBtn  = document.getElementById('hydSaveBtn');
  if (!modalEl||!saveBtn) return;

  const typeEl     = document.getElementById('hydType');
  const buildingSel= document.getElementById('hydBuildingSel');
  const floorSel   = document.getElementById('hydFloorSel');
  const mapCanvas  = document.getElementById('hydMapCanvas');
  const mapSection = document.getElementById('hydMapSection');
  const planImg    = document.getElementById('hydPlanImg');
  const markerLayer= document.getElementById('hydMarkerLayer');
  const mapStatus  = document.getElementById('hydMapStatus');
  const coordX     = document.getElementById('hydCoordX');
  const coordY     = document.getElementById('hydCoordY');
  const inspectDateEl = document.getElementById('hydInspectionDate');
  const inspectOkEl = document.getElementById('hydInspectOk');
  const inspectBadEl = document.getElementById('hydInspectBad');
  const faultWrapEl = document.getElementById('hydFaultReasonWrapInEdit');
  const faultInputEl = document.getElementById('hydFaultReasonInEdit');

  const ICON_EXT      = '/images/Extinguisher.PNG';
  const ICON_HYD_IN   = '/images/indoorfirehydrant.PNG';
  const ICON_HYD_OUT  = '/images/outdoorfirehydrant.PNG';

  let existingExt=[], existingHyd=[], selected=null, pendingSelected=null;
  let panX=0, panY=0;
  let currentHideMap = false;
  let loadedLastInspectionDate = '';
  let isDragging=false, dragStartX=0, dragStartY=0, dragPanX=0, dragPanY=0, movedWhileDrag=false;
  let openOpts={};

  function setVal(id,v){ const el=document.getElementById(id); if(el) el.value=(v??'').toString(); }
  function syncInspectUI(){
    const isBad = inspectBadEl?.checked === true;
    if (faultWrapEl) faultWrapEl.style.display = isBad ? '' : 'none';
    if (!isBad && faultInputEl) faultInputEl.value = '';
  }
  function setStatus(msg, isError){
    if (!mapStatus) return;
    mapStatus.textContent=msg||'';
    mapStatus.classList.toggle('text-danger', !!msg&&isError!==false);
    mapStatus.classList.toggle('text-muted', !!msg&&isError===false);
  }

  function computeFit(){
    const rect=mapCanvas?.getBoundingClientRect?.();
    const cw=rect?.width||0, ch=rect?.height||0;
    const iw=planImg?.naturalWidth||0, ih=planImg?.naturalHeight||0;
    if(!cw||!ch||!iw||!ih) return null;
    const scale=Math.min(cw/iw, ch/ih);
    const w=iw*scale, h=ih*scale;
    const ox=(cw-w)/2, oy=(ch-h)/2;
    return {ox,oy,w,h};
  }

  function toPx(x,y,f){ f=f||computeFit(); if(!f) return null;
    return {left:f.ox+f.w*(x/100), top:f.oy+f.h*(y/100)}; }

  function buildMarker(x,y,icon,title,sel,f,kind){
    const p=toPx(x,y,f); if(!p) return null;
    const iw=planImg?.naturalWidth||f.w||1;
    const ratio=(f.w||iw)/iw;
    const base=kind==='ext'?35:34;
    const size=Math.max(8,Math.round(base*ratio*(sel?1.22:1)*10)/10);
    const el=document.createElement('button');
    el.type='button';
    el.style.cssText=`position:absolute;left:${p.left}px;top:${p.top}px;width:${size}px;height:${size}px;transform:translate(-50%,-50%);border:0;background:transparent;padding:0;pointer-events:auto;`;
    el.style.zIndex = sel ? '25' : '10';
    el.style.filter=sel
      ? 'drop-shadow(0 0 10px rgba(255,0,0,1)) drop-shadow(0 0 18px rgba(255,215,0,.95))'
      : 'drop-shadow(0 0 5px rgba(0,0,0,.55))';
    el.title=title||'';
    el.innerHTML=`<img src="${icon}" alt="" draggable="false" style="width:100%;height:100%;object-fit:contain;pointer-events:none;${sel?'animation:hydMarkerPulse 1.15s ease-in-out infinite;':''}">` +
      (sel ? '<span style="position:absolute;left:50%;top:50%;width:145%;height:145%;transform:translate(-50%,-50%);border:3px solid #ffd60a;border-radius:999px;box-shadow:0 0 0 2px #101010,0 0 16px rgba(255,214,10,.95);animation:hydRingPulse 1.2s ease-out infinite;pointer-events:none;"></span>' : '');
    return el;
  }

  function layoutPlan(f){
    if(!planImg||!f) return;
    planImg.style.cssText=`position:absolute;left:${f.ox}px;top:${f.oy}px;width:${f.w}px;height:${f.h}px;transform:none;object-fit:contain;`;
  }

  function renderMarkers(){
    if(!markerLayer) return;
    markerLayer.innerHTML='';
    const f=computeFit(); if(!f) return;
    layoutPlan(f);
    existingExt.forEach(v=>{
      if(v.x==null||v.y==null) return;
      const m=buildMarker(Number(v.x),Number(v.y),ICON_EXT,`소화기 ${v.extinguisherId}`,false,f,'ext');
      if(m) markerLayer.appendChild(m);
    });
    const editingId = Number(document.getElementById('hydId')?.value || '0');
    existingHyd.forEach(v=>{
      if(v.x==null||v.y==null) return;
      const isEditingMarker = editingId > 0 && Number(v.hydrantId) === editingId;
      const mx = isEditingMarker && selected ? selected.x : Number(v.x);
      const my = isEditingMarker && selected ? selected.y : Number(v.y);
      const icon=String(v.hydrantType||'').toLowerCase()==='outdoor'?ICON_HYD_OUT:ICON_HYD_IN;
      const m=buildMarker(mx,my,icon,`소화전 ${v.hydrantId}`,isEditingMarker,f,'hyd');
      if(m) markerLayer.appendChild(m);
    });
    if(selected && editingId<=0){
      const selIcon=(typeEl?.value||'Indoor')==='Outdoor'?ICON_HYD_OUT:ICON_HYD_IN;
      const s=buildMarker(selected.x,selected.y,selIcon,'선택 위치',true,f,'hyd');
      if(s) markerLayer.appendChild(s);
    }
  }

  function setCoord(x,y){
    const rx=Number(x).toFixed(2), ry=Number(y).toFixed(2);
    if(coordX) coordX.textContent=rx;
    if(coordY) coordY.textContent=ry;
    setVal('hydX',rx); setVal('hydY',ry);
    selected={x:Number(rx),y:Number(ry)};
    const editingId = Number(document.getElementById('hydId')?.value || '0');
    if (editingId > 0) {
      const idx = existingHyd.findIndex(v => Number(v.hydrantId) === editingId);
      if (idx >= 0) {
        existingHyd[idx].x = selected.x;
        existingHyd[idx].y = selected.y;
      }
    }
    renderMarkers();
  }
  function clearCoord(){
    if(coordX) coordX.textContent='-';
    if(coordY) coordY.textContent='-';
    setVal('hydX',''); setVal('hydY','');
    selected=null; renderMarkers();
  }

  function syncTypeUI(){
    const t=typeEl?.value||'Indoor';
    const indoor=t==='Indoor';
    if(buildingSel) buildingSel.disabled=!indoor;
    if(floorSel) floorSel.disabled=!indoor;
    if(!indoor){ setVal('hydBuildingId',99); setVal('hydFloorId',1); }
  }

  function normName(v){ return String(v||'').replace(/\s+/g,'').toLowerCase(); }
  function selectOptionSmart(sel, id, name){
    if (!sel) return;
    const sid = String(id ?? '').trim();
    if (sid) {
      const byValue = Array.from(sel.options || []).find(o => String(o.value).trim() === sid);
      if (byValue) { sel.value = sid; return; }
    }
    const n = normName(name);
    if (n) {
      const byText = Array.from(sel.options || []).find(o => normName(o.textContent) === n);
      if (byText) { sel.value = byText.value; return; }
      const byContains = Array.from(sel.options || []).find(o => normName(o.textContent).includes(n) || n.includes(normName(o.textContent)));
      if (byContains) { sel.value = byContains.value; return; }
    }
  }
  function resolvePlanImagePathByName(buildingName, floorName){
    const b = normName(buildingName).replace(/[.,_-]/g, '');
    const f = normName(floorName);
    if (b.includes('복지관') || b.includes('bokji')) {
      if (f.includes('지하') || f.includes('b1')) return '/images/bokji_B1.png';
      if (f.includes('1')) return '/images/bokji_1F.png';
      if (f.includes('2')) return '/images/bokji_2F.png';
      if (f.includes('3')) return '/images/bokji_3F.png';
    }
    if ((b.includes('관리동') || b.includes('gwanri')) && f.includes('1')) return '/images/gwanri_1F.png';
    if ((b.includes('제지12호기') || b.includes('jeji12'))) {
      if (f.includes('1')) return '/images/jeji1,2_1F.PNG';
      if (f.includes('2')) return '/images/jeji1,2_2F.PNG';
    }
    if ((b.includes('제지3호기') || b.includes('jeji3'))) {
      if (f.includes('1')) return '/images/jeji3_1F.PNG';
      if (f.includes('2')) return '/images/jeji3_2F.PNG';
    }
    if (b.includes('패드동') || b.includes('pad')) {
      if (f.includes('1')) return '/images/pad_1F.PNG';
      if (f.includes('2')) return '/images/pad_2F.PNG';
    }
    if (b.includes('심면펄퍼') || b.includes('palpa') || b.includes('pulper')) {
      if (f.includes('1')) return '/images/palpa_1F.PNG';
      if (f.includes('2')) return '/images/palpa_2F.PNG';
    }
    if (b.includes('화장지36호기') || b.includes('tissue13') || b.includes('tissue36')) {
      if (f.includes('1')) return '/images/tissue1,3_1F.PNG';
      if (f.includes('2')) return '/images/tissue1,3_2F.PNG';
    }
    if (b.includes('화장지45호기') || b.includes('tissue45')) {
      if (f.includes('지하') || f.includes('b1')) return '/images/tissue4,5_B1.PNG';
      if (f.includes('1')) return '/images/tissue4,5_1F.PNG';
      if (f.includes('2')) return '/images/tissue4,5_2F.PNG';
      if (f.includes('3')) return '/images/tissue4,5_3F.PNG';
    }
    if ((b.includes('기저귀동') || b.includes('diaper')) && f.includes('1')) return '/images/diaper_1F.png';
    if (b.includes('옥외') || b.includes('outdoor')) return '/images/drone_photo.JPG';
    return '';
  }

  async function loadMapData(forceBuildingId, forceFloorId){
    if (currentHideMap) return;
    const t=typeEl?.value||'Indoor';
    syncTypeUI(); clearCoord(); existingExt=[]; existingHyd=[]; panX=0; panY=0;
    if(markerLayer) markerLayer.innerHTML='';

    if(t==='Indoor'){
      const b=String(forceBuildingId ?? (buildingSel?.value||'')).trim();
      const f=String(forceFloorId ?? (floorSel?.value||'')).trim();
      if(!b||!f){
        if(planImg) planImg.removeAttribute('src');
        setStatus('옥내는 건물/층 선택이 필요합니다.',false); return;
      }
      if (buildingSel) buildingSel.value = String(b);
      if (floorSel) floorSel.value = String(f);
      setVal('hydBuildingId',b); setVal('hydFloorId',f);
    }

    setStatus('도면 로딩 중...',false);
    try {
      const b=t==='Indoor'?String(forceBuildingId ?? (buildingSel?.value||'')).trim():null;
      const f=t==='Indoor'?String(forceFloorId ?? (floorSel?.value||'')).trim():null;
      let mapUrl = '';
      if (t==='Indoor' && b && f) {
        const pr = new URLSearchParams({buildingId:b, floorId:f});
        const r = await API.req(`/fire-api/maps/floor-data?${pr}`);
        if (r && r.ok) {
          const data = await r.json().catch(()=>null);
          if (data && data.ok && data.data) {
            mapUrl = data.data.planImagePath||'';
            existingExt = Array.isArray(data.data.extinguishers) ? data.data.extinguishers : [];
            existingHyd = Array.isArray(data.data.hydrants) ? data.data.hydrants : [];
          }
        }
      } else if (t==='Outdoor') {
        mapUrl = '/images/drone_photo.JPG';
        const hr = await API.req('/fire-api/hydrants?size=500&page=0');
        if (hr && hr.ok) {
          const hj = await hr.json().catch(()=>null);
          const content = Array.isArray(hj?.data?.content) ? hj.data.content : [];
          existingHyd = content.filter(x => x && x.hydrantType === 'Outdoor' && x.x != null && x.y != null);
        }
        existingExt = [];
      }
      if (!mapUrl && t==='Indoor' && b && f) {
        const bName = (buildingSel?.selectedOptions?.[0]?.textContent || '').trim();
        const fName = (floorSel?.selectedOptions?.[0]?.textContent || '').trim();
        mapUrl = resolvePlanImagePathByName(bName, fName);
        if (mapUrl) {
          const hr2 = await API.req(`/fire-api/hydrants?size=500&page=0&buildingId=${b}&floorId=${f}`);
          if (hr2 && hr2.ok) {
            const hj2 = await hr2.json().catch(()=>null);
            existingHyd = Array.isArray(hj2?.data?.content) ? hj2.data.content : [];
          }
          const er2 = await API.req(`/fire-api/extinguishers?size=500&page=0&buildingId=${b}&floorId=${f}`);
          if (er2 && er2.ok) {
            const ej2 = await er2.json().catch(()=>null);
            existingExt = Array.isArray(ej2?.data?.content) ? ej2.data.content : [];
          }
        }
      }
      if (!mapUrl) {
        if(planImg) planImg.removeAttribute('src');
        setStatus('도면 이미지를 불러올 수 없습니다.',true); return;
      }
      planImg.onerror=()=>setStatus('도면 이미지 로드 실패',true);
      planImg.onload=()=>{
        setStatus('',false);
        renderMarkers();
        if(pendingSelected){
          setCoord(pendingSelected.x, pendingSelected.y);
          pendingSelected=null;
        }
      };
      planImg.src=`${mapUrl}?v=${Date.now()}`;
    } catch(_){
      setStatus('도면 조회 중 오류가 발생했습니다.',true);
    }
  }

  async function loadHyd(id){
    const r=await API.req(`/fire-api/hydrants/${id}`);
    if(!r||!r.ok) throw new Error('소화전 로드 실패');
    const j=await r.json();
    return j.data;
  }

  window.openHydrantUpsertModal = async function(opts){
    openOpts=opts||{};
    const id=Number(opts?.id||0);
    const qs = new URLSearchParams(window.location.search);
    currentHideMap = opts?.hideMap === true || qs.get('noMap') === '1';
    if (mapSection) mapSection.style.display = currentHideMap ? 'none' : '';
    await ensureHydModalOptions();
    document.getElementById('hydUpsertTitle').textContent=id?'소화전 편집':'소화전 추가';
    setStatus('',false);
    setVal('hydId',id);
    setVal('hydBuildingId',opts?.buildingId??'');
    setVal('hydFloorId',opts?.floorId??'');
    setVal('hydX',opts?.x??'');
    setVal('hydY',opts?.y??'');
    if(typeEl) typeEl.disabled=id>0;

    if(!id){
      const t=opts?.hydrantType||'Indoor';
      setVal('hydType',t); setVal('hydTypeHidden',t);
      if (buildingSel) {
        selectOptionSmart(buildingSel, opts?.buildingId, opts?.buildingName);
      }
      if (floorSel) {
        selectOptionSmart(floorSel, opts?.floorId, opts?.floorName);
      }
      if (inspectDateEl) inspectDateEl.value = '';
      loadedLastInspectionDate = '';
      if (inspectOkEl) inspectOkEl.checked = true;
      if (faultInputEl) faultInputEl.value = '';
      syncInspectUI();
      const qx=parseFloat(String(opts?.x??'')), qy=parseFloat(String(opts?.y??''));
      pendingSelected=(Number.isFinite(qx)&&Number.isFinite(qy))?{x:qx,y:qy}:null;
      const fileEl=document.getElementById('hydPhoto'); if(fileEl) fileEl.value='';
      const selB = buildingSel?.value || opts?.buildingId;
      const selF = floorSel?.value || opts?.floorId;
      if (!currentHideMap) await loadMapData(selB, selF);
      bootstrap.Modal.getOrCreateInstance(modalEl).show(); return;
    }
    try{
      const data=await loadHyd(id);
      await ensureHydModalOptions();
      setVal('hydBuildingId',data.buildingId??'');
      setVal('hydFloorId',data.floorId??'');
      setVal('hydType',data.hydrantType||'Indoor');
      setVal('hydTypeHidden',data.hydrantType||'Indoor');
      if(typeEl) typeEl.value=data.hydrantType||'Indoor';
      document.getElementById('hydOp').value=data.operationType||'Manual';
      document.getElementById('hydLocation').value=data.locationDescription??'';
      if (inspectDateEl) inspectDateEl.value = data.lastInspectionDate ? String(data.lastInspectionDate).slice(0,10) : '';
      loadedLastInspectionDate = data.lastInspectionDate ? String(data.lastInspectionDate).slice(0,10) : '';
      if (data.lastIsFaulty) {
        if (inspectBadEl) inspectBadEl.checked = true;
        if (faultInputEl) faultInputEl.value = data.lastFaultReason || '';
      } else {
        if (inspectOkEl) inspectOkEl.checked = true;
        if (faultInputEl) faultInputEl.value = '';
      }
      syncInspectUI();
      if(buildingSel) {
        selectOptionSmart(buildingSel, data.buildingId, data.buildingName);
      }
      if(floorSel) {
        selectOptionSmart(floorSel, data.floorId, data.floorName);
      }
      const qx=parseFloat(String(data.x??'')), qy=parseFloat(String(data.y??''));
      pendingSelected=(Number.isFinite(qx)&&Number.isFinite(qy))?{x:qx,y:qy}:null;
      const fileEl=document.getElementById('hydPhoto'); if(fileEl) fileEl.value='';
      if (!currentHideMap) await loadMapData(data.buildingId, data.floorId);
      bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }catch(err){
      alert('소화전 로드 실패: '+(err?.message||err));
    }
  };

  typeEl?.addEventListener('change', async()=>{ setVal('hydTypeHidden',typeEl.value||'Indoor'); await loadMapData(); });
  buildingSel?.addEventListener('change', loadMapData);
  floorSel?.addEventListener('change', loadMapData);
  inspectOkEl?.addEventListener('change', syncInspectUI);
  inspectBadEl?.addEventListener('change', syncInspectUI);
  window.addEventListener('resize', renderMarkers);
  modalEl.addEventListener('shown.bs.modal', renderMarkers);

  document.getElementById('hydZoomIn')?.addEventListener('click',()=>{ renderMarkers(); });
  document.getElementById('hydZoomOut')?.addEventListener('click',()=>{ renderMarkers(); });
  document.getElementById('hydZoomReset')?.addEventListener('click',()=>{ renderMarkers(); });

  mapCanvas?.addEventListener('click',(e)=>{
    if(movedWhileDrag){movedWhileDrag=false;return;}
    const rect=mapCanvas.getBoundingClientRect();
    const px=e.clientX-rect.left, py=e.clientY-rect.top;
    const fit=computeFit(); if(!fit) return;
    if(px<fit.ox||px>fit.ox+fit.w||py<fit.oy||py>fit.oy+fit.h) return;
    const x=((px-fit.ox)/fit.w)*100, y=((py-fit.oy)/fit.h)*100;
    setCoord(x,y);
  });
  mapCanvas?.addEventListener('mousedown',(e)=>{
    if(e.button!==0) return;
    isDragging=true; movedWhileDrag=false;
    dragStartX=e.clientX; dragStartY=e.clientY;
    dragPanX=panX; dragPanY=panY;
    document.body.style.userSelect='none';
    if(mapCanvas) mapCanvas.style.cursor='grabbing';
  });
  window.addEventListener('mousemove',(e)=>{
    if(!isDragging) return;
    const dx=e.clientX-dragStartX, dy=e.clientY-dragStartY;
    if(Math.abs(dx)>2||Math.abs(dy)>2) movedWhileDrag=true;
    panX=dragPanX+dx; panY=dragPanY+dy; renderMarkers();
  });
  window.addEventListener('mouseup',()=>{
    if(!isDragging) return;
    isDragging=false; document.body.style.userSelect='';
    if(mapCanvas) mapCanvas.style.cursor='grab';
  });
  mapCanvas?.addEventListener('dragstart',(e)=>e.preventDefault());
  if(mapCanvas) mapCanvas.style.cursor='grab';

  saveBtn.addEventListener('click', async function(){
    saveBtn.disabled=true;
    try{
      const typeVal=typeEl?.value||'Indoor';
      setVal('hydTypeHidden',typeVal);
      const x=parseFloat(document.getElementById('hydX')?.value||'');
      const y=parseFloat(document.getElementById('hydY')?.value||'');
      if(!currentHideMap && (!Number.isFinite(x)||!Number.isFinite(y))){ alert('도면에서 위치를 선택하세요.'); return; }
      if(typeVal==='Indoor'){
        const b=parseInt(buildingSel?.value||'0',10);
        const f=parseInt(floorSel?.value||'0',10);
        if(!b||!f){ alert('옥내 소화전은 건물/층을 선택해야 합니다.'); return; }
        setVal('hydBuildingId',b); setVal('hydFloorId',f);
      } else {
        setVal('hydBuildingId',99); setVal('hydFloorId',1);
      }
      const id=Number(document.getElementById('hydId')?.value||0);
      const body={
        hydrantId: id||undefined,
        hydrantType: typeVal,
        buildingId: parseInt(document.getElementById('hydBuildingId')?.value||'0'),
        floorId: parseInt(document.getElementById('hydFloorId')?.value||'0'),
        operationType: document.getElementById('hydOp')?.value||'Manual',
        x: Number.isFinite(x) ? x : null,
        y: Number.isFinite(y) ? y : null,
        locationDescription: document.getElementById('hydLocation')?.value||''
      };
      const r=await API.req('/fire-api/hydrants',{method:'POST',body});
      const t=await r?.text().catch(()=>'');
      const ct=r?.headers.get('content-type')||'';
      let data=null;
      if(ct.includes('application/json')){ try{data=t?JSON.parse(t):null;}catch{} }
      if(!r||!r.ok){ alert('저장 실패: '+(data?.message||t||`HTTP ${r?.status}`)); return; }
      if(!data){ alert('저장 실패: 응답 파싱 실패'); return; }
      if(data.ok===false){ alert('저장 실패: '+(data.message||'알 수 없는 오류')); return; }

      const savedId = Number(data?.data?.hydrantId || body.hydrantId || 0);
      const inspectionDate = inspectDateEl?.value || '';
      const inspectIsFaulty = inspectBadEl?.checked === true;
      const faultReason = (faultInputEl?.value || '').trim();
      if (inspectIsFaulty && !faultReason) { alert('비정상인 경우 고장 사유를 입력해 주세요.'); return; }
      if (savedId > 0 && inspectionDate && inspectionDate !== loadedLastInspectionDate) {
        const params = new URLSearchParams({ isFaulty: String(inspectIsFaulty) });
        if (faultReason) params.append('faultReason', faultReason);
        const ir = await API.req(`/fire-api/hydrants/${savedId}/inspect?${params}`, { method:'POST' });
        if (!ir || !ir.ok) {
          const it = await ir?.text().catch(()=>'');
          alert('점검 저장 실패: ' + (it || ir?.status));
          return;
        }
      }
      bootstrap.Modal.getInstance(modalEl)?.hide();
      document.dispatchEvent(new CustomEvent('hydrant:upsertCompleted',{detail:{id:data.data?.hydrantId, opts:openOpts}}));
    } finally {
      saveBtn.disabled=false;
    }
  });
})();

// ===== 이벤트 위임 =====
document.addEventListener('click', async(ev)=>{
  const editBtn = ev.target.closest('.js-hydrant-edit');
  if(editBtn){
    ev.preventDefault();
    const id=parseInt(editBtn.getAttribute('data-id')||'0',10);
    if(id>0) window.openHydrantUpsertModal?.({id});
    return;
  }
  const inspBtn = ev.target.closest('.js-hydrant-inspect');
  if(inspBtn){
    ev.preventDefault();
    const id=parseInt(inspBtn.getAttribute('data-id')||'0',10);
    if(id>0) window.openHydrantInspectModal?.(null,id);
    return;
  }
  const row = ev.target.closest('tr.clickable-row[data-id]');
  if(row){
    if(ev.target.closest('a,button')) return;
    const id=parseInt(row.getAttribute('data-id')||'0',10);
    if(id>0) openHydrantDetails(id);
    return;
  }
  const zoomable = ev.target.closest('.js-zoomable');
  if(zoomable){
    const src=zoomable.getAttribute('data-zoom-src')||zoomable.getAttribute('src');
    if(src){
      const target = document.getElementById('hydrantDetailImageZoomTarget');
      const layer = document.getElementById('hydrantDetailImageZoomMarkerLayer');
      const mx = parseFloat(zoomable.getAttribute('data-marker-x') || '');
      const my = parseFloat(zoomable.getAttribute('data-marker-y') || '');
      const icon = zoomable.getAttribute('data-marker-icon') || '/images/indoorfirehydrant.PNG';
      const drawZoomMarker = () => {
        if (!layer) return;
        layer.innerHTML = '';
        if (!Number.isFinite(mx) || !Number.isFinite(my)) return;
        const stage = document.getElementById('hydrantDetailImageZoomStage');
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
      bootstrap.Modal.getOrCreateInstance(document.getElementById('hydrantDetailImageZoomModal')).show();
      setTimeout(drawZoomMarker, 0);
    }
  }
});

// ===== 버튼 이벤트 =====
document.getElementById('btnSearch')?.addEventListener('click',()=>{
  loadHydrants(document.getElementById('filterQ')?.value||'',document.getElementById('filterBuildingId')?.value||'',document.getElementById('filterFloorId')?.value||'');
});
document.getElementById('filterQ')?.addEventListener('keydown',(e)=>{ if(e.key==='Enter') document.getElementById('btnSearch')?.click(); });
document.getElementById('btnReset')?.addEventListener('click',()=>{
  document.getElementById('filterQ').value='';
  document.getElementById('filterBuildingId').value='';
  document.getElementById('filterFloorId').value='';
  loadHydrants();
});
document.getElementById('btnStatusInspect')?.addEventListener('click',()=>openStatus('inspect'));
document.getElementById('btnStatusFaulty')?.addEventListener('click',()=>openStatus('faulty'));

// 점검/저장 완료 후 갱신
const reload = () => loadHydrants(
  document.getElementById('filterQ')?.value||'',
  document.getElementById('filterBuildingId')?.value||'',
  document.getElementById('filterFloorId')?.value||'');
document.addEventListener('hydrant:upsertCompleted', reload);
document.addEventListener('hydrant:inspectionCompleted', reload);

// ===== 초기화 =====
(async function init(){
  const qs = new URLSearchParams(window.location.search);
  const embedEditMode = qs.get('embedEdit') === '1';
  const embedInspectMode = qs.get('embedInspect') === '1';
  const embedDetailsMode = qs.get('embedDetails') === '1';

  const token = localStorage.getItem('fireweb_token');
  if(!token){ window.location.href='/login.html'; return; }
  window.FireWebNav?.mount?.();

  if (embedEditMode || embedInspectMode || embedDetailsMode) {
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
      '#hydUpsertModal .modal-header { background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color:#fff; border-bottom:0; }',
      '#hydUpsertModal .modal-title { color:#fff; font-weight:700; }',
      '#hydUpsertModal .btn-close { filter: invert(1) grayscale(100%) brightness(200%); }'
    ].join('\n');
    document.head.appendChild(embedStyle);
  }

  if(isAdmin){
    const addBtn=document.getElementById('btnHydAdd');
    if(addBtn) addBtn.style.display='';
  }
  await loadHydrants();
  const inspectId = parseInt(qs.get('inspect') || '0', 10);
  if(inspectId > 0) {
    window.openHydrantInspectModal?.(null, inspectId);
    return;
  }
  const detailsId = parseInt(qs.get('details') || '0', 10);
  if (detailsId > 0) {
    openHydrantDetails(detailsId);
    return;
  }
  const editId = qs.get('edit');
  if(editId && isAdmin) {
    window.openHydrantUpsertModal?.({ id: parseInt(editId,10) });
    return;
  }
  if (isAdmin && qs.get('add') === '1') {
    const bIdQ = qs.get('buildingId') || '';
    const fIdQ = qs.get('floorId') || '';
    const bName = qs.get('buildingName') || '';
    const fName = qs.get('floorName') || '';
    const addType = ((qs.get('hydrantType') || '').toLowerCase() === 'outdoor') ? 'Outdoor' : 'Indoor';
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
        String(it.buildingName || '').replace(/\s+/g,'').toLowerCase() === String(bName).replace(/\s+/g,'').toLowerCase() &&
        String(it.floorName || '').replace(/\s+/g,'').toLowerCase() === String(fName).replace(/\s+/g,'').toLowerCase()
      );
    }
    window.openHydrantUpsertModal?.({
      id: 0,
      hydrantType: addType,
      operationType: 'Manual',
      buildingId: matched?.buildingId || bIdQ || '',
      floorId: matched?.floorId || fIdQ || '',
      buildingName: matched?.buildingName || bName || '',
      floorName: matched?.floorName || fName || '',
      x: Number.isFinite(x) ? x : '',
      y: Number.isFinite(y) ? y : ''
    });
  }
})();

document.getElementById('hydUpsertModal')?.addEventListener('hidden.bs.modal', () => {
  const qs = new URLSearchParams(window.location.search);
  if (qs.get('embedEdit') === '1') {
    try { window.parent?.postMessage('fireweb:outdoor-edit-close', '*'); } catch {}
  }
});

document.getElementById('hydrantInspectModal')?.addEventListener('hidden.bs.modal', () => {
  const qs = new URLSearchParams(window.location.search);
  if (qs.get('embedInspect') === '1') {
    try { window.parent?.postMessage('fireweb:hydrant-inspect-close', '*'); } catch {}
  }
});

document.getElementById('hydrantDetailsModal')?.addEventListener('hidden.bs.modal', () => {
  const qs = new URLSearchParams(window.location.search);
  if (qs.get('embedDetails') === '1') {
    try { window.parent?.postMessage('fireweb:hydrant-details-close', '*'); } catch {}
  }
});

document.getElementById('hydrantDetailImageZoomTarget')?.addEventListener('click', () => {
  const m = document.getElementById('hydrantDetailImageZoomModal');
  if (m) bootstrap.Modal.getOrCreateInstance(m).hide();
});
