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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.kafka.clients.consumer.Consumer;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.services.event.EventDispatcherListener;

import io.smallrye.reactive.messaging.kafka.KafkaConsumer;

public class IncomingKafkaMessageThrottlingService implements EventDispatcherListener {

    private static final Logger LOGGER = Logger
            .getLogger(IncomingKafkaMessageThrottlingService.class.getName());

    private final KafkaConsumer<?, ?> smallryeKafkaConsumer;
    private final AtomicInteger unacknowledgedMessageCounter = new AtomicInteger(0);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final long unacknowledgedMessageLimit;

    public IncomingKafkaMessageThrottlingService(
            final KafkaConsumer<?, ?> smallryeKafkaConsumer,
            final long unacknowledgedMessageLimit) {
        this.smallryeKafkaConsumer = smallryeKafkaConsumer;
        this.unacknowledgedMessageLimit = unacknowledgedMessageLimit;
    }

    @Override
    public void onItem(final Object payload) {
        if (unacknowledgedMessageCounter.incrementAndGet() >= unacknowledgedMessageLimit) {
            if (!paused.getAndSet(true)) {
                LOGGER.warning(
                        "Kafka incoming message throttling, consumer SUCCESSFULLY PAUSED");
            }
            smallryeKafkaConsumer.pause().subscribe().with(pausedTopicPartitions -> {
                if (pausedTopicPartitions.isEmpty()) {
                    LOGGER.warning(
                            "Couldn't pause consumer properly, try again");
                    final Consumer<?, ?> apacheKafkaConsumer = smallryeKafkaConsumer.unwrap();
                    apacheKafkaConsumer.pause(apacheKafkaConsumer.assignment());
                }
            });
        }
    }

    @Override
    public void onComplete(final ProcessInstance<?> processInstance, final Throwable ex) {
        if (unacknowledgedMessageCounter.decrementAndGet() < unacknowledgedMessageLimit &&
                paused.compareAndSet(true, false)) {
            smallryeKafkaConsumer.resume().subscribe().with(ignore -> LOGGER.warning(
                    "Kafka incoming message throttling, consumer SUCCESSFULLY RESUMED"));
        }
    }
}
