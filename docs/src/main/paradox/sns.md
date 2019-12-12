# AWS SNS

The AWS SNS connector provides an Akka Stream Flow and Sink for push notifications through AWS SNS.

For more information about AWS SNS please visit the [official documentation](https://aws.amazon.com/documentation/sns/).

@@project-info{ projectId="sns" }

## Artifacts

@@dependency [sbt,Maven,Gradle] {
  group=com.lightbend.akka
  artifact=akka-stream-alpakka-sns_$scala.binary.version$
  version=$project.version$
}

The table below shows direct dependencies of this module and the second tab shows all libraries it depends on transitively.

@@dependencies { projectId="sns" }


## Setup

This connector requires an implicit @javadoc[SnsAsyncClient](software.amazon.awssdk.services.sns.SnsAsyncClient) instance to communicate with AWS SQS.

It is your code's responsibility to call `close` to free any resources held by the client. In this example it will be called when the actor system is terminated.

Scala
: @@snip [snip](/sns/src/test/scala/akka/stream/alpakka/sns/IntegrationTestContext.scala) { #init-client }

Java
: @@snip [snip](/sns/src/test/java/docs/javadsl/SnsPublisherTest.java) { #init-client }

The example above uses @extref:[Akka HTTP](akka-http:) as the default HTTP client implementation. For more details about the HTTP client, configuring request retrying and best practices for credentials, see @ref[AWS client configuration](aws-shared-configuration.md) for more details.

We will also need an @scaladoc[ActorSystem](akka.actor.ActorSystem) and an @scaladoc[ActorMaterializer](akka.stream.ActorMaterializer).

Scala
: @@snip [snip](/sns/src/test/scala/akka/stream/alpakka/sns/IntegrationTestContext.scala) { #init-system }

Java
: @@snip [snip](/sns/src/test/java/docs/javadsl/SnsPublisherTest.java) { #init-system }

This is all preparation that we are going to need.

## Publish messages to an SNS topic

Now we can publish a message to any SNS topic where we have access to by providing the topic ARN to the
@scaladoc[SnsPublisher](akka.stream.alpakka.sns.scaladsl.SnsPublisher$) Flow or Sink factory method.

### Using a Flow

Scala
: @@snip [snip](/sns/src/test/scala/docs/scaladsl/SnsPublisherSpec.scala) { #use-flow }

Java
: @@snip [snip](/sns/src/test/java/docs/javadsl/SnsPublisherTest.java) { #use-flow }

As you can see, this would publish the messages from the source to the specified AWS SNS topic.
After a message has been successfully published, a
[PublishResult](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/sns/model/PublishResult.html)
will be pushed downstream.

### Using a Sink

Scala
: @@snip [snip](/sns/src/test/scala/docs/scaladsl/SnsPublisherSpec.scala) { #use-sink }

Java
: @@snip [snip](/sns/src/test/java/docs/javadsl/SnsPublisherTest.java) { #use-sink }

As you can see, this would publish the messages from the source to the specified AWS SNS topic.

@@@ index

* [retry conf](aws-shared-configuration.md)

@@@
