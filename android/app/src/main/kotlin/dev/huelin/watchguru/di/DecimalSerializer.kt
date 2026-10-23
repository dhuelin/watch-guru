package dev.huelin.watchguru.di

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * Reads and writes a [BigDecimal] the way the API actually sends one: as a
 * JSON number.
 *
 * The generated client's own adapter treats a decimal as a string, both ways.
 * The backend sends `"providerRating": 8.7`, unquoted, and a strict [Json]
 * refuses an unquoted token where a string was asked for -- so every title,
 * search result and library page failed to decode, and the screen said
 * "Something went wrong" over a 200. In the other direction a rating went up
 * as the string `"7"`, which only worked because the server happened to be
 * lenient about it.
 *
 * This lives in the app rather than as an edit to the generated adapter
 * because `api-client/src` is regenerated wholesale; a fix made there would
 * last until the next `generate-clients.sh`. [NetworkModule.json] lays it over
 * the generated module instead.
 *
 * A quoted decimal is still accepted. Snapshots cached before this fix were
 * written as strings, and a train journey is a bad time to discover the cache
 * no longer reads.
 */
object DecimalSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.huelin.watchguru.BigDecimal", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): BigDecimal =
        if (decoder is JsonDecoder) {
            // The element's content is the literal text of the token, quoted or
            // not, so 8.7 arrives as "8.7" and never passes through a double --
            // which is the whole reason the model uses BigDecimal at all.
            BigDecimal(decoder.decodeJsonElement().jsonPrimitive.content)
        } else {
            BigDecimal(decoder.decodeString())
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            // An unquoted literal rather than encodeDouble: a double would turn
            // 7.1 into 7.1000000000000005 on the way out. toPlainString, not
            // toString, because toString can produce "7E+1", which some JSON
            // parsers reject.
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }
}
