# Akka Cluster in a Docker Swarm (remote)

Akka cluster running in docker swarm mode running with raspberry pi on same network.

## Terms

Cluster : Group of nodes.

Node: An actor system running a JVM. It is possible to have multiple actor systems in one JVM.

Frontend: Node/System creator of tasks.

Backend: Node/System worker of tasks coming from Frontend. 

Swarm: A swarm is a group of machines that are running Docker and joined into a cluster.

Stack: A stack is a group of interrelated services that share dependencies, and can be orchestrated 
and scaled together. A single stack is capable of defining and coordinating the functionality of an entire application 
(though very complex applications may want to use multiple stacks).

For more definitions and how to setup environment follow the official documentation at:

[Get Started, Part 3: Services](https://docs.docker.com/get-started/part3/)

### Prerequisites

* Have a docker setup running in swarm mode. This means that all nodes must have docker installed.

     Please refer to: [Getting started with swarm mode](https://docs.docker.com/engine/swarm/swarm-tutorial/)

* One linux machine (will function as manager of the swarm)
* One raspberry pi model 3B+ (arm32v7l)

**IMPORTANT: All commands must be executed on the manager node of the swarm**


### Installing/Running

####Setup docker swarm

In general follow these steps: [Create a swarm](https://docs.docker.com/engine/swarm/swarm-tutorial/create-swarm/)
However it could be as simple as:
* Go to the manager to be node, which is the linux machine, and execute: 　
```
docker swarm init
```
This will create the swarm with the node as manager. It will also display the command to use to join 
a worker node in the form of:

```
docker swarm join \
    --token SWMTKN-1-49nj1cmql0jkz5s954yi3oex3nedyz0fb0xx14ie39trti4wxv-8vxv8rssmk743ojnwacrr2e7c \
    192.168.99.100:2377

``` 
So go to the worker nodes, in this case the raspberry pi, and use that command to join the cluster.
 
Now back to the manager node, clone the project and go to the location of the project.

Deploy the stack to the swarm:
```
docker stack deploy --compose-file docker-compose.yml akkaclusterswarm  
```

(The "akkaclusterswarm" is just the name of the stack and can be changed)

This will start three services:

- seed1 (on the raspberry node, which is a worker node, see constraints on the docker-compose file)
- seed2 (on manager node)
- frontend (on manager node)

The deployed service "seed1", which is deployed on a raspberry uses a different base image:

```
seed1:
    image: marcelodock/akkaclusterarm32v7
```

This is an special image that uses: "arm32v7/gradle" which is made for working on a raspberry pi model 3 B+
which has a arm32v7l architecture.

The other services, namely backend1 and frontend, use the base image:
```
image: marcelodock/akkacluster
```
Which uses the standard "gradle" with jdk8. Please refer to: 

[Docker Gradle](https://docs.docker.com/samples/library/gradle/)

Both of these images are located on the public dockerhub, so it can be downloaded directly from any node.

The hostnames and ports of the nodes are being set as environment variables on the
docker-compose.yml file. Then on the application.conf these variables are obtained using 
the HOCON syntax and being replaced in the corresponding places.

On the node manager to see the deployed stack:
```
docker stack ls
```
This should show all the stacks, look for the one named "akkaclusterswarm" or whatever is the name of the stack.

To see the services deployed by the stack:
```
docker stack ps akkaclusterswarm 
```

This should list all the 3 services that are listed on the docker-compose.yml file along with its state

###See logs/details of the state of a service

Once the command for deploying is used, only a single line indicating that the service is deployed(for each service)
is shown, which is not useful at all.

To see the state of each service deployed use the previous command to see the services of the satck,
look for the column ID of the service and then:
```
docker inspect serviceid
```
This will show full information about the service, look at the property: "Status", which will show
useful info in case there is a problem starting the service such as: 

"No such image" or "No suitable nodes"

###See the logs of each container:

To see the logs of each container do:
```
docker ps -a
```

This will list the containers, look for the ones that correspond to the services, for this look at the last column
which have the name of the container, it should be something like: "akkaclusterswarm_frontend".

Then do the following to see in real time the logs of the container:

```
docker logs --follow containerid   
```

###Use docker swarm visualizer

There is a very nice visualizer for the containers/services deployed on a docker swarm.
Looks at the repo of the project: 

[Docker Swarm Visualizer](https://github.com/dockersamples/docker-swarm-visualizer)



### Description

Project starts with class "main.Main" on root
There are other two classes as well:

* main.Backendmain
* Frontendmain

Each one initializes the respective systems

main.Main: it creates two instances of the backend, with specified ports.
The ports have to be the same as the seed nodes specified on the "application.conf" file, otherwise no cluster is
created.

The constant name: CLUSTER_SYSTEM_NAME has to be the same across all the systems created in the cluster.
This allows for all the created actor systems to belong to the same cluster otherwise it will create individual
clusters.

main.Backendmain: Node/System in charge of receiving tasks from frontend and producing a result. It is listening to "MemberUp"
events, so in case a new "frontend" member joins, it will tell it that it is available to process tasks.

Frontendmain: Node/System in charge of assigning tasks to backend. It has a list of backends that can process
some work. 

Backend: Actor class for the backend, that listens and carry out tasks from frontend.

Frontend: Actor class that registers workes(backends) and forward the tasks to an available backend.

AppMessages: All the messages for the application. In general there are 3 types of messages: 

* JobMessage,  which carries the job to be done. 
* ResultMessage, message that carries the result of the computation
* FailedMessage, message that carries information about the failing computation 

### Application.conf file

Basic configuration file. Provider is set to cluster. 

There are 2 seed nodes, one is deployed on a raspberry pi device and the other on the linux machine.

The hostnames and ports of the nodes are being set as environment variables on the docker-compose.yml file. 
Then on the application.conf these variables are obtained using the HOCON syntax and being replaced in the 
corresponding places.

### Cluster management (HTTP)

The cluster management is set to use the HTTP API, that can be found on:
 [Cluster Http Management](https://developer.lightbend.com/docs/akka-management/current/cluster-http-management.html)

To use it for queries and actions, is better to use Postman: [Postman](https://www.getpostman.com/)


Now go to postman, or use any browser, on any node of the cluster and go to:
```
http://nodeip:8558/cluster/members/
```
or using curl:
```
curl http://nodeip:8558/cluster/members/
```

Where the nodeip is IP address of the node.
The response is in a json format which shows the members of the node. To see more api endpoints see the 
link provided above.

The service is available at port 8558 on any node of the cluster, which is using the routing mesh of docker,
therefore it is accessible on any node, even though the service is only set on seed2. See the official documentation:
 [Publish ports](https://docs.docker.com/v17.12/engine/swarm/services/#publish-ports)

The address and port binding are set in the "application.conf" file under the "management" section.
See that the hostname is set for the "clustering.seed2-ip" node. Which is the ip of the node running the 
cluster management as stated on the file "BackendMain.java".
When using docker it is necessary to define the bindings otherwise it won't work.
