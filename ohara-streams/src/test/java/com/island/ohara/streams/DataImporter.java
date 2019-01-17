/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.island.ohara.streams;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.*;
import javassist.bytecode.ClassFile;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"rawtypes"})
class DataImporter {

  private static final Logger log = LoggerFactory.getLogger(DataImporter.class);

  private static final String HELP_KEY = "--help";
  private static final String SERSERS_KEY = "--bootstrapServers";
  private static final String OHARA_API_KEY = "--useOharaAPI";
  private static final String USAGE = String.format("[USAGE] %s %s", SERSERS_KEY, OHARA_API_KEY);

  private static String PORTS = "9092";

  private static String TOPIC_CARRIERS = "carriers";
  private static String TOPIC_PLANE = "plane";
  private static String TOPIC_AIRPORT = "airport";
  private static String TOPIC_FLIGHT = "flight";

  public static void main(String[] args) {

    if (args != null && args.length == 1 && args[0].equals(HELP_KEY)) {
      System.out.println(USAGE);
      return;
    }

    if (args != null && args.length % 2 == 0) {
      String bootstrapServers = "";
      boolean useOharaAPI = false;

      for (int i = 0; i < args.length; i += 2) {
        String key = args[i];
        String value = args[i + 1];

        if (key.equals(SERSERS_KEY)) {
          bootstrapServers = value;
        } else if (key.equals(OHARA_API_KEY)) {
          useOharaAPI = Boolean.valueOf(value);
        }
      }

      if (bootstrapServers.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        String localIP = getLocalHostAddress();
        for (String p : PORTS.split(",")) {
          sb.append(localIP).append(":").append(p).append(",");
        }

        int length = sb.toString().length();
        bootstrapServers = sb.toString().substring(0, length - 1);
      }

      importData(bootstrapServers, useOharaAPI);
    } else {
      log.info(USAGE);
    }
  }

  static void importData(String bootStrapServer, boolean useOharaAPI) {

    String prefix = "src/test/data";
    Path fileCarrier = Paths.get(prefix, "/carriers.csv");
    Path filePlane = Paths.get(prefix, "/plane-data.csv");
    Path fileAirport = Paths.get(prefix, "/airports.csv");
    Path fileFlight2007 = Paths.get(prefix, "/2007-small.csv");
    Path fileFlight2008 = Paths.get(prefix, "/2008-small.csv");

    try (KafkaProducer<String, String> producer = createKafkaProducer(bootStrapServer)) {
      if (useOharaAPI) {
        // TODO : implement ohara producer import logic
      } else {
        ExecutorService executor = Executors.newCachedThreadPool();

        Future f1 =
            executor.submit(
                () -> {
                  asyncImportFile(producer, TOPIC_CARRIERS, fileCarrier, 10, useOharaAPI);
                });
        Future f2 =
            executor.submit(
                () -> {
                  asyncImportFile(producer, TOPIC_PLANE, filePlane, 10, useOharaAPI);
                });
        Future f3 =
            executor.submit(
                () -> {
                  asyncImportFile(producer, TOPIC_AIRPORT, fileAirport, 10, useOharaAPI);
                });
        Future f4 =
            executor.submit(
                () -> {
                  asyncImportFile(producer, TOPIC_FLIGHT, fileFlight2007, 10, useOharaAPI);
                });
        Future f5 =
            executor.submit(
                () -> {
                  asyncImportFile(producer, TOPIC_FLIGHT, fileFlight2008, 10, useOharaAPI);
                });

        f1.get();
        f2.get();
        f3.get();
        f4.get();
        f5.get();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void asyncImportFile(
      KafkaProducer<String, String> producer,
      String topic,
      Path file,
      int sleepMills,
      boolean useOharaAPI) {
    try {
      Stream<String> lines = Files.lines(file).skip(1);
      lines.forEach(
          line -> {
            if (useOharaAPI) {
              // TODO : implement ohara producer import logic
            } else {
              kafkaSendLine(producer, topic, line);
            }
            if (sleepMills > 0) {
              try {
                Thread.sleep(sleepMills);
              } catch (Exception e) {
              }
            }
          });
    } catch (Exception e) {
    }
  }

  private static void kafkaSendLine(
      KafkaProducer<String, String> producer, String topicName, String line) {
    ProducerRecord<String, String> record = new ProducerRecord<>(topicName, line);
    producer.send(record, new ProducerCallback());
  }

  private static void oharaSendLine() {
    // TODO : using miniCluster to send data
  }

  static KafkaProducer<String, String> createKafkaProducer(String bootstrapServers) {
    Properties props = new Properties();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "simple-producer-group");
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    return new KafkaProducer<>(props);
  }

  static KafkaConsumer<String, String> createKafkaConsumer(String bootstrapServers) {
    Properties prop = new Properties();
    prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    prop.put(ConsumerConfig.GROUP_ID_CONFIG, "simple-consumer-group");
    prop.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    prop.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    prop.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    prop.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    return new KafkaConsumer<>(prop);
  }

  /**
   * Dynamic generate class from csv file and get all data (note : you should give the header in
   * file) The result class name will be {@code CSV_{filename.toUpper}}
   *
   * @param filename the csv file name
   * @return
   */
  static List<?> readData(String filename) throws Exception {
    String prefix = "src/test/data";
    Path p = Paths.get(prefix, File.separator, filename);
    if (!p.toFile().exists()) throw new FileNotFoundException(filename + " not exists.");
    Optional<String> header = Files.lines(p).findFirst();
    String trueHeaders =
        header.orElseThrow(
            () -> new RuntimeException("the file : " + filename + " has no header."));
    String className = filename.replace(".csv", "").toUpperCase();

    Class<?> rowClass = buildClass(className, trueHeaders);

    return Files.lines(p)
        .skip(1)
        .filter(s -> s != null && !s.isEmpty())
        .map(line -> line.split(","))
        .map(
            values -> {
              try {
                Object rowObject = rowClass.newInstance();
                Field[] fields = rowClass.getDeclaredFields();
                for (int i = 0; i < fields.length; i++) {
                  fields[i].setAccessible(true);
                  fields[i].set(rowObject, values[i]);
                }
                return rowObject;
              } catch (Exception e) {
                log.error(e.getMessage());
                return null;
              }
            })
        .collect(Collectors.toList());
  }

  private static Class<?> buildClass(String className, String headers)
      throws CannotCompileException, NotFoundException {
    String[] fields = headers.split(",");
    ClassPool pool = ClassPool.getDefault();
    CtClass ctc = pool.makeClass("CSV_" + className);
    ClassFile classFile = ctc.getClassFile();

    classFile.setSuperclass(Object.class.getName());
    for (String fieldName : fields) {
      CtField field = new CtField(pool.get(String.class.getName()), fieldName, ctc);
      ctc.addField(field);
    }
    return ctc.toClass();
  }

  private static String getLocalHostAddress() {
    String sAddr = "";
    try {
      List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
      for (NetworkInterface inter : interfaces) {
        List<InetAddress> addrs = Collections.list(inter.getInetAddresses());
        for (InetAddress addr : addrs) {
          if (!addr.isLoopbackAddress()) {
            sAddr = addr.getHostAddress();
          }
        }
      }

      sAddr = sAddr.isEmpty() ? InetAddress.getLocalHost().getHostAddress() : sAddr;
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return sAddr;
  }
}
