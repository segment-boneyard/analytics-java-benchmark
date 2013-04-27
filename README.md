Java Library Benchmark
========================

This benchmark illustrates the basic flushing principles of the
the [Segment.io](https://segment.io) [java library](https://github.com/segmentio/analytics-java/).

## Goals

Our libraries should be convenient and should not force the user to have to write extra code or worry about performance.

Our libraries should not cause the crash of the application.

Equivalently, we shouldn't starve the host from resources like CPU, memory, or network even when there's a lot of data going through the library.

## Benchmark

The benchmark calls

```java
Analytics.track(userId, "Benchmark");
```

at a specified rate. Every 1 second, the benchmark samples these variables:

#### Variables
* The system's CPU %
* The JVM's memory usage (`Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()`)
* The amount of messages inserted into the queue
* The amount of messages successfully sent to the server
* The amount of messages that failed to make it to the server
* The current queue size

#### Testbed

This benchmark was run on jdk7 on my retina Mac OS X mountain lion laptop.


## Non-Blocking

Our client libraries use in-memory queues to make sure your calls to:

```java
Analytics.identify(...)
Analytics.track(...)
```

return very quickly, and **without waiting*** for the actual HTTP request to our servers to happen. This allows you to send us data from web servers and other performance sensitive code without worrying about us blocking the calling thread.

When you call `Analytics.track` or any similar methods, all we do is validate the input and put it in the in memory queue. In practice, we found this takes less than a millisecond.

## Flushing

The library will use exactly one extra thread to constantly flush the queue.

The flushing thread does the following:

```
do
  batch = []
  do
    msg = wait_for_message(queue)
    batch += msg
  while batch.size() <= 20 and queue.size() > 0

  if batch.size > 0
    flush(batch)

while active
```

It will wait until the queue has a message. As soon as there's something to send, the flushing thread will collect as much as 20 messages. Once it has collected its current batch, it will make the request to the server. Then, repeat.

The advantage is, if you aren't flushing many messages, your messages will be sent immediately. However, if you're sending tons of messages, the flushing thread will collect large batches and send them together to maximize the request throughput and decrease TCP connection overhead.

Even at ah high rate of 50 requests a second, the flushing thread can match the insert rate:

![](http://i.imgur.com/YavECJ5.png)

In this [test](https://docs.google.com/spreadsheet/ccc?key=0AvP3ixW_RotVdFVQZW5NZ3F4TV9ra3N0N0hjbElsTEE&usp=sharing), we can see that the queue rarely grows since the flushing thread can flush as fast as messages are coming in.

### CPU Usage

Since there's only one flushing thread, both CPU and network won't be saturated.

Here's the CPU usage over the 50 requests per second test:

![](http://i.imgur.com/aSgtnR7.png)

And then again at 500 requests per second:

![](http://i.imgur.com/qAm7m4J.png)

### Maximum Queue Size

In situations where more messages are coming in than can be flushed, the library avoids running out of memory by disallowing new messages into the queue. The `maximumQueueSize` defaults to `10000`.

Here's a demonstration of 500 analytics calls each second:

![](http://i.imgur.com/2eXc8VX.png)

In this [test](https://docs.google.com/spreadsheet/ccc?key=0AvP3ixW_RotVdHdDbTJzc05hLXRzNHpPUmZsNkpOZXc&usp=sharing), more messages are being added than can be flushed. You can see that the queue size grows until its at 10,000 at which time it stops accepting new messages until more are flushed.

Looking at the JVM's memory (`totalMemory - freeMemory`) during this time, you can see that this constraint prevents the memory from overflowing:

![](http://i.imgur.com/5li3VNz.png)

Here's another [test](https://docs.google.com/spreadsheet/ccc?key=0AvP3ixW_RotVdE9nNGh2ODVYeFlkR2ppX0Z0Wi1Senc&usp=sharing) at 500 requests per second, except without a maximum queue size constraint:

![](http://i.imgur.com/gHB4sIb.png)

And you can see the memory growing out of control:

![](http://i.imgur.com/Uz5a8dB.png)

## Run it yourself

You can run the benchmark yourself via the `JavaClientBenchmark` main class.