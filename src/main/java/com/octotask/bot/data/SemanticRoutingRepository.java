package com.octotask.bot.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;


@Repository
@ConditionalOnBean(name = "vectorDataSource")
public class SemanticRoutingRepository {

    private static final Logger log = LoggerFactory.getLogger(SemanticRoutingRepository.class);

    private final DataSource vectorDataSource;
    private final JdbcTemplate jdbc;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public SemanticRoutingRepository(DataSource vectorDataSource) {
        this.vectorDataSource = vectorDataSource;
        this.jdbc = new JdbcTemplate(vectorDataSource);
    }

    /**
     * Search returning each route's cosine distance, so callers can apply a
     * confidence threshold. Uses TO_VECTOR(json) which works without native
     * VECTOR array binding.
     */
    public List<RouteMatch> searchWithDistance(float[] embedding, int k) {
        final String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(boxToDoubleArray(embedding));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize embedding to JSON", e);
        }
        String sql = "SELECT id, descripcion_texto, funcion_backend, fecha_creacion, " +
                "       VECTOR_DISTANCE(descripcion_vector, TO_VECTOR(?), COSINE) AS dist " +
                "FROM rutas_semanticas " +
                "ORDER BY dist ASC " +
                "FETCH FIRST ? ROWS ONLY";
        return jdbc.query(sql, (rs, n) -> {
            Timestamp ts = rs.getTimestamp("fecha_creacion");
            SemanticRoute route = new SemanticRoute(
                    rs.getInt("id"),
                    rs.getString("descripcion_texto"),
                    rs.getString("funcion_backend"),
                    ts != null ? ts.toLocalDateTime() : null);
            return new RouteMatch(route, rs.getDouble("dist"));
        }, json, k);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM rutas_semanticas", Long.class);
        return n == null ? 0 : n;
    }

    /** Remove every route. Used by the reset-and-reseed path. Returns rows deleted. */
    public int deleteAll() {
        return jdbc.update("DELETE FROM rutas_semanticas");
    }

    public int insertRoute(String text, float[] embedding, String backend) throws SQLException {
        try (Connection conn = vectorDataSource.getConnection()) {
            Array arr = tryCreateFloatArray(conn, embedding);
            if (arr != null) {
                try (CallableStatement cs = conn.prepareCall("{call sp_insert_route_vec(?, ?, ?, ?)}")) {
                    cs.setString(1, text);
                    cs.setArray(2, arr);
                    cs.setString(3, backend);
                    cs.registerOutParameter(4, Types.NUMERIC);
                    cs.execute();
                    return cs.getInt(4);
                }
            } else {
                try {
                    String json = OBJECT_MAPPER.writeValueAsString(boxToDoubleArray(embedding));
                    try (CallableStatement cs = conn.prepareCall("{call sp_insert_route(?, ?, ?, ?)}")) {
                        cs.setString(1, text);
                        cs.setString(2, json);
                        cs.setString(3, backend);
                        cs.registerOutParameter(4, Types.NUMERIC);
                        cs.execute();
                        return cs.getInt(4);
                    }
                } catch (JsonProcessingException e) {
                    throw new SQLException("Failed to serialize embedding to JSON", e);
                }
            }
        }
    }


    public int insertRoutePreferVector(String text, float[] embedding, String backend) throws SQLException {
        return insertRoute(text, embedding, backend);
    }

    public List<SemanticRoute> searchSimilar(float[] embedding, int k) throws SQLException {
        List<SemanticRoute> out = new ArrayList<>();
        try (Connection conn = vectorDataSource.getConnection()) {
            Array arr = tryCreateFloatArray(conn, embedding);
            if (arr != null) {
                try (CallableStatement cs = conn.prepareCall("{call sp_search_similar_vec(?, ?, ?)}")) {
                    cs.setArray(1, arr);
                    cs.setInt(2, k);
                    cs.registerOutParameter(3, Types.REF_CURSOR);
                    cs.execute();

                    try (ResultSet rs = (ResultSet) cs.getObject(3)) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            String text = rs.getString("descripcion_texto");
                            String func = rs.getString("funcion_backend");
                            Timestamp ts = rs.getTimestamp("fecha_creacion");
                            LocalDateTime created = ts != null ? ts.toLocalDateTime() : null;
                            out.add(new SemanticRoute(id, text, func, created));
                        }
                    }
                    return out;
                }
            } else {
        
                try {
                    String json = OBJECT_MAPPER.writeValueAsString(boxToDoubleArray(embedding));
                    try (CallableStatement cs = conn.prepareCall("{call sp_search_similar(?, ?, ?)}")) {
                        cs.setString(1, json);
                        cs.setInt(2, k);
                        cs.registerOutParameter(3, Types.REF_CURSOR);
                        cs.execute();

                        try (ResultSet rs = (ResultSet) cs.getObject(3)) {
                            while (rs.next()) {
                                int id = rs.getInt("id");
                                String text = rs.getString("descripcion_texto");
                                String func = rs.getString("funcion_backend");
                                out.add(new SemanticRoute(id, text, func, null));
                            }
                        }
                        return out;
                    }
                } catch (JsonProcessingException e) {
                    throw new SQLException("Failed to serialize embedding to JSON", e);
                }
            }
        }
    }


    public List<SemanticRoute> searchSimilarPreferVector(float[] embedding, int k) throws SQLException {
        return searchSimilar(embedding, k);
    }

    private static Array tryCreateFloatArray(Connection conn, float[] embedding) {
        try {
            Float[] boxed = new Float[embedding.length];
            for (int i = 0; i < embedding.length; i++)
                boxed[i] = embedding[i];
            return conn.createArrayOf("FLOAT", boxed);
        } catch (Throwable t) {
            log.debug("createArrayOf FLOAT not supported: {}", t.getMessage());
            return null;
        }
    }

    private static double[] boxToDoubleArray(float[] embedding) {
        double[] out = new double[embedding.length];
        for (int i = 0; i < embedding.length; i++)
            out[i] = embedding[i];
        return out;
    }

    private static byte[] floatsToBytesLE(float[] floats) {
        ByteBuffer bb = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats)
            bb.putFloat(f);
        return bb.array();
    }
}
