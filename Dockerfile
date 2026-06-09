# --- build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
<<<<<<< HEAD
# Install runtime dependencies and ONNX Runtime native binaries
RUN apt-get update && apt-get install -y --no-install-recommends \
		curl ca-certificates libgomp1 && rm -rf /var/lib/apt/lists/*

# Download ONNX Runtime (adjust version if you prefer another)
ENV ONNXRT_VERSION=1.15.1
RUN curl -L -o /tmp/onnx.tgz \
		https://github.com/microsoft/onnxruntime/releases/download/v${ONNXRT_VERSION}/onnxruntime-linux-x64-${ONNXRT_VERSION}.tgz \
	&& tar -xzf /tmp/onnx.tgz -C /opt \
	&& rm /tmp/onnx.tgz

# Ensure native libs are visible to the JVM / DJL
ENV LD_LIBRARY_PATH=/opt/onnxruntime-linux-x64/lib:${LD_LIBRARY_PATH}

# Copy the fat JAR built in the previous stage
COPY --from=build /app/target/*.jar app.jar

# Optional: if you prefer the model files outside the JAR, mount them or copy them here
# COPY src/main/resources/transformer /app/transformer

EXPOSE 8080

# Recommended runtime variables:
# - TNS_ADMIN: path to Oracle wallet inside container (if using wallet files)
# - DB_URL, DB_USER, DB_PASSWORD: database credentials (use secrets in production)

ENTRYPOINT ["java", "-Djavax.xml.parsers.SAXParserFactory=com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl", "-Djavax.xml.parsers.DocumentBuilderFactory=com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", "-jar", "app.jar"]
=======
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
>>>>>>> 41e62851987eb2c671793f72432cedc9b270fc77
