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
    const modalEl = document.getElementById('entity-modal');
    const modal = new bootstrap.Modal(modalEl);
    const modalFormEl = document.getElementById('entity-form');
    const modalFieldsEl = document.getElementById('entity-form-fields');
    const modalTitleEl = document.getElementById('entity-modal-title');
    const modalErrorEl = document.getElementById('entity-modal-error');

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
                { key: 'capacityKwh', label: 'Capacidad', suffix: ' kWh' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
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
                { key: 'capacityKwh', label: 'Capacidad (kWh)', type: 'number', step: '0.01', required: true },
                { key: 'voltage', label: 'Voltaje (V)', type: 'number', step: '0.1', required: true },
                { key: 'chemistry', label: 'Química', type: 'select', required: true,
                    options: ['LITHIUM_ION', 'LEAD_ACID', 'GEL', 'AGM'] },
                { key: 'depthOfDischargePercent', label: 'Profundidad de descarga (%)', type: 'number', step: '0.1' },
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
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
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
                { key: 'ratedPowerKw', label: 'Potencia nominal (kW)', type: 'number', step: '0.1', required: true },
                { key: 'surgePowerKw', label: 'Potencia pico (kW)', type: 'number', step: '0.1', required: true },
                { key: 'inverterType', label: 'Tipo', type: 'select', required: true,
                    options: ['STRING', 'HYBRID', 'MICRO', 'OFF_GRID'] },
                { key: 'mpptChannels', label: 'Canales MPPT', type: 'number' },
                { key: 'maxInputVoltage', label: 'Voltaje máx. de entrada (V)', type: 'number', step: '0.1' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        }
    };

    function currentEndpoint() {
        return currentEntity === 'kits' ? '/api/catalog/kits' : ENTITY_CONFIGS[currentEntity].endpoint;
    }

    // ---- Generic (panels/batteries/inverters) rendering ----

    function formatValue(item, column) {
        const value = item[column.key];
        if (column.type === 'boolean') {
            return value ? 'Sí' : 'No';
        }
        if (value === null || value === undefined) {
            return '';
        }
        return (column.prefix || '') + value + (column.suffix || '');
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
            const options = field.options.map((opt) =>
                '<option value="' + opt + '"' + (opt === val ? ' selected' : '') + '>' + opt + '</option>'
            ).join('');
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
        return '<input type="' + field.type + '" class="form-control" id="' + id + '" value="' + val + '"' + step + ' ' + requiredAttr + '>';
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
            } else if (field.type === 'number') {
                data[field.key] = input.value === '' ? null : Number(input.value);
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

        if (id) {
            modalTitleEl.textContent = 'Editar producto';
            const response = await authFetch(config.endpoint + '/' + id);
            renderGenericForm(config, await response.json());
        } else {
            modalTitleEl.textContent = 'Nuevo producto';
            renderGenericForm(config, null);
        }

        modal.show();
    }

    // ---- Kits (own logic: nested items referencing existing products) ----

    async function loadAllProducts() {
        const [panels, batteries, inverters] = await Promise.all([
            authFetch('/api/catalog/panels').then((r) => r.json()),
            authFetch('/api/catalog/batteries').then((r) => r.json()),
            authFetch('/api/catalog/inverters').then((r) => r.json())
        ]);
        allProducts = panels.concat(batteries, inverters).map((p) => ({
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
            btn.onclick = () => btn.closest('.kit-item-row').remove();
        });
    }

    async function loadKits() {
        currentEntity = 'kits';
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Kits';
        tableHead.innerHTML = '<tr><th>Nombre</th><th>Precio</th><th># Ítems</th><th>Activo</th><th></th></tr>';

        try {
            await loadAllProducts();
            const response = await authFetch('/api/catalog/kits');
            const kits = await response.json();

            tableBody.innerHTML = kits.map((kit) => {
                const price = kit.priceOverride != null ? kit.priceOverride : kit.computedPrice;
                return '<tr><td>' + kit.name + '</td><td>$' + price + '</td><td>' + kit.items.length + '</td>' +
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
            '<div class="form-check mb-3"><input type="checkbox" class="form-check-input" id="kit-active"' + (!kit || kit.active ? ' checked' : '') + '>' +
            '<label class="form-check-label" for="kit-active">Activo</label></div>' +
            '<label class="form-label">Productos incluidos</label>' +
            '<div id="kit-items-container">' + itemsHtml + '</div>' +
            '<button type="button" class="btn btn-sm btn-outline-success mt-2" id="add-kit-item-btn">' +
            '<i class="bi bi-plus-lg"></i> Agregar producto</button>';

        document.getElementById('add-kit-item-btn').addEventListener('click', () => {
            document.getElementById('kit-items-container').insertAdjacentHTML('beforeend', kitItemRowHtml(null));
            wireKitItemRemoveButtons();
        });
        wireKitItemRemoveButtons();

        modal.show();
    }

    function collectKitFormData() {
        const items = Array.from(document.querySelectorAll('.kit-item-row')).map((row) => ({
            productId: Number(row.querySelector('.kit-item-product').value),
            quantity: Number(row.querySelector('.kit-item-qty').value)
        }));
        const priceOverrideInput = document.getElementById('kit-price-override').value;

        return {
            name: document.getElementById('kit-name').value,
            description: document.getElementById('kit-description').value,
            active: document.getElementById('kit-active').checked,
            priceOverride: priceOverrideInput === '' ? null : Number(priceOverrideInput),
            items: items
        };
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
            tab.dataset.entity === 'kits' ? loadKits() : loadEntity(tab.dataset.entity);
        });
    });

    loadEntity('panels');
})();
