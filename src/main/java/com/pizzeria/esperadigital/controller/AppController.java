package com.pizzeria.esperadigital.controller;

import com.pizzeria.esperadigital.entity.Pedido;
import com.pizzeria.esperadigital.repository.ClienteRepository;
import com.pizzeria.esperadigital.repository.IngredienteRepository;
import com.pizzeria.esperadigital.repository.MesaRepository;
import com.pizzeria.esperadigital.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AppController {

    @Autowired private MesaRepository       mesaRepository;
    @Autowired private IngredienteRepository ingredienteRepository;
    @Autowired private PedidoRepository     pedidoRepository;
    @Autowired private ClienteRepository    clienteRepository;

    // ────────────────────────────────────────────────────────────
    // 1. Pantalla Bienvenida QR  →  /
    // ────────────────────────────────────────────────────────────
    @GetMapping("/")
    public String index() {
        return "index";
    }

    // ────────────────────────────────────────────────────────────
    // 2. Cola Virtual  →  /cola
    // ────────────────────────────────────────────────────────────
    @GetMapping("/cola")
    public String cola(Model model) {
        model.addAttribute("posicion", 3);
        return "cola";
    }

    // ────────────────────────────────────────────────────────────
    // 3. Selección de Mesa  →  /mesas
    // ────────────────────────────────────────────────────────────
    @GetMapping("/mesas")
    public String mesas(Model model) {
        model.addAttribute("mesas", mesaRepository.findByDisponibleTrue());
        return "mesas";
    }

    // ────────────────────────────────────────────────────────────
    // 4. Personalización de Pizza  →  /personalizar
    // ────────────────────────────────────────────────────────────
    @GetMapping("/personalizar")
    public String personalizar(Model model) {
        model.addAttribute("ingredientes", ingredienteRepository.findAll());
        return "personalizar";
    }

    // ────────────────────────────────────────────────────────────
    // 5. Confirmación de Pedido  →  POST /confirmar
    // ────────────────────────────────────────────────────────────
    @PostMapping("/confirmar")
    public String confirmar(Model model) {
        return "redirect:/ticket";
    }

    @GetMapping("/ticket")
    public String ticket(Model model) {
        return "ticket";
    }

    // ────────────────────────────────────────────────────────────
    // 6. Dashboard Cocina  →  /cocina
    // ────────────────────────────────────────────────────────────
    @GetMapping("/cocina")
    public String cocina(Model model) {
        model.addAttribute("pedidos", pedidoRepository.findAll());
        return "cocina";
    }

    // ────────────────────────────────────────────────────────────
    // 7. Dashboard Administrador  →  /admin
    //    Aquí se calculan TODOS los datos reales para el dashboard
    // ────────────────────────────────────────────────────────────
    @GetMapping("/admin")
    public String admin(Model model) {

        // ── Rango de fechas ──────────────────────────────────────
        LocalDateTime inicioDia  = LocalDate.now().atStartOfDay();           // hoy 00:00
        LocalDateTime finDia     = inicioDia.plusDays(1).minusSeconds(1);    // hoy 23:59:59
        LocalDateTime inicioAyer = inicioDia.minusDays(1);                   // ayer 00:00
        LocalDateTime finAyer    = inicioDia.minusSeconds(1);                // ayer 23:59:59

        // ── KPI 1: Total de clientes registrados ─────────────────
        long totalClientes = clienteRepository.count();
        model.addAttribute("totalClientes", totalClientes);

        // ── KPI 2: Pedidos procesados hoy ────────────────────────
        long pedidosHoy  = pedidoRepository.countByFechaPedidoBetween(inicioDia, finDia);
        long pedidosAyer = pedidoRepository.countByFechaPedidoBetween(inicioAyer, finAyer);
        model.addAttribute("totalPedidos", pedidosHoy);

        // Porcentaje de cambio vs ayer (+12% / -5%)
        String cambioPedidos = calcularCambio(pedidosHoy, pedidosAyer);
        model.addAttribute("cambioPedidos", cambioPedidos);
        model.addAttribute("cambioPedidosPositivo", pedidosHoy >= pedidosAyer);

        // ── KPI 3: Ventas del día ─────────────────────────────────
        Double ventasHoy  = pedidoRepository.sumTotalByFechaPedidoBetween(inicioDia, finDia);
        Double ventasAyer = pedidoRepository.sumTotalByFechaPedidoBetween(inicioAyer, finAyer);
        model.addAttribute("ventasDia", String.format("S/ %.2f", ventasHoy));

        String cambioVentas = calcularCambio(ventasHoy.longValue(), ventasAyer.longValue());
        model.addAttribute("cambioVentas", cambioVentas);
        model.addAttribute("cambioVentasPositivo", ventasHoy >= ventasAyer);

        // ── Donut: pedidos agrupados por ubicación de mesa ────────
        // Devuelve algo como: {INTERIOR=48, TERRAZA=25, PARA LLEVAR=17}
        List<Object[]> rawUbicacion = pedidoRepository.countGroupByUbicacion(inicioDia, finDia);
        Map<String, Long> pedidosPorUbicacion = new LinkedHashMap<>();
        for (Object[] row : rawUbicacion) {
            pedidosPorUbicacion.put((String) row[0], (Long) row[1]);
        }
        model.addAttribute("pedidosPorUbicacion", pedidosPorUbicacion);

        // Convertir a JSON para ApexCharts (labels y series)
        StringBuilder labelsJson  = new StringBuilder("[");
        StringBuilder seriesJson  = new StringBuilder("[");
        pedidosPorUbicacion.forEach((ubicacion, cantidad) -> {
            labelsJson.append("'").append(ubicacion).append("',");
            seriesJson.append(cantidad).append(",");
        });
        // Quitar última coma y cerrar
        if (labelsJson.length() > 1)  labelsJson.setLength(labelsJson.length() - 1);
        if (seriesJson.length() > 1)  seriesJson.setLength(seriesJson.length() - 1);
        labelsJson.append("]");
        seriesJson.append("]");
        model.addAttribute("donutLabels",  labelsJson.toString());
        model.addAttribute("donutSeries",  seriesJson.toString());

        // ── Tabla: Últimos 5 pedidos ──────────────────────────────
        List<Pedido> ultimosPedidos = pedidoRepository.findTop5ByOrderByFechaPedidoDesc();
        model.addAttribute("ultimosPedidos", ultimosPedidos);

        return "admin";
    }

    // ── Helper: calcula el % de cambio entre dos valores ──────────
    private String calcularCambio(long actual, long anterior) {
        if (anterior == 0) return "+100%";
        long diff = ((actual - anterior) * 100) / anterior;
        return (diff >= 0 ? "+" : "") + diff + "% vs ayer";
    }
}
