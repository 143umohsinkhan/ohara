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

import * as generate from '../../src/utils/generate';

/* eslint-disable no-unused-expressions */
// eslint is complaining about `expect(thing).to.be.undefined`

const setup = () => {
  const nodeName = generate.serviceName({ prefix: 'node' });
  const zookeeperClusterName = generate.serviceName({ prefix: 'zk' });
  const brokerClusterName = generate.serviceName({ prefix: 'bk' });
  const workerClusterName = generate.serviceName({ prefix: 'wk' });

  cy.createNode({
    name: nodeName,
    port: generate.port(),
    user: generate.userName(),
    password: generate.password(),
  });

  cy.createZookeeper({
    nodeNames: [nodeName],
    name: zookeeperClusterName,
  });

  cy.startZookeeper(zookeeperClusterName);

  cy.createBroker({
    name: brokerClusterName,
    nodeNames: [nodeName],
    zookeeperClusterKey: {
      group: 'default',
      name: zookeeperClusterName,
    },
  });

  cy.startBroker(brokerClusterName);

  cy.createWorker({
    name: workerClusterName,
    nodeNames: [nodeName],
    jarKeys: [],
    brokerClusterKey: {
      group: 'default',
      name: brokerClusterName,
    },
    tags: {
      broker: {
        imageName: generate.name(),
        name: brokerClusterName,
      },
      zookeeper: {
        imageName: generate.name(),
        name: zookeeperClusterName,
      },
    },
  }).as('createWorker');

  cy.startWorker(workerClusterName);

  return {
    nodeName,
    zookeeperClusterName,
    brokerClusterName,
    workerClusterName,
  };
};

describe('Worker API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('createWorker', () => {
    const { workerClusterName } = setup();

    cy.get('@createWorker').then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      const {
        name,
        clientPort,
        nodeNames,
        jarKeys,
        configTopicName,
        offsetTopicName,
        statusTopicName,
        imageName,
        tags,
      } = result.settings;

      expect(isSuccess).to.eq(true);

      expect(name).to.eq(workerClusterName);
      expect(clientPort).to.be.a('number');
      expect(nodeNames)
        .to.be.an('array')
        .that.have.lengthOf(1);
      expect(jarKeys).to.be.an('array');
      expect(configTopicName).to.be.a('string');
      expect(offsetTopicName).to.be.a('string');
      expect(statusTopicName).to.be.a('string');
      expect(imageName).to.be.a('string');
      expect(result.connectors).to.be.an('array');
      expect(tags.zookeeper).to.be.an('object');
      expect(tags.broker).to.be.an('object');
    });
  });

  it('fetchWorker', () => {
    const { workerClusterName } = setup();

    cy.fetchWorker(workerClusterName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      const {
        name,
        clientPort,
        nodeNames,
        jarKeys,
        configTopicName,
        offsetTopicName,
        statusTopicName,
        imageName,
        tags,
      } = result.settings;

      expect(isSuccess).to.eq(true);

      expect(name).to.eq(workerClusterName);
      expect(clientPort).to.be.a('number');
      expect(nodeNames)
        .to.be.an('array')
        .that.have.lengthOf(1);
      expect(jarKeys).to.be.an('array');
      expect(configTopicName).to.be.a('string');
      expect(offsetTopicName).to.be.a('string');
      expect(statusTopicName).to.be.a('string');
      expect(imageName).to.be.a('string');
      expect(result.connectors).to.be.an('array');
      expect(tags.zookeeper).to.be.an('object');
      expect(tags.broker).to.be.an('object');
    });
  });

  it('fetchWorkers', () => {
    const { nodeName, brokerClusterName } = setup();

    const paramsOne = {
      name: generate.serviceName({ prefix: 'worker' }),
      brokerClusterKey: {
        group: 'default',
        name: brokerClusterName,
      },
      nodeNames: [nodeName],
    };

    const paramsTwo = {
      name: generate.serviceName({ prefix: 'worker' }),
      brokerClusterKey: {
        group: 'default',
        name: brokerClusterName,
      },
      nodeNames: [nodeName],
    };

    cy.createWorker(paramsOne);
    cy.createWorker(paramsTwo);

    cy.fetchWorkers().then(res => {
      const {
        data: { isSuccess, result },
      } = res;

      expect(isSuccess).to.eq(true);

      const workers = result.filter(
        worker =>
          worker.settings.name === paramsOne.name ||
          worker.settings.name === paramsTwo.name,
      );

      expect(workers.length).to.eq(2);
    });
  });

  it('startWorker', () => {
    const { workerClusterName } = setup();

    cy.startWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });
  });

  it('stopWorker', () => {
    const { workerClusterName } = setup();

    cy.startWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });

    cy.stopWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq.undefined;
    });
  });

  it('forceStopWorker', () => {
    const { workerClusterName } = setup();

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.state).to.be.undefined;
    });

    cy.startWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });

    // This API is not actually used in the UI, but used in our E2E start scripts
    cy.request('PUT', `api/workers/${workerClusterName}/stop?force=true`).then(
      response => {
        expect(response.status).to.eq(202);
      },
    );

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq.undefined;
    });
  });

  it('deleteWorker', () => {
    const { workerClusterName } = setup();

    cy.get('@createWorker').then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.stopWorker(workerClusterName);

    cy.deleteWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorkers().then(response => {
      const targetWorker = response.data.result.find(
        worker => worker.settings.name === workerClusterName,
      );

      expect(targetWorker).to.be.undefined;
    });
  });
});