mvn clean package
docker build -f src/main/docker/Dockerfile.jvm . -t oracolo/personal-assistant
docker push oracolo/personal-assistant