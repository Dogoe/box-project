(function () {
    'use strict';

    const token = localStorage.getItem('adminToken');
    if (!token) {
        window.location.href = '/admin/login';
        return;
    }

    const listAlert = document.getElementById('list-alert');
    const tableHead = document.getElementById('entity-table-head');
    const tableBody = document.getElementById('entity-table-body');
    const entityTitle = document.getElementById('entity-title');
    const newBtn = document.getElementById('new-entity-btn');
    const entityTableWrapper = document.getElementById('entity-table-wrapper');
    const settingsPanel = document.getElementById('settings-panel');
    const settingsError = document.getElementById('settings-error');
    const settingsSuccess = document.getElementById('settings-success');
    const marginError = document.getElementById('margin-error');
    const marginSuccess = document.getElementById('margin-success');
    const modalEl = document.getElementById('entity-modal');
    const modal = new bootstrap.Modal(modalEl);
    const modalFormEl = document.getElementById('entity-form');
    const modalFieldsEl = document.getElementById('entity-form-fields');
    const modalTitleEl = document.getElementById('entity-modal-title');
    const modalErrorEl = document.getElementById('entity-modal-error');
    const modalDialogEl = modalEl.querySelector('.modal-dialog');
    const wiringError = document.getElementById('wiring-error');
    const wiringSuccess = document.getElementById('wiring-success');

    let currentEntity = null;
    let editingId = null;
    let allProducts = [];

    document.getElementById('logout-btn').addEventListener('click', () => {
        localStorage.removeItem('adminToken');
        window.location.href = '/admin/login';
    });

    async function authFetch(url, options) {
        const response = await fetch(url, Object.assign({}, options, {
            headers: Object.assign({}, (options && options.headers) || {}, {
                'Authorization': 'Bearer ' + token
            })
        }));

        if (response.status === 401 || response.status === 403) {
            localStorage.removeItem('adminToken');
            window.location.href = '/admin/login';
            throw new Error('No autorizado');
        }

        return response;
    }

    async function errorMessage(response, fallback) {
        const body = await response.json().catch(() => null);
        return (body && body.message) || fallback;
    }

    const WIRE_GAUGES = [
        { value: 'AWG_14', label: '14 AWG' },
        { value: 'AWG_12', label: '12 AWG' },
        { value: 'AWG_10', label: '10 AWG' },
        { value: 'AWG_8', label: '8 AWG' },
        { value: 'AWG_6', label: '6 AWG' },
        { value: 'AWG_4', label: '4 AWG' },
        { value: 'AWG_2', label: '2 AWG' },
        { value: 'AWG_1_0', label: '1/0 AWG' },
        { value: 'AWG_2_0', label: '2/0 AWG' },
        { value: 'AWG_4_0', label: '4/0 AWG' }
    ];
    const WIRE_GAUGE_LABELS = Object.fromEntries(WIRE_GAUGES.map((g) => [g.value, g.label]));

    const ENTITY_CONFIGS = {
        panels: {
            label: 'Paneles Solares',
            endpoint: '/api/catalog/panels',
            columns: [
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'wattage', label: 'Potencia', suffix: ' W' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrl', label: 'URL de imagen', type: 'text' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'wattage', label: 'Potencia (W)', type: 'number', required: true },
                { key: 'technology', label: 'Tecnología', type: 'select', required: true,
                    options: ['MONOCRYSTALLINE', 'POLYCRYSTALLINE', 'THIN_FILM'] },
                { key: 'efficiencyPercent', label: 'Eficiencia (%)', type: 'number', step: '0.1' },
                { key: 'voc', label: 'Voc (V)', type: 'number', step: '0.1' },
                { key: 'isc', label: 'Isc (A)', type: 'number', step: '0.1' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        batteries: {
            label: 'Baterías',
            endpoint: '/api/catalog/batteries',
            columns: [
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'voltage', label: 'Voltaje', suffix: ' V' },
                { key: 'capacityAh', label: 'Capacidad', suffix: ' Ah' },
                { key: 'capacityKwh', label: 'Energía', suffix: ' kWh' },
                { key: 'chemistry', label: 'Química' },
                { key: 'depthOfDischargePercent', label: 'Descarga útil', suffix: ' %' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrl', label: 'URL de imagen', type: 'text' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'capacityAh', label: 'Capacidad (Ah)', type: 'number', step: '0.1', required: true },
                { key: 'voltage', label: 'Voltaje nominal (V)', type: 'number', step: '0.1', required: true },
                { key: 'chemistry', label: 'Química (define la profundidad de descarga)', type: 'select', required: true,
                    options: ['LIFEPO4', 'LITHIUM_ION', 'LEAD_ACID', 'GEL', 'AGM'], defaultValue: 'LIFEPO4' },
                { key: 'cycleLife', label: 'Ciclos de vida', type: 'number' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        inverters: {
            label: 'Inversores',
            endpoint: '/api/catalog/inverters',
            columns: [
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'ratedPowerKw', label: 'Potencia', suffix: ' kW' },
                { key: 'batteryVoltage', label: 'Batería', suffix: ' V' },
                { key: 'mpptMaxChargeCurrentA', label: 'Carga MPPT', suffix: ' A' },
                { key: 'effectiveMaxPvPowerW', label: 'PV máx.', format: (item) => item.effectiveMaxPvPowerW > 0
                    ? Math.round(item.effectiveMaxPvPowerW) + ' W' + (item.maxPvPowerEstimated ? ' (est.)' : '') : '' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrl', label: 'URL de imagen', type: 'text' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'ratedPowerKw', label: 'Potencia nominal (kW)', type: 'number', step: '0.1', required: true },
                { key: 'surgePowerKw', label: 'Potencia pico (kW)', type: 'number', step: '0.1', required: true },
                { key: 'inverterType', label: 'Tipo', type: 'select', required: true,
                    options: ['HYBRID', 'OFF_GRID', 'STRING', 'MICRO'], defaultValue: 'HYBRID' },
                { key: 'batteryVoltage', label: 'Voltaje de baterías (V)', type: 'select', required: true,
                    options: [12, 24, 48], defaultValue: 48, numeric: true },
                { key: 'mpptChannels', label: 'Canales MPPT', type: 'number', defaultValue: 1 },
                { key: 'maxInputVoltage', label: 'MPPT: voltaje PV máx. (Voc, V)', type: 'number', step: '0.1', required: true },
                { key: 'mpptMinVoltage', label: 'MPPT: voltaje PV mínimo de operación (V)', type: 'number', step: '0.1' },
                { key: 'mpptMaxInputCurrentA', label: 'MPPT: corriente PV máx. por canal (A)', type: 'number', step: '0.1' },
                { key: 'mpptMaxChargeCurrentA', label: 'MPPT: corriente máx. de carga a batería (A)', type: 'number', step: '0.1', required: true },
                { key: 'maxPvInputPowerW', label: 'MPPT: potencia PV máx. (W) — opcional, dato de la ficha', type: 'number', step: '1' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        structures: {
            label: 'Estructura de Aluminio',
            endpoint: '/api/catalog/structures',
            columns: [
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrl', label: 'URL de imagen', type: 'text' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        cables: {
            label: 'Cables de Conexión',
            endpoint: '/api/catalog/cables',
            columns: [
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'gauge', label: 'Calibre', labels: WIRE_GAUGE_LABELS },
                { key: 'lengthMeters', label: 'Longitud', suffix: ' m' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrl', label: 'URL de imagen', type: 'text' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'gauge', label: 'Calibre', type: 'select', required: true, options: WIRE_GAUGES },
                { key: 'lengthMeters', label: 'Longitud (m)', type: 'number', step: '0.1', required: true },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        }
    };

    function currentEndpoint() {
        return currentEntity === 'kits' ? '/api/catalog/kits' : ENTITY_CONFIGS[currentEntity].endpoint;
    }

    function showEntityTable() {
        newBtn.classList.remove('d-none');
        entityTableWrapper.classList.remove('d-none');
        settingsPanel.classList.add('d-none');
    }

    function showSettingsPanel() {
        newBtn.classList.add('d-none');
        entityTableWrapper.classList.add('d-none');
        settingsPanel.classList.remove('d-none');
    }

    // ---- Generic (panels/batteries/inverters) rendering ----

    function formatValue(item, column) {
        if (column.format) {
            return column.format(item);
        }
        const value = item[column.key];
        if (column.type === 'boolean') {
            return value ? 'Sí' : 'No';
        }
        if (value === null || value === undefined) {
            return '';
        }
        if (column.type === 'link') {
            // Only http(s) links; the API enforces the same, this just guards old/odd data.
            return /^https?:\/\//i.test(value)
                ? '<a href="' + escapeAttr(value) + '" target="_blank" rel="noopener noreferrer" title="' + escapeAttr(value) + '">' +
                  '<i class="bi bi-box-arrow-up-right"></i></a>'
                : '';
        }
        const display = column.labels ? (column.labels[value] || value) : value;
        return (column.prefix || '') + display + (column.suffix || '');
    }

    function renderTableHead(columns) {
        const headerCells = columns.map((c) => '<th>' + c.label + '</th>').join('');
        tableHead.innerHTML = '<tr>' + headerCells + '<th></th></tr>';
    }

    function actionButtonsHtml(id) {
        return '<td class="text-end">' +
            '<button type="button" class="btn btn-sm btn-outline-secondary edit-btn" data-id="' + id + '"><i class="bi bi-pencil"></i></button> ' +
            '<button type="button" class="btn btn-sm btn-outline-danger delete-btn" data-id="' + id + '"><i class="bi bi-trash"></i></button>' +
            '</td>';
    }

    function wireRowActions(onEdit, onDelete) {
        tableBody.querySelectorAll('.edit-btn').forEach((btn) => {
            btn.addEventListener('click', () => onEdit(Number(btn.dataset.id)));
        });
        tableBody.querySelectorAll('.delete-btn').forEach((btn) => {
            btn.addEventListener('click', () => onDelete(Number(btn.dataset.id)));
        });
    }

    async function loadEntity(entityKey) {
        currentEntity = entityKey;
        showEntityTable();
        listAlert.classList.add('d-none');
        const config = ENTITY_CONFIGS[entityKey];
        entityTitle.textContent = config.label;
        renderTableHead(config.columns);

        try {
            const response = await authFetch(config.endpoint);
            const items = await response.json();

            tableBody.innerHTML = items.map((item) => {
                const cells = config.columns.map((c) => '<td>' + formatValue(item, c) + '</td>').join('');
                return '<tr>' + cells + actionButtonsHtml(item.id) + '</tr>';
            }).join('');

            wireRowActions((id) => openModal(id), (id) => deleteItem(id));
        } catch (err) {
            listAlert.textContent = 'No se pudo cargar la lista.';
            listAlert.classList.remove('d-none');
        }
    }

    function fieldInputHtml(field, value) {
        const id = 'field-' + field.key;
        const hasValue = value !== undefined && value !== null;
        const val = hasValue ? value : (field.defaultValue !== undefined ? field.defaultValue : '');
        const requiredAttr = field.required ? 'required' : '';

        if (field.type === 'select') {
            const options = field.options.map((opt) => {
                const optValue = typeof opt === 'object' ? opt.value : opt;
                const optLabel = typeof opt === 'object' ? opt.label : opt;
                return '<option value="' + optValue + '"' + (String(optValue) === String(val) ? ' selected' : '') + '>' + optLabel + '</option>';
            }).join('');
            return '<select class="form-select" id="' + id + '" ' + requiredAttr + '>' + options + '</select>';
        }
        if (field.type === 'textarea') {
            return '<textarea class="form-control" id="' + id + '">' + (val || '') + '</textarea>';
        }
        if (field.type === 'checkbox') {
            return '<div class="form-check"><input type="checkbox" class="form-check-input" id="' + id + '"' +
                (val ? ' checked' : '') + '></div>';
        }
        const step = field.step ? ' step="' + field.step + '"' : '';
        const placeholder = field.placeholder ? ' placeholder="' + field.placeholder + '"' : '';
        return '<input type="' + field.type + '" class="form-control" id="' + id + '" value="' + escapeAttr(val) + '"' + step + placeholder + ' ' + requiredAttr + '>';
    }

    function escapeAttr(text) {
        return String(text == null ? '' : text)
            .replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function renderGenericForm(config, item) {
        modalFieldsEl.innerHTML = config.fields.map((field) =>
            '<div class="mb-3">' +
            '<label class="form-label" for="field-' + field.key + '">' + field.label + '</label>' +
            fieldInputHtml(field, item ? item[field.key] : undefined) +
            '</div>'
        ).join('');
    }

    function collectGenericFormData(config) {
        const data = {};
        config.fields.forEach((field) => {
            const input = document.getElementById('field-' + field.key);
            if (field.type === 'checkbox') {
                data[field.key] = input.checked;
            } else if (field.type === 'number' || field.numeric) {
                data[field.key] = input.value === '' ? null : Number(input.value);
            } else if (field.type === 'url') {
                data[field.key] = input.value.trim() === '' ? null : input.value.trim();
            } else {
                data[field.key] = input.value;
            }
        });
        return data;
    }

    async function openModal(id) {
        const config = ENTITY_CONFIGS[currentEntity];
        editingId = id || null;
        modalErrorEl.classList.add('d-none');
        modalDialogEl.classList.remove('modal-lg');

        if (id) {
            modalTitleEl.textContent = 'Editar producto';
            const response = await authFetch(config.endpoint + '/' + id);
            renderGenericForm(config, await response.json());
        } else {
            modalTitleEl.textContent = 'Nuevo producto';
            renderGenericForm(config, null);
        }
        if (currentEntity === 'inverters') {
            wireInverterPvEstimate();
        }

        modal.show();
    }

    // Mirrors Inverter.ESTIMATED_PV_TO_CHARGE_POWER_RATIO on the server.
    const PV_ESTIMATE_RATIO = 1.3;

    function wireInverterPvEstimate() {
        const pvInput = document.getElementById('field-maxPvInputPowerW');
        const voltageInput = document.getElementById('field-batteryVoltage');
        const chargeInput = document.getElementById('field-mpptMaxChargeCurrentA');
        const update = () => {
            const estimate = Number(voltageInput.value) * Number(chargeInput.value) * PV_ESTIMATE_RATIO;
            pvInput.placeholder = estimate > 0
                ? 'Vacío = se usa el estimado ~' + Math.round(estimate) + ' W (carga × voltaje × 1.3)'
                : 'Vacío = se estima con la corriente de carga × voltaje × 1.3';
        };
        voltageInput.addEventListener('change', update);
        chargeInput.addEventListener('input', update);
        update();
    }

    // ---- Kits (own logic: nested items referencing existing products) ----

    async function loadAllProducts() {
        const [panels, batteries, inverters, structures, cables] = await Promise.all([
            authFetch('/api/catalog/panels').then((r) => r.json()),
            authFetch('/api/catalog/batteries').then((r) => r.json()),
            authFetch('/api/catalog/inverters').then((r) => r.json()),
            authFetch('/api/catalog/structures').then((r) => r.json()),
            authFetch('/api/catalog/cables').then((r) => r.json())
        ]);
        allProducts = panels.concat(batteries, inverters, structures, cables).map((p) => ({
            id: p.id,
            label: p.sku + ' — ' + p.name + ' ($' + p.price + ')'
        }));
    }

    function productOptionsHtml(selectedId) {
        return allProducts.map((p) =>
            '<option value="' + p.id + '"' + (p.id === selectedId ? ' selected' : '') + '>' + p.label + '</option>'
        ).join('');
    }

    function kitItemRowHtml(item) {
        const productId = item ? item.productId : (allProducts[0] && allProducts[0].id);
        const quantity = item ? item.quantity : 1;
        return '<div class="row g-2 mb-2 kit-item-row align-items-center">' +
            '<div class="col-7"><select class="form-select form-select-sm kit-item-product">' + productOptionsHtml(productId) + '</select></div>' +
            '<div class="col-3"><input type="number" class="form-control form-control-sm kit-item-qty" min="1" value="' + quantity + '"></div>' +
            '<div class="col-2"><button type="button" class="btn btn-sm btn-outline-danger remove-kit-item"><i class="bi bi-trash"></i></button></div>' +
            '</div>';
    }

    function wireKitItemRemoveButtons() {
        document.querySelectorAll('.remove-kit-item').forEach((btn) => {
            btn.onclick = () => {
                btn.closest('.kit-item-row').remove();
                scheduleCompatibilityCheck();
            };
        });
    }

    function collectKitItems() {
        return Array.from(document.querySelectorAll('.kit-item-row')).map((row) => ({
            productId: Number(row.querySelector('.kit-item-product').value),
            quantity: Number(row.querySelector('.kit-item-qty').value)
        }));
    }

    // ---- Kit compatibility (live, while editing) ----

    const SEVERITY_ICONS = {
        OK: 'bi-check-circle-fill text-success',
        WARNING: 'bi-exclamation-triangle-fill text-warning',
        ERROR: 'bi-x-circle-fill text-danger'
    };
    let compatibilityTimer = null;
    let compatibilityRequestId = 0;

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function scheduleCompatibilityCheck() {
        clearTimeout(compatibilityTimer);
        compatibilityTimer = setTimeout(runCompatibilityCheck, 300);
    }

    async function runCompatibilityCheck() {
        const panel = document.getElementById('kit-compat-panel');
        if (!panel) {
            return;
        }
        const items = collectKitItems();
        if (items.length === 0 || items.some((i) => !i.productId || !(i.quantity > 0))) {
            panel.innerHTML = '';
            return;
        }

        const requestId = ++compatibilityRequestId;
        const response = await authFetch('/api/catalog/kits/validate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(items)
        });
        if (requestId !== compatibilityRequestId) {
            return;
        }
        panel.innerHTML = response.ok
            ? compatibilityHtml(await response.json())
            : '<p class="small text-muted mb-0">No se pudo revisar la compatibilidad.</p>';
    }

    function compatibilityHtml(report) {
        const summary = [
            report.batteryBank ? '<div><strong>Banco de baterías:</strong> ' + escapeHtml(report.batteryBank) + '</div>' : '',
            report.panelArray ? '<div><strong>Paneles:</strong> ' + escapeHtml(report.panelArray) + '</div>' : ''
        ].join('');

        const checks = report.checks.length
            ? '<ul class="list-unstyled small mb-0">' + report.checks.map((c) =>
                '<li class="mb-1"><i class="bi ' + SEVERITY_ICONS[c.severity] + ' me-1"></i>' + escapeHtml(c.message) + '</li>'
            ).join('') + '</ul>'
            : '<p class="small text-muted mb-0">Agrega inversor, baterías y paneles para revisar compatibilidad.</p>';

        const wiring = report.wiring.length
            ? '<table class="table table-sm small mt-3 mb-0"><thead><tr><th>Tramo</th><th>Corriente</th><th>Calibre</th><th>Caída</th><th>Fusible</th></tr></thead><tbody>' +
              report.wiring.map((w) =>
                  '<tr><td>' + escapeHtml(w.segment) + '</td>' +
                  '<td>' + w.operatingCurrentA + ' A</td>' +
                  '<td>' + (w.gaugeLabel ? escapeHtml(w.gaugeLabel) : '—') + ' · ' + w.lengthMeters + ' m</td>' +
                  '<td>' + (w.gaugeLabel ? w.voltageDropPercent + ' %' : '—') + '</td>' +
                  '<td>' + (w.fuseA ? w.fuseA + ' A' : '—') + '</td></tr>'
              ).join('') + '</tbody></table>'
            : '';

        return '<div class="border rounded p-3 bg-light">' +
            '<h6 class="fw-semibold mb-2"><i class="bi bi-plug"></i> Revisión de compatibilidad</h6>' +
            (summary ? '<div class="small mb-2">' + summary + '</div>' : '') +
            checks + wiring +
            '</div>';
    }

    async function loadKits() {
        currentEntity = 'kits';
        showEntityTable();
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Kits';
        tableHead.innerHTML = '<tr><th>Nombre</th><th>Costo / Venta / Ganancia</th><th># Ítems</th><th>Activo</th><th></th></tr>';

        try {
            await loadAllProducts();
            const response = await authFetch('/api/catalog/kits');
            const kits = await response.json();

            tableBody.innerHTML = kits.map((kit) => {
                const margin = (kit.sellPrice - kit.computedPrice).toFixed(2);
                const priceCell = 'Costo: $' + kit.computedPrice + ' · Venta: $' + kit.sellPrice + ' · Ganancia: $' + margin;
                return '<tr><td>' + kit.name + '</td><td>' + priceCell + '</td><td>' + kit.items.length + '</td>' +
                    '<td>' + (kit.active ? 'Sí' : 'No') + '</td>' + actionButtonsHtml(kit.id) + '</tr>';
            }).join('');

            wireRowActions((id) => openKitModal(id), (id) => deleteItem(id));
        } catch (err) {
            listAlert.textContent = 'No se pudo cargar la lista de kits.';
            listAlert.classList.remove('d-none');
        }
    }

    async function openKitModal(id) {
        editingId = id || null;
        modalErrorEl.classList.add('d-none');
        await loadAllProducts();

        let kit = null;
        if (id) {
            const response = await authFetch('/api/catalog/kits/' + id);
            kit = await response.json();
        }

        modalTitleEl.textContent = id ? 'Editar kit' : 'Nuevo kit';

        const itemsHtml = kit && kit.items.length
            ? kit.items.map((item) => kitItemRowHtml(item)).join('')
            : kitItemRowHtml(null);

        modalFieldsEl.innerHTML =
            '<div class="mb-3"><label class="form-label">Nombre</label>' +
            '<input type="text" class="form-control" id="kit-name" value="' + (kit ? kit.name : '') + '" required></div>' +
            '<div class="mb-3"><label class="form-label">Descripción</label>' +
            '<textarea class="form-control" id="kit-description">' + (kit && kit.description ? kit.description : '') + '</textarea></div>' +
            '<div class="mb-3"><label class="form-label">Precio manual (opcional; si se deja vacío se calcula sumando sus productos)</label>' +
            '<input type="number" step="0.01" class="form-control" id="kit-price-override" value="' + (kit && kit.priceOverride != null ? kit.priceOverride : '') + '"></div>' +
            '<div class="mb-3"><label class="form-label">URL de imagen (opcional)</label>' +
            '<input type="text" class="form-control" id="kit-image-url" value="' + (kit && kit.imageUrl ? kit.imageUrl : '') + '"></div>' +
            '<div class="form-check mb-3"><input type="checkbox" class="form-check-input" id="kit-active"' + (!kit || kit.active ? ' checked' : '') + '>' +
            '<label class="form-check-label" for="kit-active">Activo</label></div>' +
            '<label class="form-label">Productos incluidos</label>' +
            '<div id="kit-items-container">' + itemsHtml + '</div>' +
            '<button type="button" class="btn btn-sm btn-outline-success mt-2" id="add-kit-item-btn">' +
            '<i class="bi bi-plus-lg"></i> Agregar producto</button>' +
            '<div id="kit-compat-panel" class="mt-3"></div>';

        const itemsContainer = document.getElementById('kit-items-container');
        itemsContainer.addEventListener('change', scheduleCompatibilityCheck);
        itemsContainer.addEventListener('input', scheduleCompatibilityCheck);
        document.getElementById('add-kit-item-btn').addEventListener('click', () => {
            itemsContainer.insertAdjacentHTML('beforeend', kitItemRowHtml(null));
            wireKitItemRemoveButtons();
            scheduleCompatibilityCheck();
        });
        wireKitItemRemoveButtons();

        modalDialogEl.classList.add('modal-lg');
        modal.show();
        runCompatibilityCheck();
    }

    function collectKitFormData() {
        const items = collectKitItems();
        const priceOverrideInput = document.getElementById('kit-price-override').value;
        const imageUrlInput = document.getElementById('kit-image-url').value;

        return {
            name: document.getElementById('kit-name').value,
            description: document.getElementById('kit-description').value,
            active: document.getElementById('kit-active').checked,
            priceOverride: priceOverrideInput === '' ? null : Number(priceOverrideInput),
            imageUrl: imageUrlInput === '' ? null : imageUrlInput,
            items: items
        };
    }

    // ---- Settings (exchange rate) ----

    async function loadSettings() {
        currentEntity = 'settings';
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Configuración';
        showSettingsPanel();
        settingsError.classList.add('d-none');
        settingsSuccess.classList.add('d-none');
        marginError.classList.add('d-none');
        marginSuccess.classList.add('d-none');

        try {
            const response = await authFetch('/api/settings/exchange-rate');
            const data = await response.json();
            document.getElementById('exchange-rate-input').value = data.usdToMxnRate;
        } catch (err) {
            settingsError.textContent = 'No se pudo cargar el tipo de cambio.';
            settingsError.classList.remove('d-none');
        }

        try {
            const response = await authFetch('/api/settings/profit-margin');
            const data = await response.json();
            document.getElementById('margin-input').value = data.marginPercent;
        } catch (err) {
            marginError.textContent = 'No se pudo cargar el margen de ganancia.';
            marginError.classList.remove('d-none');
        }

        wiringError.classList.add('d-none');
        wiringSuccess.classList.add('d-none');
        try {
            const response = await authFetch('/api/settings/wiring');
            const data = await response.json();
            document.getElementById('wiring-battery-input').value = data.batteryToInverterMeters;
            document.getElementById('wiring-pv-input').value = data.pvToInverterMeters;
        } catch (err) {
            wiringError.textContent = 'No se pudieron cargar las longitudes de cableado.';
            wiringError.classList.remove('d-none');
        }
    }

    document.getElementById('wiring-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        wiringError.classList.add('d-none');
        wiringSuccess.classList.add('d-none');

        const response = await authFetch('/api/settings/wiring', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                batteryToInverterMeters: Number(document.getElementById('wiring-battery-input').value),
                pvToInverterMeters: Number(document.getElementById('wiring-pv-input').value)
            })
        });

        if (response.ok) {
            wiringSuccess.textContent = 'Longitudes de cableado actualizadas.';
            wiringSuccess.classList.remove('d-none');
        } else {
            wiringError.textContent = await errorMessage(response, 'No se pudieron guardar las longitudes.');
            wiringError.classList.remove('d-none');
        }
    });

    document.getElementById('settings-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        settingsError.classList.add('d-none');
        settingsSuccess.classList.add('d-none');

        const rate = Number(document.getElementById('exchange-rate-input').value);
        const response = await authFetch('/api/settings/exchange-rate', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ usdToMxnRate: rate })
        });

        if (response.ok) {
            settingsSuccess.textContent = 'Tipo de cambio actualizado.';
            settingsSuccess.classList.remove('d-none');
        } else {
            settingsError.textContent = await errorMessage(response, 'No se pudo guardar el tipo de cambio.');
            settingsError.classList.remove('d-none');
        }
    });

    document.getElementById('margin-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        marginError.classList.add('d-none');
        marginSuccess.classList.add('d-none');

        const marginPercent = Number(document.getElementById('margin-input').value);
        const response = await authFetch('/api/settings/profit-margin', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ marginPercent: marginPercent })
        });

        if (response.ok) {
            marginSuccess.textContent = 'Margen de ganancia actualizado.';
            marginSuccess.classList.remove('d-none');
        } else {
            marginError.textContent = await errorMessage(response, 'No se pudo guardar el margen de ganancia.');
            marginError.classList.remove('d-none');
        }
    });

    // ---- Shared: delete + create/update submit ----

    async function deleteItem(id) {
        if (!window.confirm('¿Eliminar este producto? Esta acción no se puede deshacer.')) {
            return;
        }
        const response = await authFetch(currentEndpoint() + '/' + id, { method: 'DELETE' });
        if (response.ok) {
            currentEntity === 'kits' ? loadKits() : loadEntity(currentEntity);
        } else {
            window.alert('No se pudo eliminar el producto.');
        }
    }

    modalFormEl.addEventListener('submit', async (event) => {
        event.preventDefault();

        const data = currentEntity === 'kits' ? collectKitFormData() : collectGenericFormData(ENTITY_CONFIGS[currentEntity]);
        const url = editingId ? currentEndpoint() + '/' + editingId : currentEndpoint();
        const method = editingId ? 'PUT' : 'POST';

        const response = await authFetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            modal.hide();
            currentEntity === 'kits' ? loadKits() : loadEntity(currentEntity);
        } else {
            modalErrorEl.textContent = await errorMessage(response, 'No se pudo guardar el producto. Revisa los datos ingresados.');
            modalErrorEl.classList.remove('d-none');
        }
    });

    newBtn.addEventListener('click', () => {
        currentEntity === 'kits' ? openKitModal(null) : openModal(null);
    });

    document.querySelectorAll('.entity-tab').forEach((tab) => {
        tab.addEventListener('click', (event) => {
            event.preventDefault();
            document.querySelectorAll('.entity-tab').forEach((t) => t.classList.remove('active'));
            tab.classList.add('active');
            if (tab.dataset.entity === 'kits') {
                loadKits();
            } else if (tab.dataset.entity === 'settings') {
                loadSettings();
            } else {
                loadEntity(tab.dataset.entity);
            }
        });
    });

    loadEntity('panels');
})();
