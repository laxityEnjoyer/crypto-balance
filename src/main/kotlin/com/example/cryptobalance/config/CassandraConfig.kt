package com.example.cryptobalance.config

import com.datastax.oss.driver.api.core.CqlSession
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetSocketAddress

@Configuration
class CassandraConfig(
    @Value("\${cassandra.contactPoint:127.0.0.1:9042}") private val contactPoint: String,
    @Value("\${cassandra.keyspace:trx}") private val keyspace: String,
    @Value("\${cassandra.localDc:datacenter1}") private val localDc: String
) {
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
