package zio.temporal.protobuf

import com.google.protobuf.ByteString
import scalapb.GeneratedMessage
import scalapb.GeneratedSealedOneof
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.math
import java.{util => ju}
import scala.annotation.implicitNotFound

/** Typeclass allowing conversion to protocol buffers supported type
  *
  * @tparam A
  *   value type
  */
@implicitNotFound("""Type ${A} cannot be encoded as protocol buffers type.
It should be an existing type or a type generated by scalapb library""")
trait ProtoType[A] {

  /** Protocol buffers representation of this type */
  type Repr

  /** Converts a value of this type to its protocol buffers representation
    * @param value
    *   input value
    * @return
    *   value protocol buffers representation
    */
  def repr(value: A): Repr

  /** Creates a value of this type from its protocol buffers representation
    * @param repr
    *   value protocol buffers representation
    * @return
    *   value of this type
    */
  def fromRepr(repr: Repr): A

  /** Creates a new protocol buffers type based on this representation. via InvariantFunctor.imap
    *
    * @tparam B
    *   new value type
    * @param f
    *   function projecting this type into the given one
    * @param g
    *   reverse projection function
    * @return
    *   new protocol buffers type
    */
  final def convertTo[B](f: A => B)(g: B => A): ProtoType.Of[B, Repr] =
    new ProtoType.ConvertedType[A, Repr, B](this, f, g)
}

object ProtoType {
  type Of[A, Repr0] = ProtoType[A] { type Repr = Repr0 }

  implicit val integerType: ProtoType.Of[Int, Int]                        = IntegerType
  implicit val longType: ProtoType.Of[Long, Long]                         = LongType
  implicit val booleanType: ProtoType.Of[Boolean, Boolean]                = BooleanType
  implicit val stringType: ProtoType.Of[String, String]                   = StringType
  implicit val bytesType: ProtoType.Of[Array[Byte], Array[Byte]]          = BytesType
  implicit val uuidType: ProtoType.Of[ju.UUID, UUID]                      = UuidType
  implicit val bigIntType: ProtoType.Of[BigInt, BigInteger]               = BigIntegerType
  implicit val bigDecimalType: ProtoType.Of[scala.BigDecimal, BigDecimal] = BigDecimalType
  implicit val unitType: ProtoType.Of[Unit, ZUnit]                        = ZUnitType

  implicit val instantType: ProtoType.Of[Instant, Long] =
    longType.convertTo(Instant.ofEpochMilli)(_.toEpochMilli)

  implicit val localDateTimeType: ProtoType.Of[LocalDateTime, Long] =
    instantType.convertTo(_.atOffset(ZoneOffset.UTC).toLocalDateTime)(_.atOffset(ZoneOffset.UTC).toInstant)

  private final object IntegerType extends IdType[Int]
  private final object LongType    extends IdType[Long]
  private final object BooleanType extends IdType[Boolean]
  private final object StringType  extends IdType[String]
  private final object BytesType   extends IdType[Array[Byte]]

  final class GeneratedMessageType[A <: GeneratedMessage] extends IdType[A]
  final class SealedOneOfType[A <: GeneratedSealedOneof]  extends IdType[A]

  final object UuidType extends ProtoType[ju.UUID] {
    override type Repr = UUID

    override def repr(value: ju.UUID): UUID =
      UUID.of(
        mostSignificantBits = value.getMostSignificantBits,
        leastSignificantBits = value.getLeastSignificantBits
      )

    override def fromRepr(repr: UUID): ju.UUID =
      new ju.UUID(repr.mostSignificantBits, repr.leastSignificantBits)
  }

  final object BigDecimalType extends ProtoType[scala.BigDecimal] {
    override type Repr = BigDecimal

    override def repr(value: scala.BigDecimal): BigDecimal =
      BigDecimal(
        scale = value.scale,
        intVal = BigIntegerType.repr(value.bigDecimal.unscaledValue())
      )

    override def fromRepr(repr: BigDecimal): scala.BigDecimal =
      new scala.BigDecimal(new math.BigDecimal(BigIntegerType.fromRepr(repr.intVal).bigInteger, repr.scale))
  }

  final object BigIntegerType extends ProtoType[scala.BigInt] {
    override type Repr = BigInteger

    override def repr(value: BigInt): BigInteger =
      BigInteger(value = ByteString.copyFrom(value.toByteArray))

    override def fromRepr(repr: BigInteger): BigInt =
      new BigInt(new math.BigInteger(repr.value.toByteArray))
  }

  final object ZUnitType extends ProtoType[Unit] {
    override type Repr = ZUnit

    override def repr(value: Unit): ZUnitType.Repr = ZUnit()

    override def fromRepr(repr: ZUnitType.Repr): Unit = ()
  }

  final class ConvertedType[A, Repr0, B](self: ProtoType.Of[A, Repr0], project: A => B, reverse: B => A)
      extends ProtoType[B] {
    override type Repr = Repr0

    override def repr(value: B): Repr0 = self.repr(reverse(value))

    override def fromRepr(repr: Repr0): B = project(self.fromRepr(repr))
  }

  sealed abstract class IdType[A] extends ProtoType[A] {
    override type Repr = A

    override def repr(value: A): A    = value
    override def fromRepr(repr: A): A = repr
  }
}