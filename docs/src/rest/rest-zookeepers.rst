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

.. _rest-zookeepers:

Zookeeper
=========

`Zookeeper <https://zookeeper.apache.org>`__ service is the base of all
other services. It is also the fist service you should set up. At the
beginning, you can deploy zookeeper cluster in single node. However, it
may be unstable since single node can’t guarantee the data durability
when node crash. In production you should set up zookeeper cluster on 3
nodes at least.

Zookeeper service has many configs which make you spend a lot of time to
read and set. Ohara provides default values to all configs but open a
room to enable you to overwrite somethings you do care.

The properties which can be set by user are shown below.

#. name (**string**) — cluster name. The legal character is number, lowercase alphanumeric characters, or ‘.’
#. group (**string**) — cluster group. The legal character is number, lowercase alphanumeric characters, or ‘.’
#. imageName (**string**) — docker image
#. jmxPort (**int**) — zookeeper jmx port
#. clientPort (**int**) — zookeeper client port
#. electionPort (**int**) — used to select the zk node leader
#. peerPort (**int**) — port used by internal communication
#. nodeNames (**array(string)**) — the nodes running the zookeeper process
#. tags (**object**) — the user defined parameters


The following information are updated by Ohara.

#. aliveNodes (**array(string)**) — the nodes that host the running containers of zookeeper
#. state (**option(string)**) — only started/failed zookeeper has state (RUNNING or DEAD)
#. error (**option(string)**) — the error message from a failed zookeeper. If zookeeper is fine or un-started, you won’t get this field.
#. lastModified (**long**) — last modified this jar time


.. _rest-zookeepers-create-properties:

create a zookeeper properties
-----------------------------

*POST /v0/zookeepers*

Example Request
  .. code-block:: json

    {
      "name": "zk",
      "nodeNames": [
        "node00"
      ]
    }

Example Response
  .. code-block:: json

    {
      "syncLimit": 5,
      "name": "zk00",
      "xms": 1024,
      "routes": {},
      "lastModified": 1578642569693,
      "dataDir": "/home/ohara/default/data",
      "tags": {},
      "electionPort": 44371,
      "xmx": 1024,
      "imageName": "oharastream/zookeeper:$|version|",
      "aliveNodes": [],
      "initLimit": 10,
      "jmxPort": 33915,
      "clientPort": 42006,
      "peerPort": 46559,
      "tickTime": 2000,
      "group": "default",
      "nodeNames": [
        "node00"
      ]
    }

  .. note::
    All ports have default value so you can ignore them when creating zookeeper cluster. However, the port conflict detect does not allow
    you to reuse port on different purpose (a dangerous behavior, right?).

list all zookeeper clusters
---------------------------

*GET /v0/zookeepers*

the accepted query keys are listed below.

#. group
#. name
#. lastModified
#. tags
#. tag - this field is similar to tags but it addresses the "contain" behavior.
#. state
#. aliveNodes
#. key

Example Response
  .. code-block:: json

    [
      {
        "syncLimit": 5,
        "name": "zk00",
        "xms": 1024,
        "routes": {},
        "lastModified": 1578642569693,
        "dataDir": "/home/ohara/default/data",
        "tags": {},
        "electionPort": 44371,
        "xmx": 1024,
        "imageName": "oharastream/zookeeper:$|version|",
        "aliveNodes": [],
        "initLimit": 10,
        "jmxPort": 33915,
        "clientPort": 42006,
        "peerPort": 46559,
        "tickTime": 2000,
        "group": "default",
        "nodeNames": [
          "node00"
        ]
      }
    ]

update zookeeper cluster properties
-----------------------------------

*PUT /v0/zookeepers/$name?group=$group*

.. note::
   If the required zookeeper (group, name) was not exists, we will try to use this request as POST

Example Request
  .. code-block:: json

    {
      "jmxPort": 12345
    }

Example Response
  .. code-block:: json

    {
      "syncLimit": 5,
      "name": "zk00",
      "xms": 1024,
      "routes": {},
      "lastModified": 1578642751122,
      "dataDir": "/home/ohara/default/data",
      "tags": {},
      "electionPort": 44371,
      "xmx": 1024,
      "imageName": "oharastream/zookeeper:$|version|",
      "aliveNodes": [],
      "initLimit": 10,
      "jmxPort": 12345,
      "clientPort": 42006,
      "peerPort": 46559,
      "tickTime": 2000,
      "group": "default",
      "nodeNames": [
        "node00"
      ]
    }


delete a zookeeper properties
-----------------------------

*DELETE /v0/zookeepers/$name?group=$group*

You cannot delete properties of an non-stopped zookeeper cluster.
We will use the default value as the query parameter "?group=" if you don't specify it.

Example Response
  ::

     204 NoContent

  .. note::
     It is ok to delete an nonexistent zookeeper cluster, and the response is 204 NoContent.


.. _rest-zookeepers-get:

get a zookeeper cluster
-----------------------

*GET /v0/zookeepers/$name?group=$group*

Get zookeeper information by name and group. This API could fetch all information
of a zookeeper (include state).
We will use the default value as the query parameter "?group=" if you don't specify it.

Example Response
  .. code-block:: json

    {
      "syncLimit": 5,
      "name": "zk00",
      "xms": 1024,
      "routes": {},
      "lastModified": 1578642569693,
      "dataDir": "/home/ohara/default/data",
      "tags": {},
      "electionPort": 44371,
      "xmx": 1024,
      "imageName": "oharastream/zookeeper:$|version|",
      "aliveNodes": [],
      "initLimit": 10,
      "jmxPort": 33915,
      "clientPort": 42006,
      "peerPort": 46559,
      "tickTime": 2000,
      "group": "default",
      "nodeNames": [
        "node00"
      ]
    }


start a zookeeper cluster
-------------------------

*PUT /v0/zookeepers/$name/start?group=$group*

We will use the default value as the query parameter "?group=" if you don't specify it.

Example Response
  ::

    202 Accepted

  .. note::
    You should use :ref:`Get zookeeper cluster <rest-zookeepers-get>` to fetch up-to-date status


stop a zookeeper cluster
------------------------

Gracefully stopping a running zookeeper cluster. It is disallowed to
stop a zookeeper cluster used by a running :ref:`broker cluster <rest-brokers>`.

*PUT /v0/zookeepers/$name/stop?group=$group[&force=true]*

We will use the default value as the query parameter "?group=" if you don't specify it.

Query Parameters
  #. force (**boolean**) — true if you don’t want to wait the graceful shutdown
     (it can save your time but may damage your data).

Example Response
  ::

    202 Accepted

  .. note::
    You should use :ref:`Get zookeeper cluster <rest-zookeepers-get>` to fetch up-to-date status


delete a node from a running zookeeper cluster
----------------------------------------------

Unfortunately, it is a litter dangerous to remove a node from a running
zookeeper cluster so we don’t support it yet.


add a node to a running zookeeper cluster
-----------------------------------------

Unfortunately, it is a litter hard to add a node to a running zookeeper
cluster so we don’t support it yet.

