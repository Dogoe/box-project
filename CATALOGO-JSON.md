# Archivo de catálogo (exportar / importar)

En el admin, en **Configuración → Catálogo: exportar / importar**, puedes:

- **Descargar catálogo**: todos tus productos y kits en un archivo `.json`. Úsalo como respaldo o para pasarlos a otra tienda (por ejemplo, de tu computadora al sitio en línea).
- **Importar**: eliges un archivo y das **Revisar archivo**. Te muestra qué se crearía, qué se actualizaría y qué errores hay, **sin guardar nada**. Si no hay errores, aparece **Importar N cambios**.

## Reglas al importar

- Los productos se identifican por **SKU** y los kits por **nombre**, nunca por el número interno de la base de datos. Por eso el archivo funciona en cualquier tienda.
- **SKU nuevo → se crea. SKU que ya existe → se actualiza.**
- Al actualizar, **solo cambia lo que trae el archivo**. Por ejemplo, `{ "type": "BATTERY", "sku": "BAT-24V-100AH", "price": 9500 }` cambia solo el precio de esa batería y deja todo lo demás igual.
- **Nunca se borra nada.** Si un producto o kit no viene en el archivo, se queda como está.
- **Todo o nada**: si hay un solo error, no se guarda nada. Corriges el archivo y lo vuelves a revisar.
- Un SKU no puede cambiar de tipo. Si `BAT-100` ya existe como batería, no se puede importar como cable.
- **No se incluyen** la configuración (tipo de cambio, margen, cableado, datos de la tienda), los usuarios ni los pedidos.

## Formato

```json
{
  "format": "baja-solar-catalog",
  "version": 1,
  "products": [ { "type": "SOLAR_PANEL", "sku": "...", ... } ],
  "kits": [ { "name": "...", "items": [ { "sku": "...", "quantity": 1 } ] } ]
}
```

`format` y `version` son opcionales en un archivo hecho a mano. `exportedAt` (la fecha de exportación) se ignora al importar. En el admin, **Descargar ejemplo** te da un archivo completo para copiar.

### Campos de todos los productos

| Campo | Obligatorio | Qué es |
|---|---|---|
| `type` | sí | `SOLAR_PANEL`, `BATTERY`, `INVERTER`, `CHARGE_CONTROLLER`, `CABLE`, `STRUCTURE` o `PROTECTION` |
| `sku` | sí | Tu código único del producto, p. ej. `PNL-550-MONO` |
| `name` | sí | Nombre que ve el cliente |
| `brand` | no | Marca |
| `description` | no | Descripción (máx. 2000 caracteres) |
| `price` | sí | **Tu costo** en pesos (MXN). El precio de venta de los kits sale de tu margen de ganancia |
| `stockQuantity` | no | Piezas en existencia (si no viene en un producto nuevo: 0) |
| `imageUrls` | no | Lista de links de fotos (máx. 8); la primera es la principal |
| `referenceUrl` | no | Link a la página del producto (proveedor o fabricante) |
| `active` | no | `true` o `false`; `false` lo oculta de la tienda (si no viene: `true`) |

Los números van sin comillas (`550`, `21.3`); los textos, con comillas (`"Jinko"`).

### Panel solar: `"type": "SOLAR_PANEL"`

| Campo | Obligatorio | Qué es |
|---|---|---|
| `wattage` | sí | Potencia en W, p. ej. `550` |
| `technology` | sí | `MONOCRYSTALLINE`, `POLYCRYSTALLINE` o `THIN_FILM` |
| `efficiencyPercent` | no | Eficiencia en %, p. ej. `21.3` |
| `voc` | no | Voltaje de circuito abierto (V) |
| `isc` | no | Corriente de corto circuito (A) |
| `connector` | no | Conector del cable del panel: `MC4` (si no viene) o `SAE` (kits chicos de 12 V) |

### Batería: `"type": "BATTERY"`

| Campo | Obligatorio | Qué es |
|---|---|---|
| `capacityAh` | sí | Capacidad en Ah, p. ej. `100` |
| `voltage` | sí | Voltaje nominal: `12`, `24`, `48`… |
| `chemistry` | no | `LIFEPO4` (si no viene), `LITHIUM_ION`, `LEAD_ACID`, `GEL` o `AGM` |
| `cycleLife` | no | Ciclos de vida |
| `bmsMaxDischargeA` | no | Solo litio: corriente máxima continua de descarga de su BMS (A), de la ficha. Sin ella se supone su capacidad en amperes (100 Ah → 100 A) |
| `integratedBreakerA` | no | Breaker de salida que ya trae la batería (A), p. ej. las tipo rack. Con él el kit no pide fusible ni interruptor aparte para esa batería |

Los kWh y la profundidad de descarga se calculan solos. La corriente máxima es la del BMS si la capturas.

### Inversor: `"type": "INVERTER"`

| Campo | Obligatorio | Qué es |
|---|---|---|
| `ratedPowerKw` | sí | Potencia continua en kW, p. ej. `3` |
| `surgePowerKw` | sí | Potencia pico en kW |
| `inverterType` | sí | `OFF_GRID`, `HYBRID`, `STRING` o `MICRO` |
| `batteryVoltage` | no | Voltaje de batería: `12`, `24` o `48` |
| `builtInCharger` | no | `true` si trae cargador solar MPPT (si no viene: `true`); `false` si necesita controlador aparte |
| `mpptChannels` | no | Número de entradas MPPT |
| `maxInputVoltage` | no | Voltaje PV máximo (V) |
| `mpptMinVoltage` | no | Voltaje mínimo de arranque MPPT (V) |
| `mpptMaxChargeCurrentA` | no | Corriente máxima de carga a la batería (A) |
| `mpptMaxInputCurrentA` | no | Corriente PV máxima por entrada (A) |
| `maxPvInputPowerW` | no | Potencia PV máxima (W) |

