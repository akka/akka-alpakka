package akka.stream.alpakka.avroparquet.impl
import akka.annotation.InternalApi
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.avro.generic.GenericRecord
import org.apache.parquet.hadoop.ParquetWriter

/**
 * Internal API
 */
@InternalApi
private[avroparquet] class AvroParquetFlow(writer: ParquetWriter[GenericRecord])
    extends GraphStage[FlowShape[GenericRecord, GenericRecord]] {

  val in: Inlet[GenericRecord] = Inlet("AvroParquetSink.in")
  val out: Outlet[GenericRecord] = Outlet("AvroParquetSink.out")
  override val shape: FlowShape[GenericRecord, GenericRecord] = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(
        in,
        new InHandler {

          override def onUpstreamFinish(): Unit =
            //super.onUpstreamFinish()
            completeStage()

          override def onUpstreamFailure(ex: Throwable): Unit = {
            super.onUpstreamFailure(ex)
            writer.close()
          }

          override def onPush(): Unit = {
            val obtainedValue = grab(in)
            writer.write(obtainedValue)
            push(out, obtainedValue)
          }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit =
          pull(in)
      })

      override def postStop(): Unit = writer.close()
    }
}
