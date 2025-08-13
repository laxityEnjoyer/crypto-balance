package com.example.cryptobalance.config

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetSocketAddress

@Configuration
class CassandraConfig(
    @Value("\${cassandra.contactPoint}") private val contactPoint: String,
    @Value("\${cassandra.keyspace}") private val keyspace: String
) {
    @Bean
    fun cqlSession(): CqlSession {
        val (host, port) = contactPoint.split(":")
        return CqlSession.builder()
            .addContactPoint(InetSocketAddress(host, port.toInt()))
            .withLocalDatacenter("datacenter1")
            .withKeyspace(CqlIdentifier.fromCql(keyspace))
            .build()
    }
}
