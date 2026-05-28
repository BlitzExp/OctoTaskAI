
CREATE TABLE rutas_semanticas (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    descripcion_texto VARCHAR2(4000) NOT NULL,
    descripcion_vector VECTOR(384, FLOAT32),
    funcion_backend VARCHAR2(255) NOT NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE VECTOR INDEX idx_rutas_vectoriales_vec 
ON rutas_semanticas (descripcion_vector) 
ORGANIZATION INMEMORY NEIGHBOR GRAPH 
DISTANCE COSINE 
WITH TARGET ACCURACY 95;


CREATE OR REPLACE PROCEDURE sp_insert_route(
    p_text IN VARCHAR2,
    p_embedding_json IN CLOB,
    p_backend IN VARCHAR2,
    p_out_id OUT NUMBER
) AS
BEGIN
    INSERT INTO rutas_semanticas (
        descripcion_texto,
        descripcion_vector,
        funcion_backend
    )
    VALUES (
        p_text,
        TO_VECTOR(p_embedding_json),
        p_backend
    )
    RETURNING id INTO p_out_id;
END sp_insert_route;
/


CREATE OR REPLACE PROCEDURE sp_search_similar(
    p_embedding_json IN CLOB,
    p_k IN PLS_INTEGER,
    p_result OUT SYS_REFCURSOR
) AS
BEGIN
    OPEN p_result FOR
        SELECT
            id,
            descripcion_texto,
            funcion_backend
        FROM (
            SELECT
                id,
                descripcion_texto,
                funcion_backend,
                VECTOR_DISTANCE(
                    descripcion_vector,
                    TO_VECTOR(p_embedding_json),
                    COSINE
                ) AS dist
            FROM rutas_semanticas
        )
        ORDER BY dist ASC
        FETCH FIRST p_k ROWS ONLY;
END sp_search_similar;
/


CREATE OR REPLACE PROCEDURE sp_insert_route_vec(
    p_text IN VARCHAR2,
    p_embedding_vector IN rutas_semanticas.descripcion_vector%TYPE,
    p_backend IN VARCHAR2,
    p_out_id OUT NUMBER
) AS
BEGIN
    INSERT INTO rutas_semanticas (descripcion_texto, descripcion_vector, funcion_backend)
    VALUES (p_text, p_embedding_vector, p_backend)
    RETURNING id INTO p_out_id;
END sp_insert_route_vec;
/

CREATE OR REPLACE PROCEDURE sp_search_similar_vec(
    p_embedding_vector IN rutas_semanticas.descripcion_vector%TYPE,
    p_k IN PLS_INTEGER,
    p_result OUT SYS_REFCURSOR
) AS
BEGIN
    OPEN p_result FOR
      SELECT id, descripcion_texto, funcion_backend
      FROM (
        SELECT id, descripcion_texto, funcion_backend,
               COSINE_DISTANCE(descripcion_vector, p_embedding_vector) AS dist
        FROM rutas_semanticas
      )
      ORDER BY dist ASC
      FETCH FIRST p_k ROWS ONLY;
END sp_search_similar_vec;
/
