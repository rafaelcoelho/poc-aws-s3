package com.br.poc.s3.poc_express

import com.br.poc.s3.poc_express.S3USConfig.S3Clients
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.nio.charset.StandardCharsets
import kotlin.time.Duration
import kotlin.time.measureTime

@SpringBootApplication
class PocExpressApplication

fun main(args: Array<String>) {
    runApplication<PocExpressApplication>(*args)
}

@Configuration
class S3USConfig(
    @Value("\${S3_ENDPOINT:\"https://s3.us-east-1.amazonaws.com}\"")
    private val endpoint: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(S3USConfig::class.java)
    }

    data class S3Clients(val clients: Map<String, S3Client>)

    @Bean
    fun s3Clients(): S3Clients {
        return S3Clients(mapOf("us" to s3ClientUs(), "sa" to s3ClientSa()))
    }

    fun s3ClientUs(): S3Client {
        logger.info("S3 Endpoint $endpoint")

        return S3Client.builder()
//            .endpointOverride(URI.create("https://s3express-use1-az4.us-east-1.amazonaws.com"))
//            .endpointOverride(URI.create(endpoint.trim()))
            .region(Region.US_EAST_1)
            .build()
    }

    fun s3ClientSa(): S3Client {
        logger.info("S3 Endpoint $endpoint")

        return S3Client.builder()
//            .endpointOverride(URI.create("https://s3express-use1-az4.us-east-1.amazonaws.com"))
//            .endpointOverride(URI.create(endpoint.trim()))
            .region(Region.SA_EAST_1)
            .build()
    }
}

@RestController
@RequestMapping("/data/v1/s3")
class S3Controller(
    private val s3: S3Clients,
    @Value("\${BUCKET_EXPRESS:default-bucket}")
    private val expressGeneralBucket: String,
    @Value("\${BUCKET_GENERAL:default-bucket}")
    private val generalBucket: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(S3Controller::class.java)
    }

    data class Payload(val result: String, val duration: Duration)


    @GetMapping("/express/{client_prefix}")
    suspend fun readObjectExpress(@PathVariable("client_prefix") client: String): ResponseEntity<String> {
        return kotlin.runCatching {
            var result = "empty"

            val duration = measureTime {
                logger.info("Serving read request for $client")

                result = s3.clients["us"]!!.getObject { it.bucket(expressGeneralBucket).key(client) }
                    .let { StreamUtils.copyToString(it, StandardCharsets.UTF_8) }
                    .also { logger.info("Get object from S3 done with size ${it.length}") }
            }
                .also { logger.warn("Get objet from \t $expressGeneralBucket-$client \t in $it") }

            Payload(result, duration)
        }
            .onFailure { logger.error("Error to server the request cause=${it.message}", it) }
            .getOrElse { Payload("Internal Error", Duration.ZERO) }
            .let {
                ResponseEntity.ok(
                    """
                    "size": "${it.result.length}",
                    "duration": "${it.duration}"
                    """".trimIndent()
                )
            }
    }

    @GetMapping("/general/{client_prefix}")
    suspend fun readObjectGeneral(@PathVariable("client_prefix") client: String): ResponseEntity<String> {
        return kotlin.runCatching {
            var result = "empty"

            val duration = measureTime {
                logger.info("Serving read request for $client")

                result = s3.clients["sa"]!!.getObject { it.bucket(generalBucket).key(client) }
                    .let { StreamUtils.copyToString(it, StandardCharsets.UTF_8) }
                    .also { logger.info("Get object from S3 done with size ${it.length}") }
            }
                .also { logger.warn("Get objet from \t $generalBucket-$client \t\t in $it") }

            Payload(result, duration)
        }
            .onFailure { logger.error("Error to server the request cause=${it.message}", it) }
            .getOrElse { Payload("Internal Error", Duration.ZERO) }
            .let {
                ResponseEntity.ok(
                    """
                    "size": "${it.result.length}",
                    "duration": "${it.duration}"
                    """".trimIndent()
                )
            }
    }
}