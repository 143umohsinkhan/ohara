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

import React from 'react';
import LogProgress from 'components/common/Progress/LogProgress';

import * as hooks from 'hooks';

const DeleteWorkspace = () => {
  const pause = hooks.usePauseDeleteWorkspaceAction();
  const resume = hooks.useResumeDeleteWorkspaceAction();
  const rollback = hooks.useRollbackDeleteWorkspaceAction();
  const deleteWorkspace = hooks.useDeleteWorkspaceAction();

  const currentDeleteWorkspace = hooks.useDeleteWorkspace();
  const { isOpen } = currentDeleteWorkspace;
  const {
    steps,
    activeStep,
    message,
    isPause,
    log,
  } = currentDeleteWorkspace.progress;

  return (
    <LogProgress
      activeStep={activeStep}
      createTitle={'Delete Workspace'}
      data={log}
      isOpen={isOpen}
      isPause={isPause}
      message={message}
      onPause={pause}
      onResume={() => {
        resume();
        deleteWorkspace();
      }}
      onRollback={() => {
        rollback();
        deleteWorkspace();
      }}
      steps={steps}
    />
  );
};

export default DeleteWorkspace;
