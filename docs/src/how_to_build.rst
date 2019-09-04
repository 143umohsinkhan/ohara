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

.. _build:

How to build
============

.. _build-prerequisites:

Prerequisites
-------------

-  OpenJDK 1.8
-  Scala 2.12.9
-  Gradle 5.1+
-  Node.js 8.12.0
-  Yarn 1.13.0 or greater
-  Docker 18.09 or greater (Official QA is on docker 18.09. Also, docker
   multi-stage, which is supported by Docker 17.05 or higher, is
   required in building ohara images. see `Use multi-stage builds`_ for more details)
-  Kubernetes 1.14.1 (Official QA uses the Kubernetes version is 1.14.1)

--------------

.. _build-gradle-commands:

Gradle Commands
---------------

Ohara build is based on `gradle`_. Ohara has defined many gradle tasks
to simplify the development of ohara.

.. _build-binary:

  Build Binary

    .. code-block:: console

      $ gradle clean build -x test

    .. note::
      the tar file is located at ohara-assembly/build/distributions

  Run All UTs

    .. code-block:: console

      $ gradle clean test

    .. note::

       Ohara IT tests requires specific envs, and all IT tests will be
       skipped if you don't pass the related setting to IT. Ohara recommends
       you testing your code on `official QA`_ which offers the powerful
       machine and IT envs.

    .. tip::

      add flag "-PskipManager" to skip the tests of Ohara Manager. Ohara Manager is a module
      in charge of Ohara UI. Feel free to skip it if you are totally a backend developer. By
      the way, the prerequisites of testing Ohara Manager is shown in :ref:`here <managerdev>`

    .. tip::

      add flag "-PmaxParallelForks=6" to increase the number of test process to start in parallel.
      The default value is number of cores / 2, and noted that too many tests running in parallel may
      easily produce tests timeout.

  Code Style Auto-Apply

    Use this task to make sure your added code will have the same format and conventions with the rest of codebase.

    .. code-block:: console

      $ gradle spotlessApply

    .. note::

       we have this style check in early QA build.


  License Auto-Apply

    If you have added any new files in a PR. This task will automatically
    insert an Apache 2.0 license header in each one of these newly created
    files

    .. code-block:: console

      $ gradle licenseApply

    .. note::

       Note that a file without the license header will fail at early QA
       build


  Build Uber Jar

    .. code-block:: console

      $ gradle clean uberJar -PskipManager

    .. note::

       the uber jar is under ohara-assembly/build/libs/


  Publish Artifacts to JFrog Bintray

    .. code-block:: console

      $ gradle clean build -PskipManager -x test bintrayUpload -PbintrayUser=$user -PbintrayKey=$key -PdryRun=false -Poverride=true

    .. tip::

      - bintrayUser: the account that has write permission to the repository
      - bintrayKey: the account API Key
      - dryRun: whether to publish artifacts (default is true)
      - override: whether to override version artifacts already published

    .. note::

       Only release manager has permission to upload artifacts


Installation
------------

see :ref:`User Guide <userguide>`

.. _Use multi-stage builds: https://docs.docker.com/develop/develop-images/multistage-build/
.. _gradle: https://gradle.org/
.. _official QA: https://builds.is-land.com.tw/job/PreCommit-OHARA/
