package com.roky.casestudy.config

import com.roky.casestudy.coupon.CouponIssueKafkaProducer
import com.roky.casestudy.coupon.dto.CouponIssueEvent
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
@EnableKafka
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id:coupon-issue-consumer}") private val consumerGroupId: String,
    @Value("\${spring.kafka.consumer.properties.max.poll.records:10}") private val maxPollRecords: Int,
    @Value("\${spring.kafka.listener.concurrency:1}") private val listenerConcurrency: Int,
    @Value("\${spring.kafka.consumer.properties.spring.json.trusted.packages:com.roky.casestudy.coupon.dto}")
    private val trustedPackages: String,
    @Value("\${coupon.kafka.issue.topic-partitions:8}") private val couponIssueTopicPartitions: Int,
    @Value("\${coupon.kafka.issue.topic-replication-factor:1}") private val couponIssueTopicReplicationFactor: Int,
) {
    @Bean
    fun couponIssueTopic(): NewTopic =
        TopicBuilder
            .name(CouponIssueKafkaProducer.TOPIC)
            .partitions(couponIssueTopicPartitions.coerceAtLeast(1))
            .replicas(couponIssueTopicReplicationFactor.coerceAtLeast(1))
            .build()

    @Bean
    fun couponIssueProducerFactory(): ProducerFactory<String, CouponIssueEvent> =
        DefaultKafkaProducerFactory(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ),
        )

    @Bean
    fun couponIssueKafkaTemplate(
        couponIssueProducerFactory: ProducerFactory<String, CouponIssueEvent>,
    ): KafkaTemplate<String, CouponIssueEvent> = KafkaTemplate(couponIssueProducerFactory)

    @Bean
    fun couponIssueConsumerFactory(): ConsumerFactory<String, CouponIssueEvent> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to consumerGroupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecords,
                JsonDeserializer.TRUSTED_PACKAGES to trustedPackages,
                JsonDeserializer.VALUE_DEFAULT_TYPE to CouponIssueEvent::class.java.name,
            ),
        )

    @Bean
    fun kafkaListenerContainerFactory(
        couponIssueConsumerFactory: ConsumerFactory<String, CouponIssueEvent>,
    ): ConcurrentKafkaListenerContainerFactory<String, CouponIssueEvent> {
        val containerFactory = ConcurrentKafkaListenerContainerFactory<String, CouponIssueEvent>()
        containerFactory.setConsumerFactory(couponIssueConsumerFactory)
        containerFactory.setConcurrency(listenerConcurrency)
        containerFactory.setBatchListener(true)
        containerFactory.containerProperties.ackMode = ContainerProperties.AckMode.BATCH
        return containerFactory
    }
}
