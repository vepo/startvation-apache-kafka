package io.vepo.starvation.finder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.OffsetSpec.LatestSpec;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarvationFinder {
    private static final Logger logger = LoggerFactory.getLogger(StarvationFinder.class);

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        var latch = new CountDownLatch(1);
        var running = new AtomicBoolean(true);
        Runtime.getRuntime()
               .addShutdownHook(new Thread("shutdown-hook") {
                   @Override
                   public void run() {
                       System.out.println("Shutdown requested!");
                       running.set(false);
                       try {
                           latch.await();
                       } catch (InterruptedException ie) {
                           Thread.currentThread().interrupt();
                       }
                   }
               });
        var adminClient = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                                                    "kafka-0:9092,kafka-1:9094,kafka-2:9096"));
        logger.info("Initializing Starvation Finder!");
        var groupOffset = new HashMap<String, Map<TopicPartition, Long>>();
        var partitionPosition = new HashMap<TopicPartition, Long>();
        while (running.get()) {
            logger.info("Listing consumer groups...");

            loadGroupIdOffsets(adminClient, groupOffset);
            loadProducerPosition(adminClient, groupOffset, partitionPosition);
            System.out.println("""
                               ===========================================================================
                               GROUP ID                 PARTITION                LAG
                               """);
            groupOffset.forEach((groupId,
                                 partitions) -> partitions.forEach((partition,
                                                                    consumerOffset) -> System.out.println(String.format("%s%s%s%s%s",
                                                                                                                        limit(groupId),
                                                                                                                        padding(groupId),
                                                                                                                        partition.toString(),
                                                                                                                        padding(partition.toString()),
                                                                                                                        Long.toString(partitionPosition.getOrDefault(partition,
                                                                                                                                                                     0L)
                                                                                                                                - consumerOffset)))));
            Thread.sleep(1_000);
        }
    }

    private static void loadProducerPosition(AdminClient adminClient,
                                             HashMap<String, Map<TopicPartition, Long>> groupOffset,
                                             HashMap<TopicPartition, Long> partitionPosition)
            throws InterruptedException, ExecutionException {
        try {
            adminClient.listOffsets(topicPartitions(groupOffset))
                       .all()
                       .get()
                       .forEach((partition, position) -> partitionPosition.put(partition, position.offset()));
        } catch (ExecutionException ee) {
            logger.error("Error!", ee);
        }
    }

    private static final String padding(String value) {
        return IntStream.range(value.length(), 25)
                        .mapToObj(__ -> " ")
                        .collect(Collectors.joining());
    }

    private static String limit(String value) {
        return value.length() > 25 ? value.substring(0, 25) : value;
    }

    private static Map<TopicPartition, OffsetSpec> topicPartitions(HashMap<String, Map<TopicPartition, Long>> groupOffset) {
        return groupOffset.values()
                          .stream()
                          .map(Map::keySet)
                          .flatMap(Set::stream)
                          .collect(Collectors.toMap(Function.identity(), __ -> new LatestSpec()));
    }

    private static void loadGroupIdOffsets(AdminClient adminClient,
                                           HashMap<String, Map<TopicPartition, Long>> groupOffset)
            throws InterruptedException, ExecutionException {
        try {
            adminClient.listConsumerGroups()
                       .all()
                       .get()
                       .forEach(cgl -> {
                           try {
                               adminClient.listConsumerGroupOffsets(cgl.groupId())
                                          .all()
                                          .get()
                                          .forEach((groupId, groupIdMetadata) -> {
                                              var groupIdOffsets =
                                                      groupOffset.computeIfAbsent(groupId, __ -> new HashMap<>());
                                              groupIdMetadata.forEach((partition, metadata) -> {
                                                  groupIdOffsets.put(partition, metadata.offset());
                                              });

                                          });
                           } catch (InterruptedException | ExecutionException e) {
                               Thread.currentThread().interrupt();
                           }
                       });
        } catch (ExecutionException ee) {
            logger.error("Error!", ee);
        }
    }
}
