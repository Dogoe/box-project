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
        initPhotoManager } = Common;

    const kitIdAttr = document.body.dataset.kitId;
    let kitId = kitIdAttr ? Number(kitIdAttr) : null;

    const alertEl = document.getElementById('editor-alert');
    const loadingEl = document.getElementById('editor-loading');
    const rootEl = document.getElementById('editor-root');
    const sectionsEl = document.getElementById('editor-sections');
    const sideEl = document.getElementById('editor-side');
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

    /** What the selectors show: the product's code (type, specs and price); the SKU only if the server sent none. */
    function codeOf(p) {
        return p.code || (p.sku + ' — ' + p.name);
    }

    function optionLabel(p) {
        return codeOf(p) + (p.active === false ? ' · inactivo' : '');
    }

    function productCode(productId, fallback) {
        const entry = byId.get(productId);
        return entry ? codeOf(entry.dto) : fallback;
    }

    // ---- Compatible products first (worked out in the browser from the catalog already loaded) ----

    // Same tolerance as the review: a bank of N batteries in series may be 10 % off the inverter's voltage.
    const BANK_VOLTAGE_TOLERANCE = 0.10;
    // A panel's open-circuit voltage rises in the cold; the review leaves 25 % for that.
    const PV_COLD_VOC_FACTOR = 1.25;

    function selectedDto(kind) {
        const id = single[kind] ? single[kind].productId : null;
        const entry = id == null ? null : byId.get(id);
        return entry ? entry.dto : null;
    }

    /** { label, test } for the kinds that can be told apart by the chosen inverter/controller, else null. */
    function compatibility(kind) {
        const inverter = selectedDto('inverter');
        const busVolts = inverter && inverter.batteryVoltage ? Number(inverter.batteryVoltage) : 0;
        if (kind === 'battery' && busVolts > 0) {
            return {
                label: 'Compatibles con el inversor de ' + fmt(busVolts) + ' V',
                test: (p) => {
                    const series = Math.round(busVolts / p.voltage);
                    return series >= 1 && Math.abs(series * p.voltage - busVolts) / busVolts <= BANK_VOLTAGE_TOLERANCE;
                }
            };
        }
        if (kind === 'controller' && busVolts > 0) {
            return { label: 'Compatibles con el banco de ' + fmt(busVolts) + ' V', test: (p) => (p.supportedBatteryVoltages || []).includes(busVolts) };
        }
        if (kind === 'panel' && inverter) {
            // The MPPT that takes the panels: the inverter's own, or the chosen controller's.
            const charger = inverter.builtInCharger !== false ? inverter : selectedDto('controller');
            const maxVolts = charger ? Number(charger.maxInputVoltage) : 0;
            if (maxVolts > 0) {
                return {
                    label: 'Compatibles con el MPPT (máx. ' + fmt(maxVolts) + ' V)',
                    test: (p) => p.voc > 0 && p.voc * PV_COLD_VOC_FACTOR <= maxVolts
                };
            }
        }
        return null;
    }

    function optionHtml(p, selectedId) {
        return '<option value="' + p.id + '"' + (p.id === selectedId ? ' selected' : '') + '>' + esc(optionLabel(p)) + '</option>';
    }

    function productOptions(kind, selectedId) {
        const list = catalog[kind].slice();
        // A product that isn't in this kind's list (an old kit) still shows, so nothing is lost silently.
        if (selectedId != null && !list.some((p) => p.id === selectedId) && byId.has(selectedId)) {
            list.push(byId.get(selectedId).dto);
        }
        const rule = compatibility(kind);
        if (rule) {
            const fits = list.filter(rule.test);
            const others = list.filter((p) => !rule.test(p));
            // Only worth grouping when it actually separates something.
            if (fits.length && others.length) {
                return '<optgroup label="' + escapeAttr(rule.label) + '">' + fits.map((p) => optionHtml(p, selectedId)).join('') + '</optgroup>' +
                    '<optgroup label="Otros">' + others.map((p) => optionHtml(p, selectedId)).join('') + '</optgroup>';
            }
        }
        return list.map((p) => optionHtml(p, selectedId)).join('');
    }

    /** Rebuilds the one-model selectors whose order depends on the inverter/controller (keeps what is chosen). */
    function refreshSingleOptions() {
        ['controller', 'battery', 'panel'].forEach((kind) => {
            const select = sectionsEl.querySelector('[data-single="' + kind + '"]');
            if (!select) {
                return;
            }
            const sec = SECTIONS.find((x) => x.key === kind);
            select.innerHTML = '<option value="">— ' + esc(sec.blank) + ' —</option>' + productOptions(kind, single[kind].productId);
            select.value = single[kind].productId == null ? '' : String(single[kind].productId);
        });
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
        return '<div class="d-flex gap-2 align-items-end">' +
            '<div class="flex-grow-1" style="min-width: 0;"><label class="form-label small mb-1" for="kit-name">Nombre</label>' +
            '<input type="text" class="form-control form-control-sm" id="kit-name" maxlength="255" value="' + escapeAttr(kit ? kit.name : '') + '" required></div>' +
            '<div class="form-check mb-1 flex-shrink-0" title="Se vende en la tienda">' +
            '<input type="checkbox" class="form-check-input" id="kit-active"' + (!kit || kit.active ? ' checked' : '') + '>' +
            '<label class="form-check-label small" for="kit-active">Activo</label></div></div>' +
            '<button type="button" class="more-toggle mt-2" data-more aria-expanded="false" aria-controls="kit-more">' +
            '<i class="bi bi-chevron-right chev"></i> Más datos <span class="text-secondary" data-more-summary></span></button>' +
            '<div id="kit-more" class="row g-2 mt-0" hidden>' +
            '<div class="col-md-4"><label class="form-label small mb-1" for="kit-price-override" ' +
            'title="Si lo dejas vacío, se calcula sumando sus productos más el margen.">Precio manual (opcional)</label>' +
            '<input type="number" step="0.01" min="0" class="form-control form-control-sm" id="kit-price-override" placeholder="automático" value="' +
            escapeAttr(kit && kit.priceOverride != null ? kit.priceOverride : '') + '"></div>' +
            '<div class="col-12"><label class="form-label small mb-1" for="kit-description">Descripción</label>' +
            '<textarea class="form-control form-control-sm" id="kit-description" rows="2">' + esc(kit && kit.description ? kit.description : '') + '</textarea></div>' +
            '<div class="col-12"><label class="form-label small mb-1" title="Sin fotos se arma un collage con las de sus productos.">Fotos del kit (opcional)</label>' +
            photoManagerHtml('kit-photos', 'kits', { compact: true }) + '</div></div>';
    }

    /** What the closed "Más datos" block holds, so filled-in data isn't hidden without a hint. */
    function refreshMoreSummary() {
        const host = sectionsEl.querySelector('[data-more-summary]');
        if (!host) {
            return;
        }
        const parts = [];
        const override = priceOverride();
        if (override != null) {
            parts.push('Precio manual ' + money(override));
        }
        const description = document.getElementById('kit-description');
        if (description && description.value.trim()) {
            parts.push('Descripción');
        }
        const photos = photoManagerValue('kit-photos').length;
        if (photos > 0) {
            parts.push(photos + (photos === 1 ? ' foto' : ' fotos'));
        }
        host.textContent = parts.length ? '· ' + parts.join(' · ') : '';
    }

    function toggleMore() {
        const button = sectionsEl.querySelector('[data-more]');
        const open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded', String(open));
        document.getElementById('kit-more').hidden = !open;
    }

    function singleHtml(sec) {
        const cfg = KINDS[sec.key];
        const empty = catalog[sec.key].length === 0;
        return (empty ? '<p class="text-secondary small mb-2">No hay ' + KIND_NAMES[sec.key] + ' en el catálogo. Dalos de alta en su pestaña del admin.</p>' : '') +
            '<div class="row g-2 align-items-center"><div class="' + (cfg.quantity ? 'col-md-9' : 'col-12') + '">' +
            '<select class="form-select form-select-sm" data-single="' + sec.key + '" aria-label="' + esc(sec.title) + '">' +
            '<option value="">— ' + esc(sec.blank) + ' —</option>' + productOptions(sec.key, single[sec.key].productId) + '</select></div>' +
            (cfg.quantity
                ? '<div class="col-md-3"><div class="input-group input-group-sm"><span class="input-group-text">Cantidad</span>' +
                  '<input type="number" min="1" class="form-control" data-single-qty="' + sec.key + '" value="' + single[sec.key].quantity + '"></div></div>'
                : '') +
            '</div>';
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
        const foldable = sec.key !== 'general';
        const body = sec.key === 'general' ? generalHtml(kit) : single[sec.key] ? singleHtml(sec) : multiHtml(sec);
        // A foldable section's title is a button (chevron + title + what is chosen, shown while it is closed).
        const title = foldable
            ? '<button type="button" class="section-toggle me-auto" data-toggle="' + sec.key + '" aria-expanded="true" aria-controls="body-' + sec.key + '">' +
              '<i class="bi bi-chevron-right chev"></i><span class="section-title">' + esc(sec.title) + '</span>' +
              '<span class="section-summary" data-summary="' + sec.key + '"></span></button>'
            : '<h3 class="section-title mb-0 me-auto">' + esc(sec.title) + '</h3>';
        return '<section class="editor-section" id="sec-' + sec.key + '">' +
            '<div class="d-flex align-items-center gap-2 section-head">' + title +
            '<span class="small text-secondary section-cost" data-subtotal="' + sec.key + '"></span>' +
            '<span class="flex-shrink-0" data-status="' + sec.key + '"></span></div>' +
            '<div class="section-body" id="body-' + sec.key + '">' +
            body + '<div data-info="' + sec.key + '"></div><div data-review="' + sec.key + '"></div></div></section>';
    }

    // ---- Folding: only the sections marked green start closed ----

    function sectionEl(key) {
        return document.getElementById('sec-' + key);
    }

    function isOpen(key) {
        return !document.getElementById('body-' + key).hidden;
    }

    function setOpen(key, open) {
        const body = document.getElementById('body-' + key);
        const button = sectionEl(key).querySelector('[data-toggle]');
        if (!body || !button) {
            return;
        }
        body.hidden = !open;
        button.setAttribute('aria-expanded', String(open));
    }

    /** One line for a closed section: the codes it holds. */
    function summaryOf(sec) {
        if (single[sec.key]) {
            const s = single[sec.key];
            return s.productId == null ? '' : (s.quantity > 1 ? s.quantity + ' × ' : '') + productCode(s.productId, '');
        }
        return rows[sec.key].map((r) => (r.quantity > 1 ? r.quantity + ' × ' : '') + productCode(r.productId, '')).join(', ');
    }

    function renderSummaries() {
        SECTIONS.forEach((sec) => {
            const host = sectionsEl.querySelector('[data-summary="' + sec.key + '"]');
            if (host) {
                host.textContent = summaryOf(sec);
            }
        });
    }

    let foldingStarted = false;
    const lastTone = {};

    /**
     * First review: green sections close, everything else stays open. From then on nothing closes by itself,
     * but a section that changes to something other than green opens (so a new warning is never hidden).
     */
    function applyFolding() {
        SECTIONS.forEach((sec) => {
            if (sec.key === 'general') {
                return;
            }
            const tone = statusInfo(sec).tone;
            if (!foldingStarted) {
                setOpen(sec.key, tone !== 'ok');
            } else if (tone !== lastTone[sec.key] && tone !== 'ok' && tone != null) {
                setOpen(sec.key, true);
            }
            lastTone[sec.key] = tone;
        });
        foldingStarted = true;
    }

    /** A link from the review: open the section it points to and bring it into view. */
    function goToSection(key) {
        if (sectionEl(key) && sectionEl(key).querySelector('[data-toggle]')) {
            setOpen(key, true);
        }
        const target = sectionEl(key);
        if (target) {
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    function rowHtml(kind, r) {
        return '<div class="row g-2 mb-2 align-items-center" data-row="' + r.uid + '" data-kind="' + kind + '">' +
            '<div class="col-8 col-md-9"><select class="form-select form-select-sm" data-row-product aria-label="Producto">' +
            productOptions(kind, r.productId) + '</select></div>' +
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
        } else {
            renderRows(kind);
        }
    }

    // ---- Review under each section ----

    function checksOf(sec) {
        return report ? report.checks.filter((c) => c.section === sec.review) : [];
    }

    function suggestionButton(suggestion, label) {
        suggestions.push(suggestion);
        return '<div class="mt-1"><span class="text-secondary">' + esc(label) + '</span> ' +
            '<button type="button" class="btn btn-sm btn-outline-success py-0 ms-1" data-suggest="' + (suggestions.length - 1) + '">' +
            '<i class="bi bi-plus-lg"></i> Agregar</button></div>';
    }

    function suggestionLabel(s) {
        return 'Sugerido: ' + s.quantity + ' × ' + productCode(s.productId, s.name) + (s.quantity > 1 ? ' (' + money(Number(s.unitPrice) * s.quantity) + ')' : '');
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

    function renderReview() {
        suggestions = [];
        SECTIONS.forEach((sec) => {
            const infoHost = sectionsEl.querySelector('[data-info="' + sec.key + '"]');
            const reviewHost = sectionsEl.querySelector('[data-review="' + sec.key + '"]');
            if (sec.key === 'general' || sec.key === 'structure') {
                return;
            }
            infoHost.innerHTML = infoFor(sec);
            let html = '';
            if (report) {
                if (sec.key === 'protection') {
                    html += protectionsReviewHtml(report.protections);
                } else if (sec.key === 'cable') {
                    html += cablesReviewHtml(report.wiring);
                } else if (sec.key === 'terminal') {
                    html += terminalsReviewHtml(report.terminals);
                }
            }
            reviewHost.innerHTML = html;
        });
        renderStatuses();
        applyFolding();
        renderSubtotals();
        renderSummaries();
        renderSide();
    }

    // ---- Section status and subtotals ----

    function badge(cls, text) {
        return '<span class="badge ' + cls + '">' + esc(text) + '</span>';
    }

    /** { tone, html }: tone is 'ok' (green), 'warn', 'error', 'info', 'muted', or null when the section has no status. */
    function statusInfo(sec) {
        const none = { tone: null, html: '' };
        if (sec.key === 'general' || sec.key === 'structure' || !report) {
            return none;
        }
        const st = (tone, text) => ({
            tone: tone,
            html: badge({ ok: 'text-bg-success', warn: 'text-bg-warning', error: 'text-bg-danger', info: 'text-bg-info', muted: 'text-bg-secondary' }[tone], text)
        });
        const checks = checksOf(sec);
        const worst = checks.reduce((max, c) => Math.max(max, SEVERITY_RANK[c.severity]), 0);
        const empty = single[sec.key] ? single[sec.key].productId == null : rows[sec.key].length === 0;

        if (sec.key === 'protection') {
            const missing = (report.protections || []).filter((r) => !r.covered).length;
            if (worst === 2) {
                return st('error', 'Error');
            }
            return missing > 0 ? st('warn', 'Faltan ' + missing) : (report.protections || []).length ? st('ok', 'Cubiertas') : none;
        }
        if (sec.key === 'cable') {
            const missing = (report.wiring || []).filter((w) => !w.coveredBy).length;
            if (worst === 2 || (report.wiring || []).some((w) => !w.gauge)) {
                return st('error', 'Error');
            }
            return missing > 0 ? st('warn', 'Faltan ' + missing + (missing === 1 ? ' tramo' : ' tramos'))
                : (report.wiring || []).length ? st('ok', 'Cubiertos') : none;
        }
        if (sec.key === 'terminal') {
            const missing = (report.terminals || []).filter((t) => !t.covered).length;
            return missing > 0 ? st('info', 'Faltan ' + missing) : (report.terminals || []).length ? st('ok', 'Cubiertas') : none;
        }
        if (worst === 2) {
            return st('error', 'Error');
        }
        if (worst === 1) {
            return st('warn', 'Revisar');
        }
        if (empty) {
            if (sec.key === 'inverter') {
                return st('error', 'Falta');
            }
            if (sec.key === 'controller' && inverterHasCharger() === true) {
                return st('muted', 'No hace falta');
            }
            return st('muted', 'Opcional');
        }
        return st('ok', 'Listo');
    }

    function statusFor(sec) {
        return statusInfo(sec).html;
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
            ? '<div class="small mb-2"><i class="bi ' + SEVERITY_ICONS.OK + ' me-1"></i>Todo en orden.</div>'
            : '<div class="small mb-2">' + (errors ? '<span class="badge text-bg-danger me-1">' + errors + (errors === 1 ? ' error' : ' errores') + '</span>' : '') +
              (warnings ? '<span class="badge text-bg-warning">' + warnings + (warnings === 1 ? ' aviso' : ' avisos') + '</span>' : '') + '</div>';
        // Errors first, then warnings, then what is fine; each with a link to the part of the kit it is about.
        const rank = { ERROR: 0, WARNING: 1, OK: 2 };
        const list = report.checks.slice().sort((a, b) => rank[a.severity] - rank[b.severity]).map((c) => {
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
        renderSummaries();
        refreshMoreSummary();
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
        if (kind === 'inverter' || kind === 'controller') {
            refreshSingleOptions();
        }
    }

    sectionsEl.addEventListener('change', (event) => {
        const target = event.target;
        if (target.matches('[data-single]')) {
            const kind = target.dataset.single;
            single[kind].productId = target.value === '' ? null : Number(target.value);
            if (kind === 'inverter' || kind === 'controller') {
                refreshSingleOptions();
            }
            changed();
        } else if (target.matches('[data-row-product]')) {
            const rowEl = target.closest('[data-row]');
            const row = rows[rowEl.dataset.kind].find((r) => r.uid === Number(rowEl.dataset.row));
            row.productId = Number(target.value);
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
        } else if (target.id === 'kit-description') {
            dirty = true;
            refreshMoreSummary();
        } else if (target.id === 'kit-active') {
            dirty = true;
        }
    });

    sectionsEl.addEventListener('click', (event) => {
        const fold = event.target.closest('[data-toggle]');
        if (fold) {
            setOpen(fold.dataset.toggle, !isOpen(fold.dataset.toggle));
            return;
        }
        if (event.target.closest('[data-more]')) {
            toggleMore();
            return;
        }
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
        const link = event.target.closest('a[href^="#sec-"]');
        if (link) {
            event.preventDefault();
            goToSection(link.getAttribute('href').slice('#sec-'.length));
            return;
        }
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
        sectionsEl.innerHTML = '<div class="card">' + SECTIONS.map((sec) => sectionHtml(sec, kit)).join('') + '</div>';
        Object.keys(rows).forEach(renderRows);
        initPhotoManager('kit-photos', kit ? kit.imageUrls : []);
        // The photo list changes by itself (uploads, removals): keep the closed "Más datos" hint current.
        new MutationObserver(refreshMoreSummary).observe(document.querySelector('#kit-photos .photo-list'), { childList: true });

        loadingEl.classList.add('d-none');
        rootEl.classList.remove('d-none');
        renderSubtotals();
        renderSummaries();
        refreshMoreSummary();
        renderSide();
        runReview();
    }

    init();
})();
