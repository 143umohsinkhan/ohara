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

import { of } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';

import { LOG_LEVEL } from 'const';
import * as zookeeperApi from 'api/zookeeperApi';
import stopZookeeperEpic from '../../zookeeper/stopZookeeperEpic';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { SERVICE_STATE } from 'api/apiInterface/clusterInterface';
import { entity as zookeeperEntity } from 'api/__mocks__/zookeeperApi';

jest.mock('api/zookeeperApi');

const zkId = getId(zookeeperEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

beforeEach(() => {
  // ensure the mock data is as expected before each test
  jest.restoreAllMocks();
});

it('stop zookeeper should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a        ';
    const expected = '--a 499ms v';
    const subs = '    ^----------';

    const action$ = hot(input, {
      a: {
        type: actions.stopZookeeper.TRIGGER,
        payload: zookeeperEntity,
      },
    });
    const output$ = stopZookeeperEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopZookeeper.REQUEST,
        payload: {
          zookeeperId: zkId,
        },
      },
      v: {
        type: actions.stopZookeeper.SUCCESS,
        payload: {
          zookeeperId: zkId,
          entities: {
            zookeepers: {
              [zkId]: zookeeperEntity,
            },
          },
          result: zkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('stop zookeeper failed after reach retry limit', () => {
  // mock a 20 times "failed stopped" result
  const spyGet = jest.spyOn(zookeeperApi, 'get');
  for (let i = 0; i < 20; i++) {
    spyGet.mockReturnValueOnce(
      of({
        status: 200,
        title: 'retry mock get data',
        data: { ...zookeeperEntity, state: SERVICE_STATE.RUNNING },
      }),
    );
  }
  // get result finally
  spyGet.mockReturnValueOnce(
    of({
      status: 200,
      title: 'retry mock get data',
      data: { ...zookeeperEntity },
    }),
  );

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a             ';
    // we failed after retry 11 times (11 * 2000ms = 22s)
    const expected = '--a 21999ms (vu)';
    const subs = '    ^---------------';

    const action$ = hot(input, {
      a: {
        type: actions.stopZookeeper.TRIGGER,
        payload: zookeeperEntity,
      },
    });
    const output$ = stopZookeeperEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopZookeeper.REQUEST,
        payload: {
          zookeeperId: zkId,
        },
      },
      v: {
        type: actions.stopZookeeper.FAILURE,
        payload: {
          zookeeperId: zkId,
          data: { ...zookeeperEntity, state: SERVICE_STATE.RUNNING },
          meta: undefined,
          title: `Try to stop zookeeper: "${zookeeperEntity.name}" failed after retry 11 times. Expected state is nonexistent, Actual state: RUNNING`,
        },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          zookeeperId: zkId,
          data: { ...zookeeperEntity, state: SERVICE_STATE.RUNNING },
          meta: undefined,
          title: `Try to stop zookeeper: "${zookeeperEntity.name}" failed after retry 11 times. Expected state is nonexistent, Actual state: RUNNING`,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('stop zookeeper multiple times should be executed once', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a---a 1s a 10s ';
    const expected = '--a       499ms v';
    const subs = '    ^----------------';

    const action$ = hot(input, {
      a: {
        type: actions.stopZookeeper.TRIGGER,
        payload: zookeeperEntity,
      },
    });
    const output$ = stopZookeeperEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopZookeeper.REQUEST,
        payload: {
          zookeeperId: zkId,
        },
      },
      v: {
        type: actions.stopZookeeper.SUCCESS,
        payload: {
          zookeeperId: zkId,
          entities: {
            zookeepers: {
              [zkId]: zookeeperEntity,
            },
          },
          result: zkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('stop different zookeeper should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const anotherZookeeperEntity = {
      ...zookeeperEntity,
      name: 'anotherzk',
      group: 'default',
      xms: 1111,
      xmx: 2222,
      clientPort: 3333,
    };
    const input = '   ^-a--b           ';
    const expected = '--a--b 496ms y--z';
    const subs = '    ^----------------';

    const action$ = hot(input, {
      a: {
        type: actions.stopZookeeper.TRIGGER,
        payload: zookeeperEntity,
      },
      b: {
        type: actions.stopZookeeper.TRIGGER,
        payload: anotherZookeeperEntity,
      },
    });
    const output$ = stopZookeeperEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopZookeeper.REQUEST,
        payload: {
          zookeeperId: zkId,
        },
      },
      b: {
        type: actions.stopZookeeper.REQUEST,
        payload: {
          zookeeperId: getId(anotherZookeeperEntity),
        },
      },
      y: {
        type: actions.stopZookeeper.SUCCESS,
        payload: {
          zookeeperId: zkId,
          entities: {
            zookeepers: {
              [zkId]: zookeeperEntity,
            },
          },
          result: zkId,
        },
      },
      z: {
        type: actions.stopZookeeper.SUCCESS,
        payload: {
          zookeeperId: getId(anotherZookeeperEntity),
          entities: {
            zookeepers: {
              [getId(anotherZookeeperEntity)]: anotherZookeeperEntity,
            },
          },
          result: getId(anotherZookeeperEntity),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});
