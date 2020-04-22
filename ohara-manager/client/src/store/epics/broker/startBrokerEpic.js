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

import { normalize } from 'normalizr';
import { merge } from 'lodash';
import { ofType } from 'redux-observable';
import { defer, of, throwError, iif, zip } from 'rxjs';
import {
  catchError,
  delay,
  map,
  retryWhen,
  startWith,
  mergeMap,
  distinctUntilChanged,
  concatMap,
} from 'rxjs/operators';

import { SERVICE_STATE } from 'api/apiInterface/clusterInterface';
import * as brokerApi from 'api/brokerApi';
import * as actions from 'store/actions';
import * as schema from 'store/schema';
import { getId } from 'utils/object';

export const startBroker$ = params => {
  const brokerId = getId(params);
  return zip(
    defer(() => brokerApi.start(params)),
    defer(() => brokerApi.get(params)).pipe(
      map(res => {
        if (!res.data?.state || res.data.state !== SERVICE_STATE.RUNNING)
          throw res;
        else return res.data;
      }),
      retryWhen(errors =>
        errors.pipe(
          concatMap((value, index) =>
            iif(
              () => index > 10,
              throwError('exceed max retry times'),
              of(value).pipe(delay(2000)),
            ),
          ),
        ),
      ),
    ),
  ).pipe(
    map(([, data]) => normalize(data, schema.broker)),
    map(normalizedData => merge(normalizedData, { brokerId })),
    map(normalizedData => actions.startBroker.success(normalizedData)),
    startWith(actions.startBroker.request({ brokerId })),
    catchError(error =>
      of(actions.startBroker.failure(merge(error, { brokerId }))),
    ),
  );
};

export default action$ =>
  action$.pipe(
    ofType(actions.startBroker.TRIGGER),
    map(action => action.payload),
    distinctUntilChanged(),
    mergeMap(params => startBroker$(params)),
  );