Welcome to Baqend Transactions
=======

What this Example Project is for
--------
1. It shows how to connect to a running Baqend-Server using the Java-SDK.
2. It showcases how to use Baqend's distributed cache-aware transactions.

Running the Example Project
--------
1. Setup the Baqend-Server as your Backend:
	1.  Download the (free) community version of the Baqend-Server [here](http://www.baqend.com/product.html#download) and unzip it.
	2. Start the Baqend-Server (for unix: `./baqend`). It will run on [localhost](http://localhost:8080/dashboard/) port 8080. Your done.

2. Clone and setup this project:
	1. IntelliJ: File -> New -> Project from Existing Sources... -> Select `build.gradle`, use the default gradle wrapper and Java 8. Start the main-method in class `TransactionTest`.
	2. Eclipse: run `sh gradlew eclipse` in the project's directory to create the eclipse project like [here](http://stackoverflow.com/questions/10722773/import-existing-gradle-git-project-into-eclipse-for-example-hibernate-orm). In Eclipse: File -> Import -> General -> Existing Project Into Workspace -> Select project directory -> Finish. Start the main-method in class `TransactionTest`.
	3. Without IDE: run `sh gradlew run` in the project's directory to execute the test. More information on how to use the Gradle-Wrapper can be found [here](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

We've tried to keep the footprint of the example as low as possible while showing most of the transaction features in a non trivial example. The example uses the asynchronous transaction api, a synchronous api is available too. Of course you can use the example to build your own prototype.

The **Java-SDK** is a simple, low level, (actually internal) API with not the best documentation. However, it works quite similar to our main **JavaScript SDK** that is well [documented](http://www.baqend.com/guide/) and comes with a [quick-start-guide](http://www.baqend.com/guide/gettingstarted/). Both SDKs are based on the **REST-API**. If your are interested in how Baqend increases web performance you may visit our [blog](http://blog.baqend.com/post/139788321880/bringing-web-performance-to-the-next-level-an) or read up on the [scientific research](http://www.baqend.com/#final-remark) Baqend is based on.

Building Apps and Websites with Baqend
----------
The Baqend Community Edition is a free, single-server variant of the Baqend platform (think: MySQL). It comes with all our Backend-as-a-Service features (user management, cloud code, etc.) and is free for personal and commercial use.

As a single-server solution it it best used for prototyping, testing and staging. For full cache support, elastic scalability and automated cloud hosting try [Baqend Cloud](http://www.baqend.com/product.html#pricing) or [Baqend Enterprise](http://www.baqend.com/product.html#enterprise) for on premise deployments.

Baqend Transactions
=======
Orestes offers an optimistic cache-aware transaction model to achive serializability with full ACID semantics while sustaining low latency, high scalability and availability. To this end, transactions are executed without synchonization and validated at commit time.

How the Transaction Work
-----------
uring a transaction reads are preferably served from caches and writes are applied to a local copy of the data. Both read and write sets are sent to the server at commit. At commit the server validates the read and write sets to ensure serializability. After a successful validation the write set is applied to the database. A failed validation results in the abort of the transaction.

This optimistic transaction model achives minimum latency, high scalability and availability under the assumption that conflicting transactions are rare. Even though our caching architecture reduces the effect, the downside of the optimistic model is that high conflict rates lead to transaction aborts.

This protocol is an improved variant of what in the literature is called BOCC+.

Reducing Transaction Aborts
---------
To counteract the problem of transaction aborts we implemented optimizations for both read and write operations.

For write operations we can use so called partial updates where entities can be updated without a preceding read of the entity. These operations do not conflict with each other und thus do not lead to transaction aborts. Furthermore partial updates reduce latency.

As another means of optimization we implemented RAMP read-only transactions that can locally validate a read set and reload outdated entities instead of aborting the transaction and sometimes even without contacting the server. While RAMP is only applicable for special use cases it can reduce aborts and latency to almost zero.