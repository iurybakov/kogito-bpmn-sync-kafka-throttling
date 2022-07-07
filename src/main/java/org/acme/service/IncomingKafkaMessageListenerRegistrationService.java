/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.service;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.kie.kogito.event.KogitoEventExecutor;
import org.kie.kogito.services.event.impl.AbstractMessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.kafka.KafkaConnector;
import io.smallrye.reactive.messaging.kafka.KafkaConsumer;

@ApplicationScoped
@IfBuildProperty(name = "kogito.quarkus.events.kafka.throttling.enabled", stringValue = "true", enableIfMissing = true)
public class IncomingKafkaMessageListenerRegistrationService {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(IncomingKafkaMessageListenerRegistrationService.class);

    @ConfigProperty(name = KogitoEventExecutor.MAX_THREADS_PROPERTY, defaultValue = KogitoEventExecutor.DEFAULT_MAX_THREADS)
    int numThreads;

    @ConfigProperty(name = KogitoEventExecutor.QUEUE_SIZE_PROPERTY, defaultValue = KogitoEventExecutor.DEFAULT_QUEUE_SIZE)
    int queueSize;

    @Inject
    @Connector(KafkaConnector.CONNECTOR_NAME)
    KafkaConnector kafkaConnector;

    @Inject
    @Any
    Instance<AbstractMessageConsumer<?, ?>> messageConsumers;

    void onStart(@Observes StartupEvent ev) {
        final int unprocessedEventLimit = numThreads - 1;
        for (final AbstractMessageConsumer<?, ?> messageConsumer : messageConsumers) {
            final String triggerName = messageConsumer.getTriggerName();
            if (isKafkaRecordStreamSubscriptionConfigValid(triggerName)) {
                final KafkaConsumer<?, ?> smallryeKafkaConsumer =
                        kafkaConnector.getConsumer(triggerName);
                if (smallryeKafkaConsumer != null) {
                    messageConsumer.registerDispatcherListener(
                            new IncomingKafkaMessageThrottlingService(
                                    smallryeKafkaConsumer,
                                    unprocessedEventLimit));
                }
            }
        }
    }

    /**
     * Check limit buffered messages on Kafka client not greater than worker thread pool queue size
     * and system resuming consumer is disabled
     *
     * @param triggerName channel of message stream
     * @return false if 'queueSize' of worker ExecutorService lower than 'maxQueueSize' of
     *         KafkaRecordStreamSubscription, else true
     * @see io.smallrye.reactive.messaging.kafka.impl.KafkaRecordStreamSubscription
     *      KafkaRecordStreamSubscription#maxQueueSize
     */
    private boolean isKafkaRecordStreamSubscriptionConfigValid(final String triggerName) {
        final Config config = ConfigProvider.getConfig();
        final String maxPollRecordsProp =
                ConnectorFactory.INCOMING_PREFIX + triggerName + ".max.poll.records";
        final String maxQueSizeFactorProp =
                ConnectorFactory.INCOMING_PREFIX + triggerName + ".max-queue-size-factor";
        final String pauseIfNoRequestProp =
                ConnectorFactory.INCOMING_PREFIX + triggerName + ".pause-if-no-request";
        final int maxQueSizeFactor = config.getOptionalValue(maxQueSizeFactorProp, Integer.class)
                .orElse(2);
        final boolean pauseIfNoRequest = config.getOptionalValue(pauseIfNoRequestProp, Boolean.class)
                .orElse(true);
        final int maxPollRecords = config.getOptionalValue(maxPollRecordsProp, Integer.class)
                .orElse(500);
        if (pauseIfNoRequest) {
            LOGGER.warn("Pause if no request must be disabled '{} = false'. Kafka incoming message"
                    + " throttling activation skipped for channel '{}'",
                    pauseIfNoRequestProp,
                    triggerName);
            return false;
        }
        if (queueSize < maxQueSizeFactor * maxPollRecords * 2) {
            LOGGER.warn(
                    "Worker thread pool queue size is lower then Kafka record stream subscription "
                            + "max queue size, throttling cannot work properly. Kafka incoming message"
                            + " throttling activation skipped for channel '{}'. Please change config, "
                            + "where '{}' >= '{}' * '{}' * 2",
                    triggerName,
                    KogitoEventExecutor.QUEUE_SIZE_PROPERTY,
                    maxPollRecordsProp,
                    maxQueSizeFactorProp);
            return false;
        }
        return true;
    }
}
