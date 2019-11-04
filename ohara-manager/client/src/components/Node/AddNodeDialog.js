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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { isEmpty } from 'lodash';
import { Form, Field } from 'react-final-form';

import * as validateApi from 'api/validateApi';
import * as nodeApi from 'api/nodeApi';
import { useSnackbar } from 'context/SnackbarContext';
import { InputField, Button } from 'components/common/Form';
import { Dialog } from 'components/common/Dialog';
import {
  required,
  minValue,
  maxLength,
  maxValue,
  composeValidators,
} from 'utils/validate';

const AddNodeDialog = props => {
  const { isOpen, handleClose, fetchNodes } = props;
  const [isValidConnection, setIsValidConnection] = useState(false);
  const showMessage = useSnackbar();

  const onSubmit = async (values, form) => {
    const { hostname, port, ...rest } = values;

    const params = {
      port: Number(port),
      hostname,
      ...rest,
    };

    const response = await nodeApi.create(params);
    const isSuccess = !isEmpty(response);
    if (isSuccess) {
      setTimeout(form.reset);
      showMessage(`Successfully created node ${hostname}`);
      await fetchNodes();
      handleClose();
    }
  };

  const testConnection = async values => {
    const { hostname, port, ...rest } = values;

    const params = {
      port: Number(port),
      hostname,
      ...rest,
    };

    const nodes = await validateApi.validateNode(params);
    const isPassed = nodes.every(node => node.pass);

    if (isPassed) {
      setIsValidConnection(isPassed);
      showMessage('Test passed!');
    }
  };
  return (
    <Form
      onSubmit={onSubmit}
      initialValues={{}}
      render={({
        handleSubmit,
        form,
        submitting,
        pristine,
        invalid,
        values,
      }) => {
        return (
          <Dialog
            open={isOpen}
            handleClose={() => {
              handleClose();
              form.reset();
            }}
            handleConfirm={handleSubmit}
            title="Add node"
            confirmText="ADD"
            confirmDisabled={
              submitting || pristine || invalid || !isValidConnection
            }
          >
            <form onSubmit={handleSubmit}>
              <Field
                name="hostname"
                label="Hostname"
                placeholder="node-01"
                margin="normal"
                helperText="hostname of the node"
                validate={composeValidators(required, maxLength(63))}
                component={InputField}
                fullWidth
                autoFocus
              />

              <Field
                name="port"
                label="Port"
                placeholder="22"
                type="number"
                margin="normal"
                helperText="SSH port of the node"
                component={InputField}
                validate={composeValidators(
                  required,
                  minValue(1),
                  maxValue(65535),
                )}
                inputProps={{
                  min: 1,
                  max: 65535,
                }}
              />

              <Field
                name="user"
                label="User"
                placeholder="admin"
                margin="normal"
                helperText="SSH username"
                validate={required}
                component={InputField}
                fullWidth
              />

              <Field
                name="password"
                label="Password"
                type="password"
                margin="normal"
                placeholder="password"
                helperText="SSH password"
                validate={required}
                component={InputField}
                fullWidth
              />
              <Button
                color="primary"
                disabled={invalid}
                onClick={() => testConnection(values)}
              >
                TEST CONNECTION
              </Button>
            </form>
          </Dialog>
        );
      }}
    />
  );
};

AddNodeDialog.propTypes = {
  isOpen: PropTypes.bool.isRequired,
  handleClose: PropTypes.func.isRequired,
  fetchNodes: PropTypes.func.isRequired,
};
export default AddNodeDialog;