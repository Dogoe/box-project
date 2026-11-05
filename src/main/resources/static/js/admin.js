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
    const storeError = document.getElementById('store-error');
    const storeSuccess = document.getElementById('store-success');
    const ordersFilter = document.getElementById('orders-filter');
    const ordersWhatsappWarning = document.getElementById('orders-whatsapp-warning');
    const pendingOrdersBadge = document.getElementById('pending-orders-badge');
    const orderModalEl = document.getElementById('order-modal');
    const orderModal = new bootstrap.Modal(orderModalEl);
    const orderModalTitle = document.getElementById('order-modal-title');
    const orderModalBody = document.getElementById('order-modal-body');
    const orderModalError = document.getElementById('order-modal-error');
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
        ordersFilter.classList.add('d-none');
        ordersWhatsappWarning.classList.add('d-none');
    }

    function showSettingsPanel() {
        newBtn.classList.add('d-none');
        entityTableWrapper.classList.add('d-none');
        settingsPanel.classList.remove('d-none');
        ordersFilter.classList.add('d-none');
        ordersWhatsappWarning.classList.add('d-none');
    }

    function showOrdersTable() {
        newBtn.classList.add('d-none');
        entityTableWrapper.classList.remove('d-none');
        settingsPanel.classList.add('d-none');
        ordersFilter.classList.remove('d-none');
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
            checks + expansionHtml(report.expansion) + wiring +
            '</div>';
    }

    const EXPANSION_BADGE_CLASSES = { EXPANDABLE: 'bg-success', LIMITED: 'bg-warning text-dark', MAXED: 'bg-secondary' };

    function expansionBadgeHtml(expansion) {
        return expansion
            ? '<span class="badge ' + EXPANSION_BADGE_CLASSES[expansion.level] + '">' + escapeHtml(expansion.label) + '</span>'
            : '<span class="text-muted small">—</span>';
    }

    function expansionHtml(expansion) {
        if (!expansion) {
            return '<p class="small text-muted mt-2 mb-0"><strong>Ampliación:</strong> no se puede calcular (revisa los avisos de arriba).</p>';
        }
        const line = (name, aspect) => aspect
            ? '<li><strong>' + name + ':</strong> ' + escapeHtml(aspect.label) + ' — ' + escapeHtml(aspect.detail) + '</li>'
            : '';
        return '<div class="small mt-3"><strong>Ampliación</strong> ' + expansionBadgeHtml(expansion) +
            '<ul class="list-unstyled mt-1 mb-0">' +
            line('Paneles', expansion.panels) +
            line('Baterías', expansion.batteries) +
            '<li><strong>Potencia:</strong> fija en ' + expansion.inverterKw + ' kW (un solo inversor por kit).</li>' +
            '</ul></div>';
    }

    async function loadKits() {
        currentEntity = 'kits';
        showEntityTable();
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Kits';
        tableHead.innerHTML = '<tr><th>Nombre</th><th>Costo / Venta / Ganancia</th><th># Ítems</th><th>Ampliación</th><th>Activo</th><th></th></tr>';

        try {
            await loadAllProducts();
            const [kits, compatibilityById] = await Promise.all([
                authFetch('/api/catalog/kits').then((r) => r.json()),
                authFetch('/api/catalog/kits/compatibility').then((r) => r.json())
            ]);

            tableBody.innerHTML = kits.map((kit) => {
                const margin = (kit.sellPrice - kit.computedPrice).toFixed(2);
                const priceCell = 'Costo: $' + kit.computedPrice + ' · Venta: $' + kit.sellPrice + ' · Ganancia: $' + margin;
                const report = compatibilityById[kit.id];
                return '<tr><td>' + kit.name + '</td><td>' + priceCell + '</td><td>' + kit.items.length + '</td>' +
                    '<td>' + expansionBadgeHtml(report && report.expansion) + '</td>' +
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

        storeError.classList.add('d-none');
        storeSuccess.classList.add('d-none');
        try {
            const response = await authFetch('/api/settings/store');
            const data = await response.json();
            document.getElementById('store-whatsapp-input').value = data.whatsappNumber || '';
            document.getElementById('store-email-input').value = data.notificationEmail || '';
            document.getElementById('store-instructions-input').value = data.paymentInstructions || '';
        } catch (err) {
            storeError.textContent = 'No se pudo cargar la configuración de pedidos.';
            storeError.classList.remove('d-none');
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

    document.getElementById('store-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        storeError.classList.add('d-none');
        storeSuccess.classList.add('d-none');

        const response = await authFetch('/api/settings/store', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                whatsappNumber: document.getElementById('store-whatsapp-input').value.trim() || null,
                notificationEmail: document.getElementById('store-email-input').value.trim() || null,
                paymentInstructions: document.getElementById('store-instructions-input').value
            })
        });

        if (response.ok) {
            const data = await response.json();
            document.getElementById('store-whatsapp-input').value = data.whatsappNumber || '';
            storeSuccess.textContent = 'Configuración de pedidos actualizada.';
            storeSuccess.classList.remove('d-none');
        } else {
            storeError.textContent = await errorMessage(response, 'No se pudo guardar la configuración de pedidos.');
            storeError.classList.remove('d-none');
        }
    });

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

    // ---- Orders ----

    const ORDER_STATUS_BADGES = {
        PENDING: 'bg-warning text-dark',
        PAID: 'bg-info text-dark',
        DELIVERED: 'bg-success',
        CANCELLED: 'bg-secondary'
    };
    const mxnFormatter = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    let ordersStatusFilter = '';

    function money(value) {
        return '$' + mxnFormatter.format(Number(value)) + ' MXN';
    }

    function dateTime(value) {
        return value ? new Date(value).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' }) : '';
    }

    function statusBadge(order) {
        return '<span class="badge ' + ORDER_STATUS_BADGES[order.status] + '">' + escapeHtml(order.statusLabel) + '</span>';
    }

    function fullAddress(order) {
        return order.street + ', Col. ' + order.neighborhood + ', C.P. ' + order.postalCode + ', ' + order.city;
    }

    async function refreshPendingBadge() {
        try {
            const pending = await authFetch('/api/admin/orders?status=PENDING').then((r) => r.json());
            pendingOrdersBadge.textContent = pending.length;
            pendingOrdersBadge.classList.toggle('d-none', pending.length === 0);
        } catch (err) {
            // badge is a convenience; ignore failures
        }
    }

    async function loadOrders() {
        currentEntity = 'orders';
        showOrdersTable();
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Pedidos';
        ordersFilter.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b.dataset.status === ordersStatusFilter));
        tableHead.innerHTML = '<tr><th>Folio</th><th>Fecha</th><th>Cliente</th><th>Kits</th><th class="text-end">Total</th><th>Estado</th><th></th></tr>';
        authFetch('/api/settings/store')
            .then((r) => r.json())
            .then((store) => ordersWhatsappWarning.classList.toggle('d-none', Boolean(store.whatsappNumber)))
            .catch(() => ordersWhatsappWarning.classList.add('d-none'));

        try {
            const url = '/api/admin/orders' + (ordersStatusFilter ? '?status=' + ordersStatusFilter : '');
            const orders = await authFetch(url).then((r) => r.json());
            tableBody.innerHTML = orders.length
                ? orders.map((order) =>
                    '<tr><td class="fw-semibold">' + escapeHtml(order.folio) + '</td>' +
                    '<td class="small">' + dateTime(order.createdAt) + '</td>' +
                    '<td>' + escapeHtml(order.customerName) + accountBadge(order) + '<div class="small text-muted">' + escapeHtml(order.customerPhone) + '</div></td>' +
                    '<td class="small">' + order.items.map((i) => i.quantity + ' × ' + escapeHtml(i.kitName)).join('<br>') + '</td>' +
                    '<td class="text-end">' + money(order.totalMxn) + '</td>' +
                    '<td>' + statusBadge(order) + '</td>' +
                    '<td class="text-end"><button type="button" class="btn btn-sm btn-outline-secondary order-open-btn" data-id="' + order.id + '">Ver</button></td></tr>'
                ).join('')
                : '<tr><td colspan="7" class="text-center text-muted py-4">No hay pedidos' + (ordersStatusFilter ? ' con este estado' : '') + '.</td></tr>';
            tableBody.querySelectorAll('.order-open-btn').forEach((btn) => {
                btn.addEventListener('click', () => openOrderModal(Number(btn.dataset.id)));
            });
        } catch (err) {
            listAlert.textContent = 'No se pudo cargar la lista de pedidos.';
            listAlert.classList.remove('d-none');
        }
        refreshPendingBadge();
    }

    document.getElementById('orders-whatsapp-settings-link').addEventListener('click', (event) => {
        event.preventDefault();
        document.querySelector('.entity-tab[data-entity="settings"]').click();
    });

    ordersFilter.querySelectorAll('button').forEach((btn) => {
        btn.addEventListener('click', () => {
            ordersStatusFilter = btn.dataset.status;
            loadOrders();
        });
    });

    // wa.me opens WhatsApp with this text prefilled; the admin reviews it and presses send.
    function whatsappToCustomerUrl(order) {
        const greeting = 'Hola ' + order.customerName.split(' ')[0] + ', ';
        const messages = {
            PENDING: greeting + 'recibimos tu pedido ' + order.folio + ' por ' + money(order.totalMxn) +
                '. ¿Cuándo te queda bien para coordinar el pago en efectivo y la entrega en ' + fullAddress(order) + '?',
            PAID: greeting + 'confirmamos tu pago del pedido ' + order.folio + '. Te avisamos para coordinar la entrega.',
            DELIVERED: greeting + 'gracias por tu compra (pedido ' + order.folio + '). Cualquier duda con tu sistema, aquí estamos.',
            CANCELLED: greeting + 'tu pedido ' + order.folio + ' fue cancelado. Si tienes dudas, con gusto te ayudamos.'
        };
        return 'https://wa.me/52' + order.customerPhone + '?text=' + encodeURIComponent(messages[order.status]);
    }

    function orderTimelineHtml(order) {
        const lines = ['<li>Recibido: ' + dateTime(order.createdAt) + '</li>'];
        if (order.paidAt) {
            lines.push('<li>Pagado: ' + dateTime(order.paidAt) + ' · ' + money(order.paidAmountMxn) + ' · ' + escapeHtml(order.paidBy) +
                (order.paymentNote ? ' · ' + escapeHtml(order.paymentNote) : '') + '</li>');
        }
        if (order.deliveredAt) {
            lines.push('<li>Entregado: ' + dateTime(order.deliveredAt) + ' · ' + escapeHtml(order.deliveredBy) + '</li>');
        }
        if (order.cancelledAt) {
            lines.push('<li>Cancelado: ' + dateTime(order.cancelledAt) + ' · ' + escapeHtml(order.cancelledBy) + ' · ' + escapeHtml(order.cancelReason) + '</li>');
        }
        return '<ul class="small mb-0">' + lines.join('') + '</ul>';
    }

    function orderActionsHtml(order) {
        if (order.status === 'PENDING') {
            return '<div class="border rounded p-3 bg-light">' +
                '<h6 class="fw-semibold">Registrar pago en efectivo</h6>' +
                '<div class="row g-2 align-items-end">' +
                '<div class="col-md-4"><label class="form-label small" for="order-amount">Monto recibido (MXN)</label>' +
                '<input type="number" step="0.01" min="0.01" class="form-control form-control-sm" id="order-amount" value="' + order.totalMxn + '"></div>' +
                '<div class="col-md-8"><label class="form-label small" for="order-payment-note">Nota (opcional)</label>' +
                '<input type="text" class="form-control form-control-sm" id="order-payment-note" maxlength="500" placeholder="Ej. pagó al instalar"></div>' +
                '</div>' +
                '<div class="d-flex gap-2 mt-2">' +
                '<button type="button" class="btn btn-success btn-sm" data-order-action="pay">Confirmar pago</button>' +
                '<button type="button" class="btn btn-outline-success btn-sm" data-order-action="pay-and-deliver">Cobrar y entregar</button>' +
                '</div></div>' + cancelHtml();
        }
        if (order.status === 'PAID') {
            return '<button type="button" class="btn btn-success btn-sm" data-order-action="deliver">Marcar entregado</button>' + cancelHtml();
        }
        return '';
    }

    function cancelHtml() {
        return '<div class="border rounded p-3 mt-3">' +
            '<label class="form-label small" for="order-cancel-reason">Motivo de cancelación</label>' +
            '<div class="input-group input-group-sm">' +
            '<input type="text" class="form-control" id="order-cancel-reason" maxlength="500">' +
            '<button type="button" class="btn btn-outline-danger" data-order-action="cancel">Cancelar pedido</button>' +
            '</div></div>';
    }

    /** Marks orders placed from a customer account (vs. guest checkout). */
    function accountBadge(order) {
        return order.registeredCustomer
            ? ' <span class="badge text-bg-info" title="Pedido hecho con cuenta de cliente"><i class="bi bi-person-check"></i> Cuenta</span>'
            : '';
    }

    function renderOrder(order) {
        orderModalTitle.innerHTML = 'Pedido ' + escapeHtml(order.folio) + ' ' + statusBadge(order);
        orderModalBody.innerHTML =
            '<div class="row g-3 mb-3">' +
            '<div class="col-md-6"><h6 class="fw-semibold mb-1">Cliente</h6>' +
            '<div>' + escapeHtml(order.customerName) + accountBadge(order) + '</div>' +
            '<div class="small">' + escapeHtml(order.customerPhone) + (order.customerEmail ? ' · ' + escapeHtml(order.customerEmail) : '') + '</div>' +
            '<a class="btn btn-success btn-sm mt-2" target="_blank" rel="noopener noreferrer" href="' + escapeAttr(whatsappToCustomerUrl(order)) + '">' +
            '<i class="bi bi-whatsapp"></i> Escribir por WhatsApp</a></div>' +
            '<div class="col-md-6"><h6 class="fw-semibold mb-1">Entrega</h6>' +
            '<div class="small">' + escapeHtml(fullAddress(order)) + '</div>' +
            (order.addressReferences ? '<div class="small text-muted">Ref.: ' + escapeHtml(order.addressReferences) + '</div>' : '') +
            (order.notes ? '<div class="small mt-1"><strong>Notas:</strong> ' + escapeHtml(order.notes) + '</div>' : '') +
            '<a class="small d-inline-block mt-2" target="_blank" rel="noopener noreferrer" href="/pedido/' + encodeURIComponent(order.folio) + '">Ver página del cliente</a>' +
            '</div></div>' +
            '<table class="table table-sm small"><thead><tr><th>Kit</th><th class="text-center">Cant.</th><th class="text-end">Precio</th><th class="text-end">Subtotal</th></tr></thead><tbody>' +
            order.items.map((i) => '<tr><td>' + escapeHtml(i.kitName) + '</td><td class="text-center">' + i.quantity + '</td>' +
                '<td class="text-end">' + money(i.unitPriceMxn) + '</td><td class="text-end">' + money(i.lineTotalMxn) + '</td></tr>').join('') +
            '</tbody><tfoot><tr><th colspan="3" class="text-end">Total</th><th class="text-end">' + money(order.totalMxn) + '</th></tr></tfoot></table>' +
            '<div class="mb-3">' + orderTimelineHtml(order) + '</div>' +
            orderActionsHtml(order);

        orderModalBody.querySelectorAll('[data-order-action]').forEach((btn) => {
            btn.addEventListener('click', () => runOrderAction(order, btn.dataset.orderAction));
        });
    }

    async function openOrderModal(id) {
        orderModalError.classList.add('d-none');
        const response = await authFetch('/api/admin/orders/' + id);
        renderOrder(await response.json());
        orderModal.show();
    }

    async function runOrderAction(order, action) {
        orderModalError.classList.add('d-none');
        let body = null;
        if (action === 'pay' || action === 'pay-and-deliver') {
            body = {
                amountMxn: Number(document.getElementById('order-amount').value),
                note: document.getElementById('order-payment-note').value.trim() || null
            };
        } else if (action === 'cancel') {
            const reason = document.getElementById('order-cancel-reason').value.trim();
            if (!reason) {
                orderModalError.textContent = 'Indica el motivo de la cancelación.';
                orderModalError.classList.remove('d-none');
                return;
            }
            if (!window.confirm('¿Cancelar el pedido ' + order.folio + '?')) {
                return;
            }
            body = { reason: reason };
        }

        const response = await authFetch('/api/admin/orders/' + order.id + '/' + action, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined
        });
        if (!response.ok) {
            orderModalError.textContent = await errorMessage(response, 'No se pudo actualizar el pedido.');
            orderModalError.classList.remove('d-none');
            return;
        }
        renderOrder(await response.json());
        loadOrders();
    }

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
            } else if (tab.dataset.entity === 'orders') {
                loadOrders();
            } else if (tab.dataset.entity === 'settings') {
                loadSettings();
            } else {
                loadEntity(tab.dataset.entity);
            }
        });
    });

    loadEntity('panels');
    refreshPendingBadge();
})();
