package com.pizzeria.esperadigital.repository;

import com.pizzeria.esperadigital.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByFechaPedidoBetween(LocalDateTime inicio, LocalDateTime fin);

    long countByFechaPedidoBetween(LocalDateTime inicio, LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p WHERE p.fechaPedido BETWEEN :inicio AND :fin")
    Double sumTotalByFechaPedidoBetween(@Param("inicio") LocalDateTime inicio,
                                        @Param("fin")    LocalDateTime fin);

    @Query("""
        SELECT COALESCE(p.mesa.ubicacion, 'PARA LLEVAR'), COUNT(p)
        FROM Pedido p
        WHERE p.fechaPedido BETWEEN :inicio AND :fin
        GROUP BY COALESCE(p.mesa.ubicacion, 'PARA LLEVAR')
        """)
    List<Object[]> countGroupByUbicacion(@Param("inicio") LocalDateTime inicio,
                                          @Param("fin")    LocalDateTime fin);

    List<Pedido> findTop5ByOrderByFechaPedidoDesc();
}
