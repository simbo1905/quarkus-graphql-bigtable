# Quarkus GraphQL BigTable

This is a port port of [spring-bigquery-graphql](https://github.com/simbo1905/spring-bigquery-graphql) 
onto Quarkus with GraalVM Native Image support. See below for how to compile and run the native image 
using Docker. 

This project uses Quarkus, the Supersonic Subatomic Java Framework.

Note that the original project has a GitHub action to build it and instructions on running the app with KNative 
which has not been ported over to this repo. Personally I would use this GraalVM native-image ready Quarkus code
with KNative. Quarkus is based on Vert.x and this version on of the code is more scalable as it doesn't block a thread 
waiting for BigQuery. 

## Bigtable Setup

On the Google Cloud console:

1. Create the Bigtable cluster.
2. Create a service account `bigtable-graphql` and grant it Bigtable admin permission
3. Create a json keyfile for the service account and save it as `bigtable-sq.json`   
3. Set the cluster details in `application.properties`
4. Then run the main method in `com.github.simbo1905.bigtablegraphql.BigtableInitializer`

That should create the table and populate rows with the values from the original demo.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```
export GOOGLE_APPLICATION_CREDENTIALS=$(pwd)/bigtable-sa.json ; quarkus:dev
```

# GraphQL API

Open up http://localhost:8080/graphql-ui/ and query with:

```graphql
{
  bookById(id:"book-1"){
    id
    name
    pageCount
    author {
      firstName
      lastName
    }
  }
}
```

Then we get back:

```json
{
  "data": {
    "book1": {
      "id": "book-1",
      "name": "Harry Potter and the Philosopher's Stone",
      "pageCount": 223,
      "author": {
        "firstName": "Joanne",
        "lastName": "Rowling"
      }
    }
  }
}
```

## Docker Build/Run Native Executable

This repo uses `google-cloud-graalvm-support` to be able to use GraalVM to compile a native 
image that can use the Google client libraries (which use alot of reflection). 
See `application.properties` for the flags that enable security. Also see how the code uses 
`@RegisterForReflection` to ensure that some use of reflection works. 

To build and run a docker image containing the native image use: 

```shell
mvn clean package -Pnative -Dquarkus.native.container-build=true && \
docker build -f src/main/docker/Dockerfile.native -t simonmassey/quarkus-graphql . && \
docker run -it --volume $(pwd):/home/project \
  -e GOOGLE_APPLICATION_CREDENTIALS=/home/project/bigquery-sa.json \
  -p 8080:8080 \
```

At the time of writing the image is about 
