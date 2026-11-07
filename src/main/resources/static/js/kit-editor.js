(function () {
    'use strict';

    // Kit editor page: one section per component (inverter, controller, batteries, panels, protections,
    // cables, terminals), a live compatibility review under each one, and a summary column with the
    // cost, the review's messages and the save buttons. A kit is still saved as a flat list of
    // products, so this page only changes how it is edited.
    const Common = window.AdminCommon;
    if (!Common) {
        return; // no admin session: admin-common.js already sent the visitor to the login page
    }
    const { authFetch, errorMessage, escapeHtml, escapeAttr, photoManagerHtml, photoManagerValue, photoUploadInProgress,
        initPhotoManager, WIRE_GAUGE_LABELS } = Common;

    const kitIdAttr = document.body.dataset.kitId;
    let kitId = kitIdAttr ? Number(kitIdAttr) : null;

    const alertEl = document.getElementById('editor-alert');
    const loadingEl = document.getElementById('editor-loading');
    const rootEl = document.getElementById('editor-root');
    const sectionsEl = document.getElementById('editor-sections');
    const sideEl = document.getElementById('editor-side');
    const jumpNav = document.getElementById('jump-nav');
    const titleEl = document.getElementById('editor-title');

    // ---- What each kind of product is ----

    const KINDS = {
        inverter: { endpoint: '/api/catalog/inverters', single: true },
        controller: { endpoint: '/api/catalog/charge-controllers', single: true },
        battery: { endpoint: '/api/catalog/batteries', single: true, quantity: true },
        panel: { endpoint: '/api/catalog/panels', single: true, quantity: true },
        protection: { endpoint: '/api/catalog/protections' },
        cable: { endpoint: '/api/catalog/cables' },
        terminal: { endpoint: '/api/catalog/terminals' },
        structure: { endpoint: '/api/catalog/structures' }
    };
    const KIND_NAMES = {
        inverter: 'inversores', controller: 'controladores', battery: 'baterías', panel: 'paneles',
        protection: 'protecciones', cable: 'cables', terminal: 'terminales', structure: 'estructuras'
    };
    const ADD_LABELS = { protection: 'Agregar protección', cable: 'Agregar cable', terminal: 'Agregar terminal', structure: 'Agregar producto' };
    const CATALOG_TABS = { protection: 'protecciones', cable: 'cables', terminal: 'terminales' };

    // `review` is the part of the kit the review's messages are tagged with (KitSection on the server).
    const SECTIONS = [
        { key: 'general', num: 1, title: 'Datos del kit' },
        { key: 'inverter', num: 2, title: 'Inversor', review: 'INVERTER', blank: 'Sin inversor' },
        { key: 'controller', num: 3, title: 'Controlador de carga', review: 'CONTROLLER', blank: 'Sin controlador (el inversor trae su MPPT)' },
        { key: 'battery', num: 4, title: 'Baterías', review: 'BATTERIES', blank: 'Sin baterías' },
        { key: 'panel', num: 5, title: 'Paneles', review: 'PANELS', blank: 'Sin paneles' },
        { key: 'protection', num: 6, title: 'Protecciones', review: 'PROTECTIONS' },
        { key: 'cable', num: 7, title: 'Cables', review: 'CABLES' },
        { key: 'terminal', num: 8, title: 'Terminales', review: 'TERMINALS' },
        { key: 'structure', num: 9, title: 'Estructura y otros' }
    ];
    const SECTION_BY_REVIEW = Object.fromEntries(SECTIONS.filter((s) => s.review).map((s) => [s.review, s]));

    const CHEMISTRY = { LIFEPO4: 'LiFePO4', LITHIUM_ION: 'Litio-ion', LEAD_ACID: 'Plomo-ácido', GEL: 'Gel', AGM: 'AGM' };
    const INVERTER_TYPES = { STRING: 'On-grid', HYBRID: 'Híbrido', MICRO: 'Microinversor', OFF_GRID: 'Off-grid' };
    const SEVERITY_ICONS = {
        OK: 'bi-check-circle-fill text-success',
        WARNING: 'bi-exclamation-triangle-fill text-warning',
        ERROR: 'bi-x-circle-fill text-danger'
    };
    const SEVERITY_RANK = { OK: 0, WARNING: 1, ERROR: 2 };
    const moneyFormat = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    // ---- State ----

    const byId = new Map();      // product id -> { kind, dto }
    const catalog = {};          // kind -> products
    const single = {};           // kind -> { productId, quantity } (inverter, controller, batteries, panels)
    const rows = { protection: [], cable: [], terminal: [], structure: [] };
    let nextUid = 1;
    let marginPercent = 0;
    let report = null;
    let reviewFailed = false;
    let suggestions = [];        // the Agregar buttons point into this list by index
    let dirty = false;
    let saving = false;

    function money(value) {
        return '$' + moneyFormat.format(Number(value) || 0);
    }

    function esc(value) {
        return escapeHtml(value);
    }

    function fmt(value) {
        if (value == null || value === '') {
            return '—';
        }
        const n = Number(value);
        return Number.isInteger(n) ? String(n) : String(Math.round(n * 10) / 10);
    }

    function showAlert(message) {
        alertEl.textContent = message;
        alertEl.classList.remove('d-none');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    function hideAlert() {
        alertEl.classList.add('d-none');
    }

    // ---- Product labels ----

    function specOf(kind, p) {
        const parts = [];
        switch (kind) {
            case 'inverter':
                parts.push(fmt(p.ratedPowerKw) + ' kW', p.batteryVoltage != null ? fmt(p.batteryVoltage) + ' V' : null,
                    INVERTER_TYPES[p.inverterType] || p.inverterType,
                    p.builtInCharger === false ? 'sin cargador solar' : 'MPPT integrado');
                break;
            case 'controller':
                parts.push(fmt(p.maxChargeCurrentA) + ' A de carga', (p.supportedBatteryVoltages || []).join('/') + ' V', 'PV máx ' + fmt(p.maxInputVoltage) + ' V');
                break;
            case 'battery':
                parts.push(fmt(p.voltage) + ' V', p.capacityAh != null ? fmt(p.capacityAh) + ' Ah' : null, CHEMISTRY[p.chemistry] || p.chemistry,
                    fmt(p.capacityKwh) + ' kWh', p.integratedBreakerA != null ? 'breaker integrado ' + fmt(p.integratedBreakerA) + ' A' : null);
                break;
            case 'panel':
                parts.push(fmt(p.wattage) + ' W', 'Voc ' + fmt(p.voc) + ' V', 'Isc ' + fmt(p.isc) + ' A', p.connector);
                break;
            case 'protection':
            case 'terminal':
                parts.push(p.specLabel);
                break;
            case 'cable':
                parts.push(WIRE_GAUGE_LABELS[p.gauge] || p.gauge, fmt(p.lengthMeters) + ' m');
                break;
            default:
                break;
        }
        return parts.filter(Boolean).join(' · ');
    }

    function optionLabel(p) {
        return p.sku + ' — ' + p.name + ' ($' + moneyFormat.format(p.price) + ')' + (p.active === false ? ' · inactivo' : '');
    }

    function productOptions(kind, selectedId) {
        const list = catalog[kind].slice();
        // A product that isn't in this kind's list (an old kit) still shows, so nothing is lost silently.
        if (selectedId != null && !list.some((p) => p.id === selectedId) && byId.has(selectedId)) {
            list.push(byId.get(selectedId).dto);
        }
        return list.map((p) =>
            '<option value="' + p.id + '"' + (p.id === selectedId ? ' selected' : '') + '>' + esc(optionLabel(p)) + '</option>').join('');
    }

    // ---- Kit <-> state ----

    function newRow(productId, quantity) {
        return { uid: nextUid++, productId: productId, quantity: quantity };
    }

    /** Puts a product into the section it belongs to; returns that section's kind. */
    function addItem(productId, quantity) {
        const entry = byId.get(productId);
        const kind = entry ? entry.kind : 'structure';
        if (KINDS[kind].single) {
            const s = single[kind];
            if (s.productId == null) {
                s.productId = productId;
                s.quantity = quantity;
                return kind;
            }
            if (s.productId === productId) {
                s.quantity += quantity;
                return kind;
            }
            // A second model of a one-model kind (the server refuses to save that): keep it visible under "otros".
            rows.structure.push(newRow(productId, quantity));
            return 'structure';
        }
        const existing = rows[kind].find((r) => r.productId === productId);
        if (existing) {
            existing.quantity += quantity;
        } else {
            rows[kind].push(newRow(productId, quantity));
        }
        return kind;
    }

    function collectItems() {
        const merged = new Map();
        const put = (productId, quantity) => {
            if (productId != null) {
                merged.set(productId, (merged.get(productId) || 0) + quantity);
            }
        };
        Object.keys(single).forEach((kind) => put(single[kind].productId, single[kind].quantity));
        Object.keys(rows).forEach((kind) => rows[kind].forEach((r) => put(r.productId, r.quantity)));
        return Array.from(merged, ([productId, quantity]) => ({ productId: productId, quantity: quantity }));
    }

    function unitPrice(productId) {
        const entry = byId.get(productId);
        return entry ? Number(entry.dto.price) || 0 : 0;
    }

    function sectionCost(sec) {
        if (sec.key === 'general') {
            return null;
        }
        if (single[sec.key]) {
            const s = single[sec.key];
            return s.productId == null ? 0 : unitPrice(s.productId) * (s.quantity || 0);
        }
        return rows[sec.key].reduce((sum, r) => sum + unitPrice(r.productId) * (r.quantity || 0), 0);
    }

    function totalCost() {
        return collectItems().reduce((sum, i) => sum + unitPrice(i.productId) * i.quantity, 0);
    }

    function priceOverride() {
        const input = document.getElementById('kit-price-override');
        return input && input.value !== '' ? Number(input.value) : null;
    }

    // ---- Page skeleton ----

    function generalHtml(kit) {
        return '<div class="mb-3"><label class="form-label" for="kit-name">Nombre</label>' +
            '<input type="text" class="form-control" id="kit-name" maxlength="255" value="' + escapeAttr(kit ? kit.name : '') + '" required></div>' +
            '<div class="mb-3"><label class="form-label" for="kit-description">Descripción</label>' +
            '<textarea class="form-control" id="kit-description" rows="3">' + esc(kit && kit.description ? kit.description : '') + '</textarea></div>' +
            '<div class="row g-3 mb-3"><div class="col-md-8"><label class="form-label" for="kit-price-override">Precio manual (opcional)</label>' +
            '<input type="number" step="0.01" min="0" class="form-control" id="kit-price-override" value="' +
            escapeAttr(kit && kit.priceOverride != null ? kit.priceOverride : '') + '">' +
            '<div class="form-text">Si lo dejas vacío, se calcula sumando sus productos más el margen.</div></div>' +
            '<div class="col-md-4 d-flex align-items-center"><div class="form-check mt-md-4">' +
            '<input type="checkbox" class="form-check-input" id="kit-active"' + (!kit || kit.active ? ' checked' : '') + '>' +
            '<label class="form-check-label" for="kit-active">Activo (se vende en la tienda)</label></div></div></div>' +
            '<label class="form-label">Fotos del kit</label>' +
            photoManagerHtml('kit-photos', 'kits') +
            '<div class="form-text">Opcional. Sin fotos se arma un collage con las de sus productos.</div>';
    }

    function singleHtml(sec) {
        const cfg = KINDS[sec.key];
        const empty = catalog[sec.key].length === 0;
        return (empty ? '<p class="text-secondary small mb-2">No hay ' + KIND_NAMES[sec.key] + ' en el catálogo. Dalos de alta en su pestaña del admin.</p>' : '') +
            '<div class="row g-2 align-items-center"><div class="' + (cfg.quantity ? 'col-md-9' : 'col-12') + '">' +
            '<select class="form-select" data-single="' + sec.key + '" aria-label="' + esc(sec.title) + '">' +
            '<option value="">— ' + esc(sec.blank) + ' —</option>' + productOptions(sec.key, single[sec.key].productId) + '</select></div>' +
            (cfg.quantity
                ? '<div class="col-md-3"><div class="input-group"><span class="input-group-text">Cantidad</span>' +
                  '<input type="number" min="1" class="form-control" data-single-qty="' + sec.key + '" value="' + single[sec.key].quantity + '"></div></div>'
                : '') +
            '</div><div class="small text-secondary mt-2" data-spec="' + sec.key + '"></div>';
    }

    function multiHtml(sec) {
        const tab = CATALOG_TABS[sec.key];
        const empty = catalog[sec.key].length === 0;
        return '<div data-rows="' + sec.key + '"></div>' +
            '<button type="button" class="btn btn-sm btn-outline-success mt-1" data-add-row="' + sec.key + '"' + (empty ? ' disabled' : '') + '>' +
            '<i class="bi bi-plus-lg"></i> ' + ADD_LABELS[sec.key] + '</button>' +
            (empty ? ' <span class="small text-secondary">No hay ' + KIND_NAMES[sec.key] + ' en el catálogo' + (tab ? '; dalas de alta en su pestaña' : '') + '.</span>' : '');
    }

    function sectionHtml(sec, kit) {
        const body = sec.key === 'general' ? generalHtml(kit) : single[sec.key] ? singleHtml(sec) : multiHtml(sec);
        return '<section class="card mb-3 editor-section" id="sec-' + sec.key + '">' +
            '<div class="card-header d-flex align-items-center gap-2 flex-wrap">' +
            '<span class="section-num">' + sec.num + '</span>' +
            '<h3 class="h6 fw-semibold mb-0 me-auto">' + esc(sec.title) + '</h3>' +
            '<span class="small text-secondary" data-subtotal="' + sec.key + '"></span>' +
            '<span data-status="' + sec.key + '"></span></div>' +
            '<div class="card-body">' + body +
            '<div data-info="' + sec.key + '"></div><div data-review="' + sec.key + '"></div></div></section>';
    }

    function rowHtml(kind, r) {
        const entry = byId.get(r.productId);
        return '<div class="row g-2 mb-2 align-items-start" data-row="' + r.uid + '" data-kind="' + kind + '">' +
            '<div class="col-8 col-md-9"><select class="form-select form-select-sm" data-row-product aria-label="Producto">' +
            productOptions(kind, r.productId) + '</select>' +
            '<div class="small text-secondary mt-1" data-row-spec>' + esc(entry ? specOf(kind, entry.dto) : '') + '</div></div>' +
            '<div class="col-3 col-md-2"><input type="number" min="1" class="form-control form-control-sm" data-row-qty aria-label="Cantidad" value="' + r.quantity + '"></div>' +
            '<div class="col-1"><button type="button" class="btn btn-sm btn-outline-danger" data-row-remove aria-label="Quitar"><i class="bi bi-trash"></i></button></div>' +
            '</div>';
    }

    function renderRows(kind) {
        const host = sectionsEl.querySelector('[data-rows="' + kind + '"]');
        host.innerHTML = rows[kind].length
            ? rows[kind].map((r) => rowHtml(kind, r)).join('')
            : '<p class="text-secondary small mb-2">Todavía no hay ' + KIND_NAMES[kind] + ' en este kit.</p>';
    }

    function syncSection(kind) {
        if (single[kind]) {
            const select = sectionsEl.querySelector('[data-single="' + kind + '"]');
            select.value = single[kind].productId == null ? '' : String(single[kind].productId);
            const qty = sectionsEl.querySelector('[data-single-qty="' + kind + '"]');
            if (qty) {
                qty.value = single[kind].quantity;
            }
            renderSpec(kind);
        } else {
            renderRows(kind);
        }
    }

    function renderSpec(kind) {
        const host = sectionsEl.querySelector('[data-spec="' + kind + '"]');
        const entry = single[kind].productId == null ? null : byId.get(single[kind].productId);
        host.textContent = entry ? specOf(kind, entry.dto) : '';
    }

    // ---- Review under each section ----

    function checksOf(sec) {
        return report ? report.checks.filter((c) => c.section === sec.review) : [];
    }

    function checksHtml(checks) {
        return checks.length
            ? '<ul class="list-unstyled review-note mb-0 mt-2">' + checks.map((c) =>
                '<li class="mb-1"><i class="bi ' + SEVERITY_ICONS[c.severity] + ' me-1"></i>' + esc(c.message) + '</li>').join('') + '</ul>'
            : '';
    }

    function suggestionButton(suggestion, label) {
        suggestions.push(suggestion);
        return '<div class="mt-1"><span class="text-secondary">' + esc(label) + '</span> ' +
            '<button type="button" class="btn btn-sm btn-outline-success py-0 ms-1" data-suggest="' + (suggestions.length - 1) + '">' +
            '<i class="bi bi-plus-lg"></i> Agregar</button></div>';
    }

    function suggestionLabel(s) {
        return 'Sugerido: ' + s.quantity + ' × ' + s.name + ' (' + money(Number(s.unitPrice) * s.quantity) + ')';
    }

    function protectionsReviewHtml(list) {
        if (!list || !list.length) {
            return '';
        }
        return '<div class="table-responsive mt-3"><table class="table table-sm align-middle review-note mb-0">' +
            '<thead><tr><th>Protección</th><th>Lo que pide</th><th>En el kit</th></tr></thead><tbody>' +
            list.map((r) => {
                let status;
                if (r.covered) {
                    status = '<i class="bi bi-check-circle-fill text-success me-1"></i>' + esc(r.coveredBy);
                } else {
                    status = '<i class="bi bi-exclamation-triangle-fill text-warning me-1"></i><strong>Falta' +
                        (r.quantity > 1 ? ' (' + r.quantity + ')' : '') + '</strong>' +
                        (r.coveredBy ? ' <span class="text-secondary">· ya tiene ' + esc(r.coveredBy) + '</span>' : '') +
                        (r.problem ? '<div class="text-secondary">' + esc(r.problem) + '</div>' : '') +
                        (r.suggestion ? suggestionButton(r.suggestion, suggestionLabel(r.suggestion))
                            : '<div class="text-secondary">No hay una en tu catálogo que cumpla; dala de alta en Protecciones.</div>');
                }
                return '<tr><td class="fw-semibold">' + (r.quantity > 1 ? r.quantity + ' × ' : '') + esc(r.label) + '</td><td>' + esc(r.requirement) +
                    '</td><td>' + status + '</td></tr>';
            }).join('') + '</tbody></table></div>';
    }

    function cablesReviewHtml(wiring) {
        if (!wiring || !wiring.length) {
            return '';
        }
        return '<div class="table-responsive mt-3"><table class="table table-sm align-middle review-note mb-0">' +
            '<thead><tr><th>Tramo</th><th>Corriente</th><th>Cable que pide</th><th>Caída</th><th>En el kit</th></tr></thead><tbody>' +
            wiring.map((w) => {
                let status;
                if (!w.gauge) {
                    status = '<i class="bi bi-x-circle-fill text-danger me-1"></i>Ningún cable de la tabla alcanza';
                } else if (w.coveredBy) {
                    status = '<i class="bi bi-check-circle-fill text-success me-1"></i>' + esc(w.coveredBy);
                } else {
                    status = '<i class="bi bi-exclamation-triangle-fill text-warning me-1"></i><strong>Falta ' + fmt(w.requiredCableMeters) + ' m</strong>' +
                        (w.suggestion ? suggestionButton(w.suggestion, suggestionLabel(w.suggestion))
                            : '<div class="text-secondary">No hay un cable en tu catálogo que sirva.</div>');
                }
                return '<tr><td class="fw-semibold">' + esc(w.segment) +
                    (w.connector ? '<div class="fw-normal text-secondary">Conector ' + esc(w.connector) + '</div>' : '') + '</td>' +
                    '<td>' + fmt(w.operatingCurrentA) + ' A' + (w.currentNote ? '<div class="text-secondary">' + esc(w.currentNote) + '</div>' : '') + '</td>' +
                    '<td>' + (w.gaugeLabel ? esc(w.gaugeLabel) + ' · ' + fmt(w.requiredCableMeters) + ' m' : '—') +
                    (w.fuseA ? '<div class="text-secondary">Fusible ' + w.fuseA + ' A</div>' : '') + '</td>' +
                    '<td>' + (w.gauge ? fmt(w.voltageDropPercent) + ' %' : '—') + '</td><td>' + status + '</td></tr>';
            }).join('') + '</tbody></table></div>';
    }

    function terminalsReviewHtml(list) {
        if (!list || !list.length) {
            return '';
        }
        return '<div class="table-responsive mt-3"><table class="table table-sm align-middle review-note mb-0">' +
            '<thead><tr><th>Terminal</th><th>Cant.</th><th>Dónde</th><th>En el kit</th></tr></thead><tbody>' +
            list.map((t) => {
                const status = t.covered
                    ? '<i class="bi bi-check-circle-fill text-success me-1"></i>' + esc(t.coveredBy)
                    : '<i class="bi bi-info-circle-fill text-info me-1"></i><strong>Faltan</strong>' +
                      (t.suggestion ? suggestionButton(t.suggestion, suggestionLabel(t.suggestion))
                          : '<div class="text-secondary">No hay una bolsa en tu catálogo; dala de alta en Terminales.</div>');
                return '<tr><td class="fw-semibold">' + esc(t.gaugeLabel) + ' · ' + esc(t.sizeLabel) + '</td><td>' + t.quantity + '</td><td>' + esc(t.places) +
                    '</td><td>' + status + '</td></tr>';
            }).join('') + '</tbody></table></div>' +
            '<div class="form-text">Es informativo: no cuenta las de tierra ni las punteras.</div>';
    }

    /** Messages the tables already say (a missing protection or cable), so they aren't shown twice. */
    function isShownInTable(sec, check) {
        if (check.severity === 'OK') {
            return sec.key === 'cable' && /el kit incluye cable suficiente/.test(check.message);
        }
        if (sec.key === 'protection') {
            return /^Falta /.test(check.message);
        }
        if (sec.key === 'cable') {
            return /se necesitan [\d.]+ m de cable/.test(check.message);
        }
        return false;
    }

    function infoFor(sec) {
        if (!report) {
            return '';
        }
        const lines = [];
        if (sec.key === 'inverter' && report.solarCharger && /^Integrado/.test(report.solarCharger)) {
            lines.push('Cargador solar: ' + report.solarCharger);
        }
        if (sec.key === 'controller' && report.solarCharger && /^Controlador/.test(report.solarCharger)) {
            lines.push('Cargador solar: ' + report.solarCharger);
        }
        if (sec.key === 'battery' && report.batteryBank) {
            lines.push('Banco: ' + report.batteryBank);
        }
        if (sec.key === 'panel' && report.panelArray) {
            lines.push('Arreglo: ' + report.panelArray);
        }
        return lines.length
            ? '<div class="mt-2 small fw-semibold"><i class="bi bi-diagram-3 me-1"></i>' + lines.map(esc).join('<br>') + '</div>'
            : '';
    }

    function inverterHasCharger() {
        const id = single.inverter.productId;
        const entry = id == null ? null : byId.get(id);
        return entry ? entry.dto.builtInCharger !== false : null;
    }

    function noteFor(sec) {
        if (sec.key === 'controller' && single.controller.productId == null && inverterHasCharger() === true) {
            return '<div class="mt-2 small text-secondary"><i class="bi bi-info-circle me-1"></i>El inversor elegido ya trae su cargador solar (MPPT), así que no hace falta un controlador.</div>';
        }
        if (sec.key === 'controller' && single.controller.productId == null && inverterHasCharger() === false && single.panel.productId != null) {
            return '<div class="mt-2 small text-warning-emphasis"><i class="bi bi-exclamation-triangle me-1"></i>Este inversor no tiene cargador solar: elige un controlador para conectar los paneles.</div>';
        }
        return '';
    }

    function renderReview() {
        suggestions = [];
        SECTIONS.forEach((sec) => {
            const infoHost = sectionsEl.querySelector('[data-info="' + sec.key + '"]');
            const reviewHost = sectionsEl.querySelector('[data-review="' + sec.key + '"]');
            if (sec.key === 'general' || sec.key === 'structure') {
                return;
            }
            infoHost.innerHTML = infoFor(sec) + noteFor(sec);
            let html = '';
            if (report) {
                if (sec.key === 'protection') {
                    html += protectionsReviewHtml(report.protections);
                } else if (sec.key === 'cable') {
                    html += cablesReviewHtml(report.wiring);
                } else if (sec.key === 'terminal') {
                    html += terminalsReviewHtml(report.terminals);
                }
                html += checksHtml(checksOf(sec).filter((c) => !isShownInTable(sec, c)));
            }
            reviewHost.innerHTML = html;
        });
        renderStatuses();
        renderSubtotals();
        renderSide();
    }

    // ---- Section status and subtotals ----

    function badge(cls, text) {
        return '<span class="badge ' + cls + '">' + esc(text) + '</span>';
    }

    function statusFor(sec) {
        if (sec.key === 'general' || sec.key === 'structure' || !report) {
            return '';
        }
        const checks = checksOf(sec);
        const worst = checks.reduce((max, c) => Math.max(max, SEVERITY_RANK[c.severity]), 0);
        const empty = single[sec.key] ? single[sec.key].productId == null : rows[sec.key].length === 0;

        if (sec.key === 'protection') {
            const missing = (report.protections || []).filter((r) => !r.covered).length;
            if (worst === 2) {
                return badge('text-bg-danger', 'Error');
            }
            return missing > 0 ? badge('text-bg-warning', 'Faltan ' + missing) : (report.protections || []).length ? badge('text-bg-success', 'Cubiertas') : '';
        }
        if (sec.key === 'cable') {
            const missing = (report.wiring || []).filter((w) => !w.coveredBy).length;
            if (worst === 2 || (report.wiring || []).some((w) => !w.gauge)) {
                return badge('text-bg-danger', 'Error');
            }
            return missing > 0 ? badge('text-bg-warning', 'Faltan ' + missing + (missing === 1 ? ' tramo' : ' tramos'))
                : (report.wiring || []).length ? badge('text-bg-success', 'Cubiertos') : '';
        }
        if (sec.key === 'terminal') {
            const missing = (report.terminals || []).filter((t) => !t.covered).length;
            return missing > 0 ? badge('text-bg-info', 'Faltan ' + missing) : (report.terminals || []).length ? badge('text-bg-success', 'Cubiertas') : '';
        }
        if (worst === 2) {
            return badge('text-bg-danger', 'Error');
        }
        if (worst === 1) {
            return badge('text-bg-warning', 'Revisar');
        }
        if (empty) {
            if (sec.key === 'inverter') {
                return badge('text-bg-danger', 'Falta');
            }
            if (sec.key === 'controller' && inverterHasCharger() === true) {
                return badge('text-bg-secondary', 'No hace falta');
            }
            return badge('text-bg-secondary', 'Opcional');
        }
        return badge('text-bg-success', 'Listo');
    }

    function renderStatuses() {
        SECTIONS.forEach((sec) => {
            sectionsEl.querySelector('[data-status="' + sec.key + '"]').innerHTML = statusFor(sec);
        });
    }

    function renderSubtotals() {
        SECTIONS.forEach((sec) => {
            const cost = sectionCost(sec);
            const host = sectionsEl.querySelector('[data-subtotal="' + sec.key + '"]');
            host.textContent = cost ? money(cost) : '';
        });
    }

    // ---- Summary column ----

    function expansionHtml(expansion) {
        if (!expansion) {
            return '';
        }
        const classes = { EXPANDABLE: 'bg-success', LIMITED: 'bg-warning text-dark', MAXED: 'bg-secondary' };
        const line = (name, aspect) => aspect ? '<li><strong>' + name + ':</strong> ' + esc(aspect.label) + ' — ' + esc(aspect.detail) + '</li>' : '';
        return '<hr><div class="small"><strong>Ampliación</strong> <span class="badge ' + (classes[expansion.level] || 'bg-secondary') + '">' + esc(expansion.label) + '</span>' +
            '<ul class="list-unstyled mt-1 mb-0">' + line('Paneles', expansion.panels) + line('Baterías', expansion.batteries) + '</ul></div>';
    }

    function messagesHtml() {
        if (reviewFailed) {
            return '<p class="small text-secondary mb-0">No se pudo revisar la compatibilidad.</p>';
        }
        if (!report) {
            return '<p class="small text-secondary mb-0">Agrega productos para ver la revisión de compatibilidad.</p>';
        }
        const problems = report.checks.filter((c) => c.severity !== 'OK');
        const errors = problems.filter((c) => c.severity === 'ERROR').length;
        const warnings = problems.length - errors;
        const summary = errors === 0 && warnings === 0
            ? '<div class="small"><i class="bi ' + SEVERITY_ICONS.OK + ' me-1"></i>Todo en orden.</div>'
            : '<div class="small mb-2">' + (errors ? '<span class="badge text-bg-danger me-1">' + errors + (errors === 1 ? ' error' : ' errores') + '</span>' : '') +
              (warnings ? '<span class="badge text-bg-warning">' + warnings + (warnings === 1 ? ' aviso' : ' avisos') + '</span>' : '') + '</div>';
        const list = problems.map((c) => {
            const sec = SECTION_BY_REVIEW[c.section];
            return '<li class="mb-2"><i class="bi ' + SEVERITY_ICONS[c.severity] + ' me-1"></i>' + esc(c.message) +
                (sec ? ' <a href="#sec-' + sec.key + '" class="text-nowrap">(' + esc(sec.title) + ')</a>' : '') + '</li>';
        }).join('');
        return summary + (list ? '<ul class="list-unstyled small mb-0">' + list + '</ul>' : '');
    }

    function renderSide() {
        const cost = totalCost();
        const override = priceOverride();
        const sell = override != null ? override : cost * (1 + marginPercent / 100);
        const suggested = report && report.suggestedItems ? report.suggestedItems : [];
        const suggestedCost = suggested.reduce((sum, s) => sum + Number(s.unitPrice) * s.quantity, 0);

        const suggestButton = suggested.length
            ? '<button type="button" class="btn btn-sm btn-outline-success w-100 mb-3" data-suggest-all>' +
              '<i class="bi bi-plus-lg"></i> ' + (suggested.length === 1 ? 'Agregar el sugerido' : 'Agregar los ' + suggested.length + ' sugeridos') +
              ' (' + money(suggestedCost) + ')</button>'
            : '';

        // Cost and save buttons stay together and always in view; the review (which can get long) scrolls on its own.
        sideEl.innerHTML = '<div class="card mb-3"><div class="card-body">' +
            '<h3 class="h6 fw-semibold mb-2"><i class="bi bi-cash-coin me-1"></i>Costo del kit</h3>' +
            '<div class="d-flex justify-content-between"><span>Costo</span><strong>' + money(cost) + '</strong></div>' +
            '<div class="d-flex justify-content-between"><span>Venta' + (override != null ? ' (manual)' : ' (+' + fmt(marginPercent) + ' %)') + '</span><strong>' + money(sell) + '</strong></div>' +
            '<div class="d-flex justify-content-between text-secondary small mb-3"><span>Ganancia</span><span>' + money(sell - cost) + '</span></div>' +
            '<div class="d-grid gap-2">' +
            '<button type="button" class="btn btn-primary" data-save="stay"' + (saving ? ' disabled' : '') + '><i class="bi bi-save"></i> Guardar</button>' +
            '<button type="button" class="btn btn-outline-primary btn-sm" data-save="back"' + (saving ? ' disabled' : '') + '>Guardar y volver a los kits</button>' +
            '<div class="d-flex gap-2"><a class="btn btn-outline-secondary btn-sm flex-fill" href="/admin" data-leave>Cancelar</a>' +
            (kitId ? '<a class="btn btn-outline-secondary btn-sm flex-fill" target="_blank" rel="noopener" href="/admin/kits/' + kitId +
                '/diagrama" title="Muestra el kit guardado"><i class="bi bi-diagram-3"></i> Diagrama</a>' : '') + '</div>' +
            '</div><div class="small mt-2" id="save-message" aria-live="polite"></div></div></div>' +
            '<div class="card"><div class="card-body editor-review">' +
            '<h3 class="h6 fw-semibold mb-2"><i class="bi bi-plug me-1"></i>Revisión de compatibilidad</h3>' +
            suggestButton + messagesHtml() + expansionHtml(report && report.expansion) + '</div></div>';
    }

    // ---- Changes ----

    let reviewTimer = null;
    let reviewSeq = 0;

    function changed() {
        dirty = true;
        hideAlert();
        renderSubtotals();
        renderSide();
        scheduleReview();
    }

    function scheduleReview() {
        clearTimeout(reviewTimer);
        reviewTimer = setTimeout(runReview, 300);
    }

    async function runReview() {
        const items = collectItems();
        if (items.length === 0 || items.some((i) => !(i.quantity > 0))) {
            report = null;
            reviewFailed = false;
            renderReview();
            return;
        }
        const seq = ++reviewSeq;
        let response;
        try {
            response = await authFetch('/api/catalog/kits/validate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(items)
            });
        } catch (err) {
            return;
        }
        if (seq !== reviewSeq) {
            return;
        }
        reviewFailed = !response.ok;
        report = response.ok ? await response.json() : null;
        if (seq !== reviewSeq) {
            return;
        }
        renderReview();
    }

    function applySuggestion(suggestion) {
        const kind = addItem(suggestion.productId, suggestion.quantity);
        syncSection(kind);
    }

    sectionsEl.addEventListener('change', (event) => {
        const target = event.target;
        if (target.matches('[data-single]')) {
            const kind = target.dataset.single;
            single[kind].productId = target.value === '' ? null : Number(target.value);
            renderSpec(kind);
            changed();
        } else if (target.matches('[data-row-product]')) {
            const rowEl = target.closest('[data-row]');
            const row = rows[rowEl.dataset.kind].find((r) => r.uid === Number(rowEl.dataset.row));
            row.productId = Number(target.value);
            rowEl.querySelector('[data-row-spec]').textContent = specOf(rowEl.dataset.kind, byId.get(row.productId).dto);
            changed();
        }
    });

    sectionsEl.addEventListener('input', (event) => {
        const target = event.target;
        if (target.matches('[data-single-qty]')) {
            single[target.dataset.singleQty].quantity = Math.floor(Number(target.value)) || 0;
            changed();
        } else if (target.matches('[data-row-qty]')) {
            const rowEl = target.closest('[data-row]');
            const row = rows[rowEl.dataset.kind].find((r) => r.uid === Number(rowEl.dataset.row));
            row.quantity = Math.floor(Number(target.value)) || 0;
            changed();
        } else if (target.id === 'kit-name') {
            titleEl.textContent = target.value.trim() || 'Kit sin nombre';
            dirty = true;
        } else if (target.id === 'kit-price-override') {
            changed();
        } else if (target.matches('#kit-description, #kit-active')) {
            dirty = true;
        }
    });

    sectionsEl.addEventListener('click', (event) => {
        const add = event.target.closest('[data-add-row]');
        if (add) {
            const kind = add.dataset.addRow;
            const first = catalog[kind][0];
            if (first) {
                const used = new Set(rows[kind].map((r) => r.productId));
                const pick = catalog[kind].find((p) => !used.has(p.id)) || first;
                addItem(pick.id, 1);
                renderRows(kind);
                changed();
            }
            return;
        }
        const remove = event.target.closest('[data-row-remove]');
        if (remove) {
            const rowEl = remove.closest('[data-row]');
            const kind = rowEl.dataset.kind;
            rows[kind] = rows[kind].filter((r) => r.uid !== Number(rowEl.dataset.row));
            renderRows(kind);
            changed();
            return;
        }
        const suggest = event.target.closest('[data-suggest]');
        if (suggest) {
            applySuggestion(suggestions[Number(suggest.dataset.suggest)]);
            changed();
            return;
        }
        if (event.target.closest('.photo-manager')) {
            dirty = true;
        }
    });

    sideEl.addEventListener('click', (event) => {
        if (event.target.closest('[data-suggest-all]')) {
            (report && report.suggestedItems ? report.suggestedItems : []).forEach(applySuggestion);
            changed();
            return;
        }
        const saveButton = event.target.closest('[data-save]');
        if (saveButton) {
            save(saveButton.dataset.save === 'back');
        }
    });

    // ---- Save ----

    function setSaveMessage(html) {
        const host = document.getElementById('save-message');
        if (host) {
            host.innerHTML = html;
        }
    }

    async function save(goBack) {
        if (saving) {
            return;
        }
        hideAlert();
        if (photoUploadInProgress()) {
            showAlert('Espera a que terminen de subir las fotos.');
            return;
        }
        const nameInput = document.getElementById('kit-name');
        if (!nameInput.value.trim()) {
            showAlert('El nombre del kit es obligatorio.');
            nameInput.focus();
            return;
        }
        const items = collectItems();
        if (items.length === 0) {
            showAlert('Agrega al menos un producto al kit.');
            return;
        }
        if (items.some((i) => !Number.isInteger(i.quantity) || i.quantity < 1)) {
            showAlert('Todas las cantidades deben ser números enteros de 1 en adelante.');
            return;
        }

        const body = {
            name: nameInput.value.trim(),
            description: document.getElementById('kit-description').value,
            active: document.getElementById('kit-active').checked,
            priceOverride: priceOverride(),
            imageUrls: photoManagerValue('kit-photos'),
            items: items
        };

        saving = true;
        renderSide();
        let response;
        try {
            response = await authFetch(kitId ? '/api/catalog/kits/' + kitId : '/api/catalog/kits', {
                method: kitId ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
        } catch (err) {
            saving = false;
            renderSide();
            showAlert('No se pudo guardar el kit. Revisa tu conexión e intenta de nuevo.');
            return;
        }
        if (!response.ok) {
            const message = await errorMessage(response, 'No se pudo guardar el kit. Revisa los datos ingresados.');
            saving = false;
            renderSide();
            showAlert(message);
            return;
        }
        const saved = await response.json();
        dirty = false;
        saving = false;
        if (goBack) {
            window.location.href = '/admin';
            return;
        }
        if (!kitId) {
            kitId = saved.id;
            document.body.dataset.kitId = String(kitId);
            history.replaceState(null, '', '/admin/kits/' + kitId + '/editar');
        }
        titleEl.textContent = saved.name;
        renderSide();
        setSaveMessage('<span class="text-success"><i class="bi bi-check-circle-fill me-1"></i>Guardado.</span>');
    }

    window.addEventListener('beforeunload', (event) => {
        if (dirty) {
            event.preventDefault();
            event.returnValue = '';
        }
    });
    document.addEventListener('click', (event) => {
        // Leaving with unsaved changes asks first (the browser's own dialog covers closing the tab).
        const leave = event.target.closest('a[data-leave], #back-link');
        if (leave && dirty && !window.confirm('Tienes cambios sin guardar. ¿Salir sin guardarlos?')) {
            event.preventDefault();
        } else if (leave) {
            dirty = false;
        }
    });

    // ---- Start ----

    async function init() {
        const kinds = Object.keys(KINDS);
        let lists;
        let kit = null;
        try {
            const [catalogs, kitResponse, margin] = await Promise.all([
                Promise.all(kinds.map((kind) => authFetch(KINDS[kind].endpoint).then((r) => {
                    if (!r.ok) {
                        throw new Error('catalog');
                    }
                    return r.json();
                }))),
                kitId ? authFetch('/api/catalog/kits/' + kitId) : Promise.resolve(null),
                authFetch('/api/settings/profit-margin').then((r) => (r.ok ? r.json() : null)).catch(() => null)
            ]);
            lists = catalogs;
            if (kitResponse) {
                if (kitResponse.status === 404) {
                    loadingEl.classList.add('d-none');
                    showAlert('Ese kit no existe (quizá se borró). Vuelve a la lista de kits.');
                    return;
                }
                if (!kitResponse.ok) {
                    throw new Error('kit');
                }
                kit = await kitResponse.json();
            }
            marginPercent = margin && margin.marginPercent != null ? Number(margin.marginPercent) : 0;
        } catch (err) {
            loadingEl.classList.add('d-none');
            showAlert('No se pudo cargar el editor. Revisa tu conexión y recarga la página.');
            return;
        }

        kinds.forEach((kind, index) => {
            catalog[kind] = lists[index].slice().sort((a, b) => String(a.sku).localeCompare(String(b.sku)));
            catalog[kind].forEach((dto) => byId.set(dto.id, { kind: kind, dto: dto }));
            if (KINDS[kind].single) {
                single[kind] = { productId: null, quantity: 1 };
            }
        });
        if (kit) {
            kit.items.forEach((item) => addItem(item.productId, item.quantity));
        }
        single.inverter.quantity = 1;
        single.controller.quantity = 1;

        titleEl.textContent = kit ? kit.name : 'Nuevo kit';
        document.title = (kit ? kit.name : 'Nuevo kit') + ' | Panel de administración | Baja Solar Supply';
        jumpNav.innerHTML = SECTIONS.map((sec) => '<a href="#sec-' + sec.key + '">' + sec.num + '. ' + esc(sec.title) + '</a>').join('');
        sectionsEl.innerHTML = SECTIONS.map((sec) => sectionHtml(sec, kit)).join('');
        Object.keys(rows).forEach(renderRows);
        Object.keys(single).forEach(renderSpec);
        initPhotoManager('kit-photos', kit ? kit.imageUrls : []);

        loadingEl.classList.add('d-none');
        rootEl.classList.remove('d-none');
        renderSubtotals();
        renderSide();
        runReview();
    }

    init();
})();
