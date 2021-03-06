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

import * as actions from 'store/actions';

const initialState = {
  isOpen: false,
  opened: false,
};

export default function reducer(state = initialState, action) {
  switch (action.type) {
    case actions.openIntro.TRIGGER:
      return {
        ...state,
        isOpen: true,
        opened: true,
      };
    case actions.closeIntro.TRIGGER:
      return {
        ...state,
        isOpen: false,
      };
    default:
      return state;
  }
}
