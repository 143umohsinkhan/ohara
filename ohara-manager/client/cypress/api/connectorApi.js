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

const setup = () => {
  const nodeName = generate.serviceName({ prefix: 'node' });
  const zookeeperClusterName = generate.serviceName({ prefix: 'zookeeper' });
  const brokerClusterName = generate.serviceName({ prefix: 'broker' });
  const workerClusterName = generate.serviceName({ prefix: 'worker' });
  const connectorName = generate.serviceName({ prefix: 'connector' });
  const topicName = generate.serviceName({ prefix: 'topic' });

  cy.createNode({
    name: nodeName,
    port: 22,
    user: generate.userName(),
    password: generate.password(),
  });

  cy.createZookeeper({
    name: zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startZookeeper(zookeeperClusterName);

  cy.createBroker({
    name: brokerClusterName,
    zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startBroker(brokerClusterName);

  cy.createWorker({
    name: workerClusterName,
    brokerClusterName,
    nodeNames: [nodeName],
  });

  cy.startWorker(workerClusterName);

  cy.createTopic({
    name: topicName,
    brokerClusterName,
  });

  cy.startTopic(topicName);

  cy.createConnector({
    'connector.class': 'com.island.ohara.connector.ftp.FtpSource',
    'connector.name': connectorName,
    name: connectorName,
    topicKeys: [{ group: 'default', name: topicName }],
    workerClusterName,
    tags: { name: connectorName },
  }).as('createConnector');

  return {
    nodeName,
    zookeeperClusterName,
    brokerClusterName,
    workerClusterName,
    connectorName,
    topicName,
  };
};

describe('Connector API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('createConnector', () => {
    setup();

    cy.get('@createConnector').then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.a('object');
      expect(settings.tags.name).to.eq(settings.name);

      expect(settings.topicKeys).to.have.deep.property('[0].group', 'default');
    });
  });

  it('fetchConnector', () => {
    const { connectorName, topicName } = setup();

    cy.fetchConnector(connectorName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(result).to.include.keys('settings');
      expect(settings).to.be.an('object');
      expect(settings.tags.name).to.eq(settings.name);

      expect(settings.topicKeys)
        .to.be.an('array')
        .to.have.lengthOf(1)
        .to.have.deep.property('[0].name', topicName);

      expect(settings.topicKeys).to.have.deep.property('[0].group', 'default');
    });
  });

  it('updateConnector', () => {
    const { workerClusterName, connectorName, topicName } = setup();

    const params = {
      name: connectorName,
      params: {
        name: connectorName,
        author: 'root',
        columns: [
          { dataType: 'STRING', name: 'test', newName: 'test', order: 1 },
        ],
        'connector.class': 'com.island.ohara.connector.ftp.FtpSource',
        'connector.name': 'Untitled source',
        'ftp.completed.folder': 'test',
        'ftp.encode': 'UTF-8',
        'ftp.error.folder': 'test',
        'ftp.hostname': 'test',
        'ftp.input.folder': 'test',
        'ftp.port': 20,
        'ftp.user.name': 'test',
        'ftp.user.password': 'test',
        kind: 'source',
        revision: '1e7da9544e6aa7ad2f9f2792ed8daf5380783727',
        'tasks.max': 1,
        topicKeys: [{ group: 'default', name: topicName }],
        version: '0.8.0-SNAPSHOT',
        workerClusterName,
      },
    };

    cy.updateConnector(params).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.a('object');
      expect(settings.tags.name).to.eq(settings.name);

      expect(settings.topicKeys)
        .to.be.an('array')
        .to.have.lengthOf(1)
        .to.have.deep.property('[0].name', topicName);

      expect(settings.topicKeys).to.have.deep.property('[0].group', 'default');
    });
  });

  it('startConnector', () => {
    const { connectorName } = setup();

    cy.startConnector(connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });

  it('stopConnector', () => {
    const { connectorName } = setup();

    cy.stopConnector(connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });

  it('deleteConnector', () => {
    const { connectorName } = setup();

    cy.deleteConnector(connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });
});
