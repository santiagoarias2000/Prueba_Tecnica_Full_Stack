-- Datos semilla ficticios (02d). Consistencia: prima = canon x meses; canon colectiva = suma de riesgos ACTIVOS.
-- Ids de riesgo: individuales -> mismo id de su poliza (1, 3, 5); colectivas -> 10.. (poliza 2) y 20.. (poliza 4).
INSERT INTO poliza (id, numero_poliza, tipo, estado, tomador, fecha_inicio_vigencia, fecha_fin_vigencia,
                    meses_vigencia, valor_canon, valor_prima, version)
VALUES (1, 'IND-2026-0001', 'INDIVIDUAL', 'ACTIVA', 'Carlos Pérez',
        DATE '2026-01-01', DATE '2026-12-31', 12, 1500000.00, 18000000.00, 0),
       (2, 'COL-2026-0001', 'COLECTIVA', 'ACTIVA', 'Inmobiliaria Los Andes SAS',
        DATE '2026-02-01', DATE '2026-07-31', 6, 3200000.00, 19200000.00, 0),
       (3, 'IND-2026-0002', 'INDIVIDUAL', 'CANCELADA', 'Laura Torres',
        DATE '2025-06-01', DATE '2026-05-31', 12, 950000.00, 11400000.00, 0),
       (4, 'COL-2026-0002', 'COLECTIVA', 'RENOVADA', 'Administración Conjunto Alameda PH',
        DATE '2026-03-01', DATE '2027-02-28', 12, 3150000.00, 37800000.00, 0),
       (5, 'IND-2026-0003', 'INDIVIDUAL', 'ACTIVA', 'Jorge Ramírez',
        DATE '2026-04-01', DATE '2027-03-31', 12, 2300000.00, 27600000.00, 0);

INSERT INTO riesgo (id, poliza_id, descripcion, arrendatario, arrendador, valor_canon, estado,
                    fecha_inicio, fecha_cancelacion, motivo_cancelacion)
VALUES (1, 1, 'Casa - Calle 45 # 12-30, Medellín', 'Carlos Pérez', 'María Restrepo',
        1500000.00, 'ACTIVO', DATE '2026-01-01', NULL, NULL),
       (10, 2, 'Apto 301 - Cra 15 # 93-20, Bogotá', 'Ana Gómez', 'Inversiones Andes SAS',
        2000000.00, 'ACTIVO', DATE '2026-02-01', NULL, NULL),
       (11, 2, 'Apto 502 - Cra 15 # 93-20, Bogotá', 'Luis Martínez', 'Inversiones Andes SAS',
        1200000.00, 'ACTIVO', DATE '2026-02-01', NULL, NULL),
       (3, 3, 'Apto 204 - Calle 10 # 40-15, Cali', 'Laura Torres', 'Ricardo Salazar',
        950000.00, 'CANCELADO', DATE '2025-06-01', DATE '2026-03-15', 'Solicitud del tomador'),
       (20, 4, 'Torre 1 Apto 101 - Conjunto Alameda, Barranquilla', 'Sofía Herrera', 'Pedro Castillo',
        1800000.00, 'ACTIVO', DATE '2025-03-01', NULL, NULL),
       (21, 4, 'Torre 2 Apto 305 - Conjunto Alameda, Barranquilla', 'Andrés Vargas', 'Claudia Mejía',
        1350000.00, 'ACTIVO', DATE '2025-03-01', NULL, NULL),
       (22, 4, 'Torre 3 Apto 402 - Conjunto Alameda, Barranquilla', 'Diana López', 'Fernando Rojas',
        1100000.00, 'CANCELADO', DATE '2025-03-01', DATE '2025-11-30', 'Terminación del contrato'),
       (5, 5, 'Apto 801 - Av. El Poblado # 5-60, Medellín', 'Jorge Ramírez', 'Gloria Ospina',
        2300000.00, 'ACTIVO', DATE '2026-04-01', NULL, NULL);

ALTER TABLE poliza ALTER COLUMN id RESTART WITH 100;
ALTER TABLE riesgo ALTER COLUMN id RESTART WITH 100;
