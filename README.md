# SPARQL Debugger

**SPARQL Debugger** is a tool designed to trace the execution of federated SPARQL queries. It provides a visual representation of service executions in tree structures, displaying information about a service execution such as requests, responses, duration, and the number of results.

The tool is divided into two main components:
- **Server (this repository):** Handles the backend logic, including REST API and SSE messages.
- **Frontend React Component:** Renders the service execution tree. You can find it [here](https://github.com/iocbbioinf/sparql_debugger_component).

## REST API

The server offers the following REST API endpoints:
- `POST /syncquery`: Create and execute query and return query execution tree in JSON.
- `GET POST /query`: Create async. query
- `POST /query/{queryId}/delete`: Delete a query debugging instance.
- `GET /query/{queryId}/sse`: Stream real-time updates for the service execution tree via Server-Sent Events (SSE).
- `GET /query/{queryId}/call/{callId}/request`: Retrieve the request details of a specific service execution call.
- `GET /query/{queryId}/call/{callId}/response`: Retrieve the response details of a specific service execution call.

## Configuration

All config. properties are stored in the file: `/src/main/java/resources/application.properties`
Properties such as logging level, query execution time expiration etc. are configurable here.

## Build, Package, Run

This is a Spring Boot Web application built using Gradle. You can use the following Gradle tasks (with Gradle wrapper `.gradlew`)

- `build`: Compiles the project.
- `bootJar`: Packages the application into a JAR file.
- `bootRun`: Runs the application locally.

### Run app

- by Gradle: `./gradlew bootRun`
- or by Java: `java -jar path_to_jar/debugger-0.0.1-SNAPSHOT.jar`
- Java version >= 21 required

To run a local instance of the SPARQL Debugger, it must be accessible via a specific public URL, which should be set using the system variable `debugService`.
When deploying with Kubernetes, this URL is preconfigured in the deployment descriptor.

## Deployment on the Cloud by Kubernetes

### Docker Image
To deploy the application, first build a Docker image using the Dockerfile located at `/docker/Dockerfile`.

### Kubernetes Deployment
Use the provided Kubernetes descriptors to deploy the application:

1. **Deployment Descriptor:** `/kubernetes/idsmDebugger.deployment.yaml`
    - This descriptor deploys the application as a Kubernetes Deployment and Service.
    - **Required Edits:**
        - Update the `container image` attribute.
        - Set the `debugService` environment variable as needed.

2. **Ingress Descriptor:** `/kubernetes/idsmDebugger.ingress.yaml`
    - This descriptor creates a Kubernetes Ingress for the application.
    - **Required Edits:**
        - Update the `host` and `hosts` attributes.
        - Modify the `nginx.ingress.kubernetes.io/cors-allow-origin` annotation.

## Running instance
Whole SPARQL Federated Query Debugger application is available [here](https://idsm-react-debugger-1.dyn.cloud.e-infra.cz/). 
