/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.s3.javadsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorSystem;
import akka.stream.alpakka.s3.ListBucketResultContents;
import akka.stream.alpakka.s3.MultipartUploadResult;
import akka.stream.alpakka.s3.ObjectMetadata;
import org.junit.Test;
import akka.NotUsed;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.ByteRange;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.alpakka.s3.impl.S3Headers;
import akka.stream.alpakka.s3.impl.ServerSideEncryption;
import akka.stream.alpakka.s3.scaladsl.S3WireMockBase;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import scala.Option;

public class S3ClientTest extends S3WireMockBase {

  private final ActorSystem system = system();
  private final Materializer materializer = ActorMaterializer.create(system());

  @Test
  public void multipartUpload() throws Exception {

    mockUpload();

    // #upload
    final Sink<ByteString, Source<MultipartUploadResult, NotUsed>> sink =
        S3.multipartUpload(bucket(), bucketKey());
    // #upload

    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        Source.single(ByteString.fromString(body())).runWith(sink, materializer);

    MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        MultipartUploadResult.create(
            Uri.create(url()), bucket(), bucketKey(), etag(), Optional.empty()),
        result);
  }

  @Test
  public void multipartUploadSSE() throws Exception {

    mockUploadSSE();

    // #upload
    final Sink<ByteString, Source<MultipartUploadResult, NotUsed>> sink =
        S3.multipartUpload(bucket(), bucketKey(), sseCustomerKeys());
    // #upload

    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        Source.single(ByteString.fromString(body())).runWith(sink, materializer);

    MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        MultipartUploadResult.create(
            Uri.create(url()), bucket(), bucketKey(), etag(), Optional.empty()),
        result);
  }

  @Test
  public void download() throws Exception {

    mockDownload();

    // #download
    final Source<Optional<Pair<Source<ByteString, NotUsed>, ObjectMetadata>>, NotUsed>
        sourceAndMeta = S3.download(bucket(), bucketKey());
    final Source<ByteString, NotUsed> source =
        sourceAndMeta
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS)
            .get()
            .first();
    // #download

    final CompletionStage<String> resultCompletionStage =
        source.map(ByteString::utf8String).runWith(Sink.head(), materializer);

    String result = resultCompletionStage.toCompletableFuture().get(5, TimeUnit.SECONDS);

    assertEquals(body(), result);
  }

  @Test
  public void head() throws Exception {

    long contentLength = 8L;
    mockHead(contentLength);

    // #objectMetadata
    final Source<Optional<ObjectMetadata>, NotUsed> source =
        S3.getObjectMetadata(bucket(), bucketKey());
    // #objectMetadata

    Optional<ObjectMetadata> result =
        source.runWith(Sink.head(), materializer).toCompletableFuture().get(5, TimeUnit.SECONDS);

    final ObjectMetadata objectMetadata = result.get();
    Optional<String> s3eTag = objectMetadata.getETag();
    long actualContentLength = objectMetadata.getContentLength();
    Optional<String> versionId = objectMetadata.getVersionId();

    assertEquals(s3eTag, Optional.of(etag()));
    assertEquals(actualContentLength, contentLength);
    assertEquals(versionId, Optional.empty());
  }

  @Test
  public void headWithVersion() throws Exception {
    String versionId = "3/L4kqtJlcpXroDTDmJ+rmSpXd3dIbrHY+MTRCxf3vjVBH40Nr8X8gdRQBpUMLUo";
    mockHeadWithVersion(versionId);

    // #objectMetadata
    final Source<Optional<ObjectMetadata>, NotUsed> source =
        S3.getObjectMetadata(bucket(), bucketKey(), Optional.of(versionId), null);
    // #objectMetadata

    Optional<ObjectMetadata> result =
        source.runWith(Sink.head(), materializer).toCompletableFuture().get(5, TimeUnit.SECONDS);

    final ObjectMetadata objectMetadata = result.get();
    Optional<String> s3eTag = objectMetadata.getETag();
    Optional<String> metadataVersionId = objectMetadata.getVersionId();

    assertEquals(s3eTag, Optional.of(etag()));
    assertEquals(metadataVersionId, Optional.of(versionId));
  }

  @Test
  public void headServerSideEncryption() throws Exception {
    mockHeadSSEC();

    // #objectMetadata
    final Source<Optional<ObjectMetadata>, NotUsed> source =
        S3.getObjectMetadata(bucket(), bucketKey(), sseCustomerKeys());
    // #objectMetadata

    Optional<ObjectMetadata> result =
        source.runWith(Sink.head(), materializer).toCompletableFuture().get(5, TimeUnit.SECONDS);

    Optional<String> etag = result.get().getETag();

    assertEquals(etag, Optional.of(etagSSE()));
  }

  @Test
  public void downloadServerSideEncryption() throws Exception {
    mockDownloadSSEC();

    // #download
    final Source<Optional<Pair<Source<ByteString, NotUsed>, ObjectMetadata>>, NotUsed>
        sourceAndMeta = S3.download(bucket(), bucketKey(), sseCustomerKeys());
    // #download

    final Source<ByteString, NotUsed> source =
        sourceAndMeta
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS)
            .get()
            .first();
    final CompletionStage<String> resultCompletionStage =
        source.map(ByteString::utf8String).runWith(Sink.head(), materializer);

    String result = resultCompletionStage.toCompletableFuture().get(5, TimeUnit.SECONDS);

    assertEquals(bodySSE(), result);
  }

  @Test
  public void downloadServerSideEncryptionWithVersion() throws Exception {
    String versionId = "3/L4kqtJlcpXroDTDmJ+rmSpXd3dIbrHY+MTRCxf3vjVBH40Nr8X8gdRQBpUMLUo";
    mockDownloadSSECWithVersion(versionId);

    // #download
    final Source<Optional<Pair<Source<ByteString, NotUsed>, ObjectMetadata>>, NotUsed>
        sourceAndMeta =
            S3.download(bucket(), bucketKey(), null, Optional.of(versionId), sseCustomerKeys());
    // #download

    final Source<ByteString, NotUsed> source =
        sourceAndMeta
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS)
            .get()
            .first();
    final CompletionStage<String> resultCompletionStage =
        source.map(ByteString::utf8String).runWith(Sink.head(), materializer);
    final ObjectMetadata metadata =
        sourceAndMeta
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS)
            .get()
            .second();
    final String result = resultCompletionStage.toCompletableFuture().get();

    assertEquals(bodySSE(), result);
    assertEquals(Optional.of(versionId), metadata.getVersionId());
  }

  @Test
  public void rangedDownload() throws Exception {

    mockRangedDownload();

    // #rangedDownload
    final Source<Optional<Pair<Source<ByteString, NotUsed>, ObjectMetadata>>, NotUsed>
        sourceAndMeta =
            S3.download(
                bucket(), bucketKey(), ByteRange.createSlice(bytesRangeStart(), bytesRangeEnd()));
    // #rangedDownload

    final Source<ByteString, NotUsed> source =
        sourceAndMeta
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS)
            .get()
            .first();
    final CompletionStage<byte[]> resultCompletionStage =
        source.map(ByteString::toArray).runWith(Sink.head(), materializer);

    byte[] result = resultCompletionStage.toCompletableFuture().get(5, TimeUnit.SECONDS);

    assertTrue(Arrays.equals(rangeOfBody(), result));
  }

  @Test
  public void rangedDownloadServerSideEncryption() throws Exception {

    mockRangedDownloadSSE();

    // #rangedDownload
    final Source<Optional<Pair<Source<ByteString, NotUsed>, ObjectMetadata>>, NotUsed>
        sourceAndMeta =
            S3.download(
                bucket(),
                bucketKey(),
                ByteRange.createSlice(bytesRangeStart(), bytesRangeEnd()),
                sseCustomerKeys());
    // #rangedDownload

    final Source<ByteString, NotUsed> source =
        sourceAndMeta
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS)
            .get()
            .first();
    final CompletionStage<byte[]> resultCompletionStage =
        source.map(ByteString::toArray).runWith(Sink.head(), materializer);

    byte[] result = resultCompletionStage.toCompletableFuture().get(5, TimeUnit.SECONDS);

    assertTrue(Arrays.equals(rangeOfBodySSE(), result));
  }

  @Test
  public void listBucket() throws Exception {

    mockListBucket();

    // #list-bucket
    final Source<ListBucketResultContents, NotUsed> keySource =
        S3.listBucket(bucket(), Option.apply(listPrefix()));
    // #list-bucket

    final CompletionStage<ListBucketResultContents> resultCompletionStage =
        keySource.runWith(Sink.head(), materializer);

    ListBucketResultContents result =
        resultCompletionStage.toCompletableFuture().get(5, TimeUnit.SECONDS);

    assertEquals(result.key(), listKey());
  }

  @Test
  public void copyUploadWithContentLengthLessThenChunkSize() throws Exception {
    mockCopy();

    String bucket = bucket();
    String sourceKey = bucketKey();
    String targetBucket = targetBucket();
    String targetKey = targetBucketKey();
    // #multipart-copy
    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        S3.multipartCopy(bucket, sourceKey, targetBucket, targetKey).run(materializer);
    // #multipart-copy

    final MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        result,
        MultipartUploadResult.create(
            Uri.create(targetUrl()), targetBucket(), targetBucketKey(), etag(), Optional.empty()));
  }

  @Test
  public void copyUploadWithSourceVersion() throws Exception {
    mockCopyVersioned();

    String bucket = bucket();
    String sourceKey = bucketKey();
    String targetBucket = targetBucket();
    String targetKey = targetBucketKey();

    // #multipart-copy-with-source-version
    String sourceVersionId = "3/L4kqtJlcpXroDTDmJ+rmSpXd3dIbrHY+MTRCxf3vjVBH40Nr8X8gdRQBpUMLUo";
    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        S3.multipartCopy(
                bucket,
                sourceKey,
                targetBucket,
                targetKey,
                Optional.of(sourceVersionId),
                S3Headers.empty(),
                null)
            .run(materializer);
    // #multipart-copy-with-source-version

    final MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        result,
        MultipartUploadResult.create(
            Uri.create(targetUrl()),
            targetBucket(),
            targetBucketKey(),
            etag(),
            Optional.of("43jfkodU8493jnFJD9fjj3HHNVfdsQUIFDNsidf038jfdsjGFDSIRp")));
  }

  @Test
  public void copyUploadWithContentLengthEqualToChunkSize() throws Exception {
    mockCopy(5242880);

    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        S3.multipartCopy(bucket(), bucketKey(), targetBucket(), targetBucketKey())
            .run(materializer);
    final MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        result,
        MultipartUploadResult.create(
            Uri.create(targetUrl()), targetBucket(), targetBucketKey(), etag(), Optional.empty()));
  }

  @Test
  public void copyUploadWithContentLengthGreaterThenChunkSize() throws Exception {
    mockCopyMulti();

    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        S3.multipartCopy(bucket(), bucketKey(), targetBucket(), targetBucketKey())
            .run(materializer);
    final MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        result,
        MultipartUploadResult.create(
            Uri.create(targetUrl()), targetBucket(), targetBucketKey(), etag(), Optional.empty()));
  }

  @Test
  public void copyUploadEmptyFile() throws Exception {
    mockCopy(0);

    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        S3.multipartCopy(bucket(), bucketKey(), targetBucket(), targetBucketKey())
            .run(materializer);
    final MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        result,
        MultipartUploadResult.create(
            Uri.create(targetUrl()), targetBucket(), targetBucketKey(), etag(), Optional.empty()));
  }

  @Test
  public void copyUploadWithCustomHeader() throws Exception {
    mockCopy();

    final Source<MultipartUploadResult, NotUsed> resultCompletionStage =
        S3.multipartCopy(
                bucket(),
                bucketKey(),
                targetBucket(),
                targetBucketKey(),
                S3Headers.empty(),
                ServerSideEncryption.AES256$.MODULE$)
            .run(materializer);

    final MultipartUploadResult result =
        resultCompletionStage
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertEquals(
        result,
        MultipartUploadResult.create(
            Uri.create(targetUrl()), targetBucket(), targetBucketKey(), etag(), Optional.empty()));
  }
}
