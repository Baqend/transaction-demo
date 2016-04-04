Welcome to Baqend transactions
=======
This small example project has two purposes:

1. It shows how to connect to a running Baqend-Server using the Java-SDK.
2. It showcases how to use Baqend's distribute cache-aware transactions.

Running the Example Project
--------
0. Clone this project.
1.  Download the (free) community version of the Baqend-Server [here](http://www.baqend.com/product.html#download) and unzip it.
2. Activate the transaction feature in the config file under `/conf/sample_config.json` by adding the following option `"transaction": { "enabled": true }`.
3. Start the Baqend-Server (for unix: `./baqend`). It will run on localhost on port 8080.
4. Now the dashboard will be [online](http://localhost:8080/dashboard/app/default/start).
5. Start the `main`-method in the class `TransactionTest`

We've kept the footprint of the example as low as possible while showing most of the transaction features in a non trivial example. Of course you can use the example to build your own prototype. The **Java-SDK** is a simple, low level, (actually internal) API with not the best documentation. However, it works quite similar to our main **JavaScript SDK**  that is well [documented](http://www.baqend.com/guide/) and comes with a [quick-start-guide](http://www.baqend.com/guide/gettingstarted/). Both SDKs are based on the **REST-API**. If your are interested in how Baqend increases web performance you may visit our [blog](http://blog.baqend.com) or read up on the [scientific research](http://www.baqend.com/#final-remark) Baqend is based on.

 Building Apps and Websites with Baqend
----------
The Baqend Community Edition is a free, single-server variant of the Baqend platform (think: MySQL). It comes with all our Backend-as-a-Service features (user management, cloud code, etc.) and is free for personal and commercial use.

As a single-server solution it it best used for prototyping, testing and staging. For full cache support, elastic scalability and automated cloud hosting try [Baqend Cloud](http://www.baqend.com/product.html#pricing) or [Baqend Enterprise](http://www.baqend.com/product.html#enterprise) for on premise deployments.

