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

import * as URLS from '../../src/constants/urls';
import { makeRandomStr } from '../utils';

describe('StreamApp', () => {
  before(() => {
    cy.deleteAllWorkers();
    cy.createWorker();
  });

  beforeEach(() => {
    cy.server();
    cy.route('GET', 'api/pipelines/*').as('getPipeline');
    cy.route('PUT', 'api/pipelines/*').as('putPipeline');
    cy.route('GET', 'api/files?*').as('getJars');
  });

  it('starts a stream app', () => {
    const fromTopicName = `topic${makeRandomStr()}`;
    const toTopicName = `topic${makeRandomStr()}`;
    const pipelineName = `pipeline${makeRandomStr()}`;
    const streamAppName = `stream${makeRandomStr()}`;

    cy.createTopic(fromTopicName);
    cy.createTopic(toTopicName);

    cy.visit(URLS.PIPELINES)
      .uploadTestStreamAppJar(Cypress.env('WORKER_NAME'))
      .getByText('NEW PIPELINE')
      .click()
      .getByTestId('name-input')
      .type(pipelineName)
      .getByText('Please select...')
      .click()
      .get(`li[data-value=${Cypress.env('WORKER_NAME')}]`)
      .click()
      .getByText('Add')
      .click()
      .wait('@getPipeline');

    cy.getByTestId('toolbar-streams')
      .click()
      .wait('@getJars')
      .getByText('Add')
      .click()
      .getByPlaceholderText('StreamApp name')
      .type(streamAppName)
      .get('.ReactModal__Content')
      .eq(1)
      .within(() => {
        cy.getByText('Add').click();
      })
      .wait('@putPipeline');

    // TODO: these two topics can be added via API which should be faster than
    // adding from the UI
    cy.getByTestId('toolbar-topics')
      .click({ force: true })
      .getByTestId('topic-select')
      .select(fromTopicName)
      .getByText('Add')
      .click()
      .wait('@putPipeline');

    cy.getByTestId('toolbar-topics')
      .click({ force: true })
      .getByTestId('topic-select')
      .select(toTopicName)
      .getByText('Add')
      .click()
      .wait('@putPipeline');

    cy.getByText(streamAppName)
      .click({ force: true })
      .getByTestId('from')
      .click()
      .get(`li[data-value=${fromTopicName}]`)
      .click()
      .wait(2000) // UI has one sec throttle
      .wait('@putPipeline')
      .getByTestId('to')
      .click()
      .get(`li[data-value=${toTopicName}]`)
      .click()
      .wait(2000) // UI has one sec throttle
      .wait('@putPipeline')
      .getByTestId('start-button')
      .click()
      .getByText('Stream app successfully started!')
      .getByText(streamAppName)
      .getByTestId('stop-button')
      .click();
  });
});
