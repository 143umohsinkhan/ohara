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

import React, { useRef } from 'react';
import PropTypes from 'prop-types';
import { Form } from 'react-final-form';
import RenderDefinition from './RenderDefinition';

const RenderDefinitions = props => {
  const {
    Definitions = [],
    initialValues = {},
    onSubmit,
    topics = [],
    files = [],
  } = props;
  const formRef = useRef(null);
  const formHandleSubmit = () =>
    formRef.current.dispatchEvent(new Event('submit'));
  const refs = {};

  const displayDefinitions = Definitions.filter(def => !def.internal);

  const RenderForm = (
    <Form
      onSubmit={onSubmit}
      initialValues={initialValues}
      render={({ handleSubmit, form }) => {
        return (
          <form onSubmit={handleSubmit} ref={formRef}>
            {displayDefinitions
              .filter(def => def.key !== 'group')
              .map(def => {
                const { definitionField, ref } = RenderDefinition({
                  def,
                  topics,
                  files,
                });

                refs[def.key] = ref;

                return definitionField;
              })}
          </form>
        );
      }}
    />
  );

  return { RenderForm, formHandleSubmit, refs };
};

RenderDefinitions.propTypes = {
  Definitions: PropTypes.array,
  topics: PropTypes.array,
  onSubmit: PropTypes.func,
  id: PropTypes.string,
};

export default RenderDefinitions;