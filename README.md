# LFS Data Core based on Apache Sling

[![Build Status](https://travis-ci.com/ccmbioinfo/lfs.svg?branch=dev)](https://travis-ci.com/ccmbioinfo/lfs)

## Prerequisites:
* Java 1.8+
* Maven 3.3+
* Python 2.5+ or Python 3.0+

## Build:
`mvn clean install`

Additional options include:

`mvn clean -Pclean-node` to remove compiled frontend code

`mvn clean -Pclean-instance` to remove the sling/ folder generated by running Sling

`mvn install -Pquick` to skip many of the test steps

`mvn install -Pskip-webpack` to skip reinstalling webpack/yarn and dependencies and skip regenerating the frontend with webpack

`mvn install -PautoInstallBundle` to inject compiled code into a running instance of Sling at `http://localhost:8080/` with admin:admin.

To specify a different password, use `-Dsling.password=newPassword`

To specify a different URL, use `-Dsling.url=https://lfs.server:8443/system/console` (the URL must end with `/system/console` to work properly)

`mvn install -PintegrationTests` to run integration tests

## Run:
`java -jar distribution/target/lfs-*.jar` => the app will run at `http://localhost:8080` (default port)

`java -jar distribution/target/lfs-*.jar -p PORT` to run at a different port

`java -jar distribution/target/lfs-*.jar -Dsling.run.modes=dev` to include the content browser (Composum), accessible at `http://localhost:8080/bin/browser.html`

## Running with Docker
If Docker is installed, then the build will also create a new image named `lfs/lfs:latest`. You can run this image (for testing) with:

`docker run -d -p 8080:8080 lfs/lfs`

However, since runtime data isn't persisted after the container stops, no changes will be permanently persisted this way.
It is recommended to first create a permanent volume that can be reused between different image instantiations, and different image versions.

`docker volume create --label server=production lfs-production-volume`

Then the container can be started with:

`docker container run --rm --detach --volume lfs-test-volume:/opt/lfs/sling/ -p 8080:8080 --name lfs-production lfs/lfs`

Explanation:

- `docker container run` creates and starts a new container
- `--rm` will automatically remove the container after it is stopped
- `--detach` starts the container in the background
- `--volume lfs-test-volume:/opt/lfs/sling/` mounts the volume named `lfs-test-volume` at `/opt/lfs/sling/`, where the application data is stored
- `-p 8080:8080` makes the local port 8080 forward to the 8080 port inside the container
    - you can also specify a specific local network, and a different local port, for example `-p 127.0.0.1:9999:8080`
    - the second port must be `8080`
- `--name lfs-production` gives a name to the container, for easy identification
- `lfs/lfs` is the name of the image

To enable developer mode, also add `--env DEV=true -p 5005:5005` to the `docker run` command.

To enable debug mode, also add `--env DEBUG=true` to the `docker run` command. Note that the application will not start until a debugger is actually attached to the process on port 5005.

`docker run -d -p 8080:8080 -p 5005:5005 --env DEV=true --env DEBUG=true --name lfs-debug lfs/lfs`
