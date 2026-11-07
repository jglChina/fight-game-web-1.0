# 构建阶段：用官方 Maven 镜像编译，避免依赖本机环境
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先单独拷贝 pom.xml，让依赖层可以被 Docker 缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# 运行阶段：只保留 JRE 和 jar，镜像体积更小
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/fight-game-web-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
