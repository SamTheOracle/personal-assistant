version=$1
mvn clean package
docker build -f src/main/docker/Dockerfile.jvm . -t oracolo/personal-assistant:"$version"
docker push oracolo/personal-assistant:"$version"