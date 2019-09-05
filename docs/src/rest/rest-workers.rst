..
.. Copyright 2019 is-land
..
.. Licensed under the Apache License, Version 2.0 (the "License");
.. you may not use this file except in compliance with the License.
.. You may obtain a copy of the License at
..
..     http://www.apache.org/licenses/LICENSE-2.0
..
.. Unless required by applicable law or agreed to in writing, software
.. distributed under the License is distributed on an "AS IS" BASIS,
.. WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
.. See the License for the specific language governing permissions and
.. limitations under the License.
..

.. _rest-workers:

Worker
======

`Worker <https://kafka.apache.org/intro>`__ is core of running
connectors for ohara. It provides a simple but powerful system to
distribute and execute connectors on different nodes. The performance of
connectors depends on the scale of worker cluster. For example, you can
assign the number of task when creating connector. If there is only 3
nodes within your worker cluster and you specify 6 tasks for your
connector, the tasks of you connectors still be deployed on 3 nodes.
That is to say, the connector can’t get more resources to execute.

Worker is based on :ref:`Broker <rest-brokers>`, hence you have to create broker
cluster first. Noted that a broker cluster can be used by multi worker
clusters. BTW, worker cluster will pre-allocate a lot of topics on
broker cluster, and the pre-created topics CAN’T be reused by different
worker clusters.

The properties which can be set by user are shown below.

#. settings (**objects**) — cluster settings passed by user

   - name (**string**) — cluster name
   - imageName (**string**) — docker image
   - brokerClusterName (**string**) — broker cluster used to host topics for this worker cluster
   - clientPort (**int**) — worker client port
   - jmxPort (**int**) — worker jmx port
   - freePorts (**Array(int)**) — thr ports you want to pre-bind for the connectors. If your connectors want
     to build a service on a port which is available to external nodes, you have to
     define the free ports for your worker cluster so as to make Configurator pre-bind
     the ports on your worker cluster. Otherwise, your connectors is disable to build service
     on the port of worker cluster and be connected by external node.
   - groupId (**string**) — the id of worker stored in broker cluster
   - configTopicName (**string**) — a internal topic used to store connector configuration
   - configTopicReplications (**int**) — number of replications for config topic
   - offsetTopicName (**string**) — a internal topic used to store connector offset
   - offsetTopicPartitions (**int**) — number of partitions for offset topic
   - offsetTopicReplications (**int**) — number of replications for offset topic
   - statusTopicName (**string**) — a internal topic used to store connector status
   - statusTopicPartitions (**int**) — number of partitions for status topic
   - statusTopicReplications (**int**) — number of replications for status topic
   - jarKeys (**array(object)**) — the “primary key” of jars that will be loaded by worker cluster.
     You can require worker cluster to load the jars stored in ohara if you want to run custom connectors
     on the worker cluster. see :ref:`Files APIs <rest-files>` for uploading jars to ohara. Noted: the response
     will replace this by :ref:`JarInfo <rest-files>`.
   - nodeNames (**array(string)**) — the nodes running the worker process

#. deadNodes (**array(string)**) — the nodes that have failed containers of worker

    .. note::
       The groupId, configTopicName, offsetTopicName and statusTopicName
       must be unique in broker cluster. Don’t reuse them in same broker
       cluster. Dispatching above unique resources to two worker cluster
       will pollute the data. Of course, ohara do a quick failure for this
       dumb case. However, it is not a quick failure when you are using raw
       kafka rather than ohara. Please double check what you configure!

    After building the worker cluster, ohara starts to fetch the details of
    available connectors from the worker cluster. The details is the setting
    definitions of connector. It shows how to assign the settings to a
    connector correctly. The details of connector’s setting definitions can
    be retrieved via :ref:`GET <rest-workers-get>` or :ref:`LIST <rest-workers-list>`,
    and the JSON representation is shown below.

    .. code-block:: json

       {
         "connectors": [
           {
             "className": "xxx",
             "definitions": [
               {
                 "reference": "NONE",
                 "displayName": "connector.class",
                 "internal": false,
                 "documentation": "the class name of connector",
                 "valueType": "CLASS",
                 "tableKeys": [],
                 "orderInGroup": 0,
                 "key": "connector.class",
                 "required": true,
                 "defaultValue": null,
                 "group": "core",
                 "editable": true
               }
             ]
           }
         ]
       }

#. connectors (**array(string)**) — the available connectors of worker cluster

   - connectors[i].className (**string**) — the class name of available connector
   - connectors[i].definitions (**array(object)**) — the settings used by this connector

     - connectors[i].definitions[j].displayName (**string**) — the
       readable name of this setting
     - connectors[i].definitions[j].group (**string**) — the group of
       this setting (all core setting are in core group)
     - connectors[i].definitions[j].orderInGroup (**int**) — the order in
       group
     - connectors[i].definitions[j].editable (**boolean**) — true if this
       setting is modifiable
     - connectors[i].definitions[j].key (**string**) — the key of
       configuration
     - connectors[i].definitions[j]. :ref:`valueType <setting-definition-type>` (**string**) — the type of value
     - connectors[i].definitions[j].defaultValue (**string**) — the
       default value
     - connectors[i].definitions[j].documentation (**string**) — the
       explanation of this definition
     - connectors[i].definitions[j]. :ref:`reference <setting-definition-reference>` (**string**) — works for ohara manager.
       It represents the reference of value.
     - connectors[i].definitions[j].required (**boolean**) — true if
       this setting has no default value and you have to assign a value.
       Otherwise, you can’t start connector.
     - connectors[i].definitions[j].internal (**string**) — true if this
       setting is assigned by system automatically.
     - connectors[i].definitions[j].tableKeys (**array(string)**) — the
       column name when the type is TABLE

  Apart from official settings (topics, columns, etc), a connector also
  have custom settings. Those setting definition can be found through
  :ref:`GET <rest-workers-get>` or :ref:`LIST <rest-workers-list>`.
  And for another, the worker
  cluster needs to take some time to load available connectors. If you
  don’t see the setting definitions, please retry it later.

.. _rest-workers-create:

create a worker properties
--------------------------

*POST /v0/workers*

#. name (**string**) — cluster name
#. imageName (**string**) — docker image
#. clientPort (**int**) — worker client port.
#. jmxPort (**int**) — worker jmx port.
#. brokerClusterName (**string**) — broker cluster used to host topics
   for this worker cluster
#. jarKeys (**array(object)**) — the “primary key” object list of jar
   that will be loaded by worker cluster

   - jarKeys[i].group (**string**) — the group name of jar
   - jarKeys[i].name (**string**) — the name of jar

#. groupId (**string**) — the id of worker stored in broker cluster
#. configTopicName (**string**) — a internal topic used to store connector configuration
#. configTopicReplications (**int**) — number of replications for config topic
#. offsetTopicName (**string**) — a internal topic used to store connector offset
#. offsetTopicReplications (**int**) — number of replications for offset topic
#. offsetTopicPartitions (**int**) — number of partitions for offset topic
#. statusTopicName (**string**) — a internal topic used to store connector status
#. statusTopicReplications (**int**) — number of replications for status topic
#. statusTopicPartitions (**int**) — number of partitions for status topic
#. nodeNames (**array(string)**) — the nodes running the worker process

Example Request
  .. code-block:: json

    {
      "name": "wk00",
      "clientPort": 12345,
      "jmxPort": 12346,
      "freePorts": [],
      "brokerClusterName": "preCreatedBkCluster",
      "groupId": "abcdefg",
      "configTopicName": "configTopic",
      "configTopicReplications": 1,
      "offsetTopicName": "offsetTopic",
      "offsetTopicReplications": 1,
      "offsetTopicPartitions": 1,
      "statusTopicName": "statusTopic",
      "statusTopicReplications": 1,
      "statusTopicPartitions": 1,
      "nodeNames": [
        "node00"
      ]
    }

Example Response
  .. code-block:: json

    {
      "lastModified": 1567177024356,
      "connectors": [],
      "deadNodes": [],
      "settings": {
        "statusTopicName": "statusTopic",
        "name": "wk00",
        "offsetTopicPartitions": 1,
        "brokerClusterName": "preCreatedBkCluster",
        "tags": {},
        "jarInfos": [],
        "offsetTopicName": "offsetTopic",
        "imageName": "oharastream/connect-worker:0.8.0-SNAPSHOT",
        "groupId": "abcdefg",
        "statusTopicReplications": 1,
        "offsetTopicReplications": 1,
        "configTopicReplications": 1,
        "statusTopicPartitions": 1,
        "configTopicName": "configTopic",
        "jmxPort": 12346,
        "clientPort": 12345,
        "freePorts": [],
        "jarKeys": [],
        "nodeNames": [
          "node00"
        ]
      }
    }

  As mentioned before, ohara provides default to most settings. You can
  just input name, nodeNames and jars to run a worker cluster.

Example Request
  .. code-block:: json

    {
      "name": "wk",
      "nodeNames": [
        "node10"
      ]
    }

  .. note::
     As you don’t input the broker cluster name, Ohara will try to pick up
     a broker cluster for you. If the number of broker cluster host by
     ohara is only one, ohara do deploy worker cluster on the broker
     cluster. Otherwise, ohara will say that it can’t match a broker
     cluster for you. All ports have default value so you can ignore them
     when creating worker cluster. However, the port conflict detect does
     not allow you to reuse port on different purpose (a dangerous behavior, right?).

Example Response
  .. code-block:: json

    {
      "lastModified": 1567176877946,
      "connectors": [],
      "deadNodes": [],
      "settings": {
        "statusTopicName": "a6c5018531",
        "name": "wk",
        "offsetTopicPartitions": 1,
        "brokerClusterName": "bk",
        "tags": {},
        "jarInfos": [],
        "offsetTopicName": "6ec3cd5f1b",
        "imageName": "oharastream/connect-worker:0.8.0-SNAPSHOT",
        "groupId": "979a8c0c18",
        "statusTopicReplications": 1,
        "offsetTopicReplications": 1,
        "configTopicReplications": 1,
        "statusTopicPartitions": 1,
        "configTopicName": "4fdfdabb51",
        "jmxPort": 37116,
        "clientPort": 37634,
        "freePorts": [],
        "jarKeys": [],
        "nodeNames": [
          "node10"
        ]
      }
    }

.. _rest-workers-list:

list all workers clusters
-------------------------

*GET /v0/workers*

Example Response
  .. code-block:: json

    [
      {
        "lastModified": 1567177024356,
        "connectors": [],
        "deadNodes": [],
        "settings": {
          "statusTopicName": "statusTopic",
          "name": "wk00",
          "offsetTopicPartitions": 1,
          "brokerClusterName": "preCreatedBkCluster",
          "tags": {},
          "jarInfos": [],
          "offsetTopicName": "offsetTopic",
          "imageName": "oharastream/connect-worker:0.8.0-SNAPSHOT",
          "groupId": "abcdefg",
          "statusTopicReplications": 1,
          "offsetTopicReplications": 1,
          "configTopicReplications": 1,
          "statusTopicPartitions": 1,
          "configTopicName": "configTopic",
          "jmxPort": 12346,
          "clientPort": 12345,
          "freePorts": [],
          "jarKeys": [],
          "nodeNames": [
            "node00"
          ],
        }
      }
    ]


delete a worker properties
--------------------------

*DELETE /v0/workers/$name*

You cannot delete properties of an non-stopped worker cluster.

Example Response
  ::

     204 NoContent

  .. note::
     It is ok to delete an nonexistent worker cluster, and the response is
     204 NoContent.

.. _rest-workers-get:

get a worker cluster
--------------------

*GET /v0/workers/$name*

Example Response
  .. code-block:: json

      {
         "lastModified":1567178933996,
         "connectors":[
            {
               "className":"com.island.ohara.connector.console.ConsoleSink",
               "definitions":[
                  {
                     "reference":"NONE",
                     "displayName":"Connector name",
                     "internal":true,
                     "documentation":"the name of this connector",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":1,
                     "key":"name",
                     "required":true,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"Connector class",
                     "internal":false,
                     "documentation":"the class name of connector",
                     "valueType":"CLASS",
                     "tableKeys":[

                     ],
                     "orderInGroup":2,
                     "key":"connector.class",
                     "required":true,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"Number of tasks",
                     "internal":false,
                     "documentation":"the number of tasks invoked by connector",
                     "valueType":"INT",
                     "tableKeys":[

                     ],
                     "orderInGroup":5,
                     "key":"tasks.max",
                     "required":true,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"key converter",
                     "internal":true,
                     "documentation":"key converter",
                     "valueType":"CLASS",
                     "tableKeys":[

                     ],
                     "orderInGroup":8,
                     "key":"key.converter",
                     "required":false,
                     "defaultValue":"org.apache.kafka.connect.converters.ByteArrayConverter",
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"value converter",
                     "internal":true,
                     "documentation":"value converter",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":9,
                     "key":"value.converter",
                     "required":false,
                     "defaultValue":"org.apache.kafka.connect.converters.ByteArrayConverter",
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"TOPIC",
                     "displayName":"Topics",
                     "internal":true,
                     "documentation":"the topic names in kafka form used by connector.This field is internal and is generated from topicKeys. Normally, it is composed by group and name",
                     "valueType":"ARRAY",
                     "tableKeys":[

                     ],
                     "orderInGroup":4,
                     "key":"topics",
                     "required":true,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"kind",
                     "internal":false,
                     "documentation":"kind of connector",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":13,
                     "key":"kind",
                     "required":false,
                     "defaultValue":"sink",
                     "group":"core",
                     "editable":false
                  },
                  {
                     "reference":"NONE",
                     "displayName":"the frequence of printing data",
                     "internal":false,
                     "documentation":"the frequence to print the row on log",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":-1,
                     "key":"console.sink.frequence",
                     "required":false,
                     "defaultValue":"3 seconds",
                     "group":"common",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"the divider charset to distinguish each row",
                     "internal":false,
                     "documentation":"the charset used to divide the rows.",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":-1,
                     "key":"console.sink.row.divider",
                     "required":false,
                     "defaultValue":"|",
                     "group":"common",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"Connector key",
                     "internal":false,
                     "documentation":"the key of this connector",
                     "valueType":"CONNECTOR_KEY",
                     "tableKeys":[

                     ],
                     "orderInGroup":0,
                     "key":"connectorKey",
                     "required":true,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"Schema",
                     "internal":false,
                     "documentation":"output schema",
                     "valueType":"TABLE",
                     "tableKeys":[
                        "order",
                        "dataType",
                        "name",
                        "newName"
                     ],
                     "orderInGroup":6,
                     "key":"columns",
                     "required":false,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"WORKER_CLUSTER",
                     "displayName":"worker cluster",
                     "internal":false,
                     "documentation":"the cluster name of running this connector.If there is only one worker cluster, you can skip this setting since configurator will pick up a worker cluster for you",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":7,
                     "key":"workerClusterName",
                     "required":false,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"TOPIC",
                     "displayName":"Topics",
                     "internal":false,
                     "documentation":"the topics used by connector",
                     "valueType":"TOPIC_KEYS",
                     "tableKeys":[

                     ],
                     "orderInGroup":3,
                     "key":"topicKeys",
                     "required":true,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"tags",
                     "internal":true,
                     "documentation":"tags to this connector",
                     "valueType":"TAGS",
                     "tableKeys":[

                     ],
                     "orderInGroup":14,
                     "key":"tags",
                     "required":false,
                     "defaultValue":null,
                     "group":"core",
                     "editable":true
                  },
                  {
                     "reference":"NONE",
                     "displayName":"version",
                     "internal":false,
                     "documentation":"version of connector",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":10,
                     "key":"version",
                     "required":false,
                     "defaultValue":"unknown",
                     "group":"core",
                     "editable":false
                  },
                  {
                     "reference":"NONE",
                     "displayName":"revision",
                     "internal":false,
                     "documentation":"revision of connector",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":11,
                     "key":"revision",
                     "required":false,
                     "defaultValue":"unknown",
                     "group":"core",
                     "editable":false
                  },
                  {
                     "reference":"NONE",
                     "displayName":"author",
                     "internal":false,
                     "documentation":"author of connector",
                     "valueType":"STRING",
                     "tableKeys":[

                     ],
                     "orderInGroup":12,
                     "key":"author",
                     "required":false,
                     "defaultValue":"unknown",
                     "group":"core",
                     "editable":false
                  }
               ]
            }
         ],
         "deadNodes":[
         ],
         "settings":{
            "statusTopicName":"d28ca7c875",
            "name":"wk",
            "offsetTopicPartitions":1,
            "brokerClusterName":"bk",
            "tags":{

            },
            "offsetTopicName":"f1f6ae812c",
            "imageName":"oharastream/connect-worker:0.8.0-SNAPSHOT",
            "groupId":"16f3408f84",
            "statusTopicReplications":1,
            "offsetTopicReplications":1,
            "configTopicReplications":1,
            "statusTopicPartitions":1,
            "configTopicName":"4279f8a236",
            "jmxPort":33983,
            "freePorts": [],
            "clientPort":34601,
            "jarKeys":[

            ],
            "nodeNames":[
               "node10"
            ]
         }
      }

start a worker cluster
----------------------

*PUT /v0/workers/$name/start*

Example Response
  ::

    202 Accepted

  .. note::
     You should use :ref:`Get worker cluster <rest-workers-get>` to fetch up-to-date status

stop a worker cluster
---------------------

Gracefully stopping a running worker cluster.

*PUT /v0/workers/$name/stop[?force=true]*

Query Parameters
  #. force (**boolean**) — true if you don’t want to wait the graceful shutdown
     (it can save your time but may damage your data).

Example Response
  ::

    202 Accepted

  .. note::
     You should use :ref:`Get worker cluster <rest-workers-get>` to fetch up-to-date status


add a new node to a running worker cluster
------------------------------------------

*PUT /v0/workers/$name/$nodeName*

If you want to extend a running worker cluster, you can add a node to
share the heavy loading of a running worker cluster. However, the
balance is not triggered at once. By the way, moving a task to another
idle node needs to **stop** task first. Don’t worry about the temporary
lower throughput when balancer is running.

remove a node from a running worker cluster
-------------------------------------------

*DELETE /v0/workers/$name/$nodeName*

If your budget is limited, you can decrease the number of nodes running
worker cluster. BUT, removing a node from a running worker cluster
invoke a lot of task move, and it will decrease the throughput of your
connector.

Example Response
  ::

     204 NoContent

  .. note::
     It is ok to delete an nonexistent worker node, and the response is
     204 NoContent.

