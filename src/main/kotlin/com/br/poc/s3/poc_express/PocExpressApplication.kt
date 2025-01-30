package com.br.poc.s3.poc_express

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3ClientBuilder
import java.nio.charset.StandardCharsets
import kotlin.time.measureTime

@SpringBootApplication
class PocExpressApplication

fun main(args: Array<String>) {
	runApplication<PocExpressApplication>(*args)
}

@Configuration
class S3USConfig {

	@Bean
	fun s3Client(): S3Client {
		return S3Client.builder()
			.region(Region.US_EAST_1)
			.credentialsProvider(
				AwsCredentialsProviderChain.of(
					EnvironmentVariableCredentialsProvider.create(),
					WebIdentityTokenFileCredentialsProvider.create()
				)
			)
			.build()
	}
}

@RestController
@RequestMapping("/data/v1/s3")
class S3Controller(
	private val s3: S3Client,
	@Value("\${BUCKET:default-bucket}")
	private val bucket: String
) {
	companion object {
		private val logger = LoggerFactory.getLogger(S3Controller::class.java)
	}

	@GetMapping("/express/{client_prefix}")
	suspend fun readObject(@RequestParam("client_prefix") client: String) : ResponseEntity<String> {
		return kotlin.runCatching {
			var result = "empty"

			measureTime {
				logger.info("Serving read request for $client")

				result = s3.getObject { it.bucket(bucket).key(client) }
					.let { StreamUtils.copyToString(it, StandardCharsets.UTF_8) }
					.also { logger.info("Get object from S3 done with size ${it.length}") }
			}
				.also { logger.warn("Get objet from $bucket-$client in ${it}ms") }

			ResponseEntity.ok(result)
		}
			.onFailure { logger.error("Error to server the request", it) }
			.getOrElse { ResponseEntity.internalServerError().body("Internal Error") }
	}
}