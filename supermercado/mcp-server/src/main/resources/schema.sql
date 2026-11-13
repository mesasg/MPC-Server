-- Base de datos del supermercado. Se puede ejecutar varias veces sin duplicar datos.
-- mysql -u root -p -e "source C:/ruta/al/proyecto/supermercado/mcp-server/src/main/resources/schema.sql"

CREATE DATABASE IF NOT EXISTS supermercado_mcp
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE supermercado_mcp;

CREATE TABLE IF NOT EXISTS producto (
    id        BIGINT         NOT NULL AUTO_INCREMENT,
    nombre    VARCHAR(120)   NOT NULL,
    categoria VARCHAR(60)    NOT NULL,
    unidad    VARCHAR(30)    NOT NULL,
    precio    DECIMAL(12, 2) NOT NULL,
    stock     INT            NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_producto_nombre (nombre),
    CONSTRAINT ck_producto_precio CHECK (precio >= 0),
    CONSTRAINT ck_producto_stock CHECK (stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pedido (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    cliente_nombre VARCHAR(120)   NOT NULL,
    telefono       VARCHAR(20)    NOT NULL,
    direccion      VARCHAR(255)   NOT NULL,
    estado         VARCHAR(20)    NOT NULL,
    creado_en      DATETIME(6)    NOT NULL,
    total          DECIMAL(12, 2) NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pedido_telefono (telefono)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS detalle_pedido (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    pedido_id       BIGINT         NOT NULL,
    producto_id     BIGINT         NOT NULL,
    cantidad        INT            NOT NULL,
    precio_unitario DECIMAL(12, 2) NOT NULL,
    subtotal        DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_detalle_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO producto (nombre, categoria, unidad, precio, stock) VALUES
    ('Arroz blanco',            'Despensa',   'bolsa 1 kg',    4800,  120),
    ('Lentejas',                'Despensa',   'bolsa 500 g',   5200,   80),
    ('Pasta spaghetti',         'Despensa',   'paquete 500 g', 4300,   90),
    ('Aceite de girasol',       'Despensa',   'botella 1 L',  14500,   60),
    ('Azúcar blanca',           'Despensa',   'bolsa 1 kg',    5100,  100),
    ('Sal refinada',            'Despensa',   'bolsa 500 g',   1900,  110),
    ('Café molido',             'Despensa',   'bolsa 250 g',  13900,   70),
    ('Leche entera',            'Lácteos',    'bolsa 1 L',     4600,  150),
    ('Yogur natural',           'Lácteos',    'vaso 200 g',    2900,   75),
    ('Queso campesino',         'Lácteos',    'bloque 500 g', 12800,   40),
    ('Mantequilla',             'Lácteos',    'barra 250 g',   9700,   45),
    ('Huevos AA',               'Huevos',     'cubeta x 30',  18900,   50),
    ('Pechuga de pollo',        'Carnes',     'bandeja 1 kg', 19800,   35),
    ('Carne molida de res',     'Carnes',     'bandeja 500 g',15900,   30),
    ('Lomo de cerdo',           'Carnes',     'bandeja 500 g',14700,   25),
    ('Banano',                  'Frutas',     'kg',            3200,   90),
    ('Manzana roja',            'Frutas',     'kg',            8900,   60),
    ('Naranja',                 'Frutas',     'kg',            3600,   70),
    ('Papa pastusa',            'Verduras',   'kg',            2800,  130),
    ('Tomate chonto',           'Verduras',   'kg',            4100,   85),
    ('Cebolla cabezona',        'Verduras',   'kg',            3700,   80),
    ('Zanahoria',               'Verduras',   'kg',            2900,   75),
    ('Pan tajado',              'Panadería',  'paquete 500 g', 7200,   55),
    ('Agua mineral',            'Bebidas',    'botella 1.5 L', 3400,  100),
    ('Jugo de naranja',         'Bebidas',    'caja 1 L',      7800,   50),
    ('Jabón de loza',           'Aseo',       'barra 500 g',   5900,   65),
    ('Detergente en polvo',     'Aseo',       'bolsa 1 kg',   12400,   40),
    ('Papel higiénico',         'Aseo',       'paquete x 4',   9800,   60);
