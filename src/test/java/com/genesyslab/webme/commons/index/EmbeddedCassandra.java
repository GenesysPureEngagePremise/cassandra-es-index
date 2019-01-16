/*
 * Copyright 2019 Genesys Telecommunications Laboratories, Inc.
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
package com.genesyslab.webme.commons.index;

import org.apache.cassandra.service.CassandraDaemon;

/**
 * Useful to debug the index
 * Created by Jacques-Henri Berthemet on 18/07/2017.
 */
public class EmbeddedCassandra {

  //use below java args, start in cassandra folder and change 'cassandra.yaml' location:
  //-Dcassandra.config=file:\\\\\\conf\cassandra.yaml -Dcassandra -Dlogback.configurationFile=logback.xml -Dcassandra.logdir=logs -Dcassandra.storagedir=data -Xloggc:logs/gc.log -ea -XX:+UseThreadPriorities -XX:ThreadPriorityPolicy=42 -XX:+HeapDumpOnOutOfMemoryError -Xss256k -XX:StringTableSize=1000003 -XX:+AlwaysPreTouch -XX:-UseBiasedLocking -XX:+UseTLAB -XX:+ResizeTLAB -XX:+UseNUMA -XX:+PerfDisableSharedMem -Djava.net.preferIPv4Stack=true -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:SurvivorRatio=8 -XX:MaxTenuringThreshold=1 -XX:CMSInitiatingOccupancyFraction=75 -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSWaitDuration=10000 -XX:+CMSParallelInitialMarkEnabled -XX:+CMSEdenChunksRecordAlways -XX:+CMSClassUnloadingEnabled -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintHeapAtGC -XX:+PrintTenuringDistribution -XX:+PrintGCApplicationStoppedTime -XX:+PrintPromotionFailure -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=10M -Xms4096M -Xmx4096M -Xmn800M -XX:+UseCondCardMark -Djava.library.path=lib/sigar-bin -XX:CompileCommandFile=conf/hotspot_compiler -javaagent:lib/jamm-0.3.0.jar -Dcassandra.jmx.local.port=7199

  // Set working directory to cassandra installation directory (<path>\apache-cassandra-3.11.0)
  public static void main(String... args) {
    System.setProperty("cassandra-foreground", "true");
    CassandraDaemon.main(args);
  }
}