Con `builtInCharger: false`, los campos `mppt…`, `maxInputVoltage` y `maxPvInputPowerW` se ignoran.

### Controlador de carga MPPT: `"type": "CHARGE_CONTROLLER"`

| Campo | Obligatorio | Qué es |
|---|---|---|
| `supportedBatteryVoltages` | sí | Lista de voltajes que acepta, p. ej. `[12, 24, 48]` |
| `maxChargeCurrentA` | sí | Corriente máxima de carga (A) |
| `maxInputVoltage` | sí | Voltaje PV máximo (V) |
| `mpptMinVoltage` | no | Voltaje mínimo MPPT (V) |
| `mpptMaxInputCurrentA` | no | Corriente PV máxima (A) |
| `maxPvInputPowerW` | no | Potencia PV máxima (W) |
| `mpptChannels` | no | Número de entradas MPPT |

### Cable: `"type": "CABLE"`

| Campo | Obligatorio | Qué es |
|---|---|---|
| `lengthMeters` | sí | Largo en metros |
| `gauge` | sí | `AWG_14`, `AWG_12`, `AWG_10`, `AWG_8`, `AWG_6`, `AWG_4`, `AWG_2`, `AWG_1_0`, `AWG_2_0` o `AWG_4_0` |

### Estructura de aluminio: `"type": "STRUCTURE"`

Solo los campos comunes.

### Protección: `"type": "PROTECTION"`

Fusibles, breakers y desconectadores. Se agregan al kit como cualquier producto (cuentan en su costo) y la revisión del kit dice cuáles necesita.

| Campo | Obligatorio | Qué es |
|---|---|---|
| `protectionType` | sí | `FUSE` (fusible), `DC_BREAKER` (breaker o interruptor DC de batería), `PV_DISCONNECT` (desconectador DC de paneles) o `AC_BREAKER` (breaker AC de salida, con caja) |
| `fuseClass` | solo fusibles | `MRBF`, `ANL`, `CLASS_T`, `GPV` (fusible de cadena de paneles) u `OTHER` |
| `ratedCurrentA` | sí | Corriente nominal (A) |
| `maxVoltage` | sí | Voltaje máximo que aguanta: DC en fusibles, breakers DC y desconectadores; AC en breakers AC |
| `poles` | no | Polos (si no viene: 1). El desconectador de paneles necesita 2 |

Ejemplo:

```json
{ "type": "PROTECTION", "sku": "FUS-MRBF-110", "name": "Fusible MRBF 110 A 58 V con base", "price": 350,
  "protectionType": "FUSE", "fuseClass": "MRBF", "ratedCurrentA": 110, "maxVoltage": 58 }
```

Qué pide la revisión del kit:

- **Fusible principal de batería**: entre la corriente de diseño del tramo batería–inversor y lo que aguanta su cable, con voltaje de al menos 1.2 × el del banco (14.4 / 28.8 / 57.6 V). **Clase T** si es litio con baterías en paralelo o más de 300 A.
- **Interruptor / breaker DC de batería**: al menos la corriente de diseño de ese tramo.
- **Protección controlador – batería** (solo con controlador aparte): fusible o breaker DC, como el fusible principal.
- **Desconectador DC de paneles**: 2 polos, al menos el voltaje de la cadena en frío y 1.25 × su corriente.
- **Fusible por cadena** (`GPV`, solo con 3 o más cadenas en paralelo).
- **Breaker AC de salida**: potencia del inversor ÷ 120 V × 1.25, al tamaño comercial siguiente (o uno más).

Una batería con `integratedBreakerA` cubre el interruptor, y también el fusible si es una sola cadena de baterías y su breaker no pasa de lo que aguanta el cable.

### Kits

| Campo | Obligatorio | Qué es |
|---|---|---|
| `name` | sí | Nombre del kit (único) |
| `description` | no | Descripción |
| `active` | no | `false` lo oculta de la tienda (si no viene: `true`) |
| `priceOverride` | no | Precio de venta fijo; sin él se calcula con tu margen |
| `imageUrls` | no | Fotos propias del kit; sin ellas se arma un collage con las de sus productos |
| `items` | sí | Lista de `{ "sku": "...", "quantity": 2 }` |

Los productos de un kit tienen que venir en el mismo archivo o existir ya en la tienda. Se aplican las mismas reglas que en el admin: un solo inversor y un solo controlador por kit.

## Fotos

Las fotos son links. Las que subiste desde el admin están en tu almacenamiento (Cloudflare R2) y funcionan en cualquier tienda. Si pones el link a la foto de la página de un proveedor, la foto sigue viviendo en su servidor y puede dejar de verse si la cambian o la borran.

## Pasar productos de otra página

Arma el archivo con los campos de arriba, o pídele a una IA algo como: *"Convierte estos productos al formato de este ejemplo"*, pegando el texto del proveedor y el archivo de **Descargar ejemplo**. Después revisa el archivo en el admin; los errores dicen exactamente qué campo falta o está mal.
