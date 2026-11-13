package com.udea.supermercado.service;

import com.udea.supermercado.model.EstadoPedido;
import com.udea.supermercado.model.Producto;
import com.udea.supermercado.repository.PedidoRepository;
import com.udea.supermercado.repository.ProductoRepository;
import com.udea.supermercado.service.dto.ItemPedido;
import com.udea.supermercado.service.dto.PedidoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SupermercadoToolsServiceTests {

    private static final String TELEFONO = "3001234567";

    @Autowired
    private SupermercadoToolsService tools;

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private PedidoRepository pedidos;

    @Autowired
    private TransactionTemplate tx;

    private Producto arroz;
    private Producto leche;

    @BeforeEach
    void preparar() {
        tx.executeWithoutResult(s -> {
            pedidos.deleteAll();
            productos.deleteAll();
        });
        arroz = productos.save(new Producto("Arroz blanco", "Despensa", "bolsa 1 kg", new BigDecimal("4800.00"), 10));
        leche = productos.save(new Producto("Leche entera", "Lácteos", "bolsa 1 L", new BigDecimal("4600.00"), 5));
    }

    private int stock(Producto p) {
        return productos.findById(p.getId()).orElseThrow().getStock();
    }

    private PedidoDto pedir(ItemPedido... items) {
        return tools.crearPedido("Ana Pérez", TELEFONO, "Calle 10 # 20-30", List.of(items));
    }

    @Test
    void buscaPorNombreYCategoria() {
        assertThat(tools.listarCategorias()).containsExactly("Despensa", "Lácteos");
        assertThat(tools.buscarProductos("ARRO", null)).extracting("nombre").containsExactly("Arroz blanco");
        assertThat(tools.buscarProductos(null, "lácteos")).extracting("nombre").containsExactly("Leche entera");
        assertThat(tools.buscarProductos(null, null)).hasSize(2);
    }

    @Test
    void crearPedidoCalculaTotalYDescuentaStock() {
        PedidoDto pedido = pedir(new ItemPedido(arroz.getId(), 2), new ItemPedido(leche.getId(), 1));

        assertThat(pedido.total()).isEqualByComparingTo("14200");
        assertThat(pedido.estado()).isEqualTo(EstadoPedido.RECIBIDO);
        assertThat(pedido.lineas()).hasSize(2);
        assertThat(stock(arroz)).isEqualTo(8);
        assertThat(stock(leche)).isEqualTo(4);
    }

    @Test
    void lineasRepetidasDelMismoProductoSeSuman() {
        PedidoDto pedido = pedir(new ItemPedido(arroz.getId(), 2), new ItemPedido(arroz.getId(), 3));

        assertThat(pedido.lineas()).singleElement().satisfies(l -> assertThat(l.cantidad()).isEqualTo(5));
        assertThat(stock(arroz)).isEqualTo(5);
    }

    @Test
    void sinStockSuficienteNoQuedaNadaDescontado() {
        assertThatThrownBy(() -> pedir(new ItemPedido(arroz.getId(), 2), new ItemPedido(leche.getId(), 6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stock insuficiente de Leche entera");

        assertThat(stock(arroz)).isEqualTo(10);
        assertThat(stock(leche)).isEqualTo(5);
        assertThat(pedidos.count()).isZero();
    }

    @Test
    void comprasConcurrentesNoVendenMasDelStockDisponible() throws Exception {
        Callable<Boolean> compra = () -> {
            try {
                pedir(new ItemPedido(leche.getId(), 1));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        };

        long exitosas;
        try (ExecutorService hilos = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> resultados = hilos.invokeAll(IntStream.range(0, 12).mapToObj(i -> compra).toList());
            exitosas = 0;
            for (Future<Boolean> r : resultados) {
                if (r.get()) exitosas++;
            }
        }

        assertThat(exitosas).isEqualTo(5);
        assertThat(stock(leche)).isZero();
    }

    @Test
    void rechazaDatosInvalidos() {
        List<ItemPedido> items = List.of(new ItemPedido(arroz.getId(), 1));

        assertThatThrownBy(() -> tools.crearPedido(" ", TELEFONO, "Calle 1", items)).hasMessageContaining("nombre");
        assertThatThrownBy(() -> tools.crearPedido("Ana", "abc", "Calle 1", items)).hasMessageContaining("teléfono");
        assertThatThrownBy(() -> tools.crearPedido("Ana", TELEFONO, "", items)).hasMessageContaining("dirección");
        assertThatThrownBy(() -> tools.crearPedido("Ana", TELEFONO, "Calle 1", List.of())).hasMessageContaining("al menos");
        assertThatThrownBy(() -> pedir(new ItemPedido(arroz.getId(), 0))).hasMessageContaining("entre 1 y 50");
        assertThatThrownBy(() -> pedir(new ItemPedido(arroz.getId(), -1), new ItemPedido(arroz.getId(), 2)))
                .hasMessageContaining("entre 1 y 50");
        assertThatThrownBy(() -> pedir(new ItemPedido(9999L, 1))).hasMessageContaining("No existe el producto");
    }

    @Test
    void soloSeConsultaConElTelefonoDelPedido() {
        PedidoDto pedido = pedir(new ItemPedido(arroz.getId(), 1));

        assertThat(tools.consultarPedido(pedido.id(), "300 123 4567").lineas()).hasSize(1);
        assertThatThrownBy(() -> tools.consultarPedido(pedido.id(), "3110000000"))
                .hasMessageContaining("No se encontró");
        assertThat(tools.listarPedidosCliente(TELEFONO)).hasSize(1);
        assertThat(tools.listarPedidosCliente("3110000000")).isEmpty();
    }

    @Test
    void cancelarDevuelveStockYNoSePuedeRepetir() {
        PedidoDto pedido = pedir(new ItemPedido(arroz.getId(), 4));
        assertThat(stock(arroz)).isEqualTo(6);

        assertThat(tools.cancelarPedido(pedido.id(), TELEFONO).estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(stock(arroz)).isEqualTo(10);

        assertThatThrownBy(() -> tools.cancelarPedido(pedido.id(), TELEFONO))
                .hasMessageContaining("ya no se puede cancelar");
        assertThat(stock(arroz)).isEqualTo(10);
    }
}
