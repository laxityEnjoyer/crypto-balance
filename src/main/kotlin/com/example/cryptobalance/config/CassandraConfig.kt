package com.example.cryptobalance.config

import com.datastax.oss.driver.api.core.CqlSession
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetSocketAddress

/**
 * Spring configuration for the Apache Cassandra CQL session.
 *
 * Establishes a single [CqlSession] bean that is shared across the application.
 * Connection parameters are externalised to `application.yml` so that the service
 * can be pointed at different Cassandra instances without code changes (e.g. local
 * Docker for development, a managed cluster in production).
 *
 * @param contactPoint Cassandra contact point in `host:port` format
 *                     (default: `127.0.0.1:9042`).
 * @param keyspace     Cassandra keyspace to bind the session to (default: `trx`).
 * @param localDc      Name of the local datacenter used by the driver's
 *                     load-balancing policy (default: `datacenter1`).
 */
@Configuration
class CassandraConfig(
    @Value("\${cassandra.contactPoint:127.0.0.1:9042}") private val contactPoint: String,
    @Value("\${cassandra.keyspace:trx}") private val keyspace: String,
    @Value("\${cassandra.localDc:datacenter1}") private val localDc: String
) {
    /**
     * Creates and returns a [CqlSession] connected to the configured Cassandra node.
     *
     * The session is scoped to the configured [keyspace], so all CQL statements
     * that omit an explicit keyspace qualifier will operate within it by default.
     */
    @Bean
    fun cqlSession(): CqlSession {
        val (host, portStr) = contactPoint.split(":")
        return CqlSession.builder()
            .addContactPoint(InetSocketAddress(host, portStr.toInt()))
            .withLocalDatacenter(localDc)
            .withKeyspace(keyspace)
            .build()
    }
}
