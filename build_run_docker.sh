mvn clean package -Pnative -Dquarkus.native.container-build=true && \
docker build -f src/main/docker/Dockerfile.native -t simonmassey/quarkus-graphql . && \
docker run -it --volume $(pwd):/home/project \
  -e GOOGLE_APPLICATION_CREDENTIALS=/home/project/bigtable-sa.json \
  -p 8080:8080 \
  simonmassey/quarkus-graphql
