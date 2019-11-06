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

import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useHistory, useParams } from 'react-router-dom';

import NodeDialog from 'components/Node/NodeDialog';
import IntroDialog from './IntroDialog';
import Toolbar from './Toolbar';
import Toolbox from './Toolbox/Toolbox';
import Graph from './Graph';
import { useWorkspace } from 'context/WorkspaceContext';
import { usePipeline } from 'context/PipelineContext';
import { useNewWorkspace } from 'context/NewWorkspaceContext';

const GraphWrapper = styled.div`
  position: relative;

  /* Subtract the height of Toolbar  */
  height: calc(100vh - 72px);
`;

const Pipeline = () => {
  const history = useHistory();
  const { workspaces, isFetching: isFetchingWorkspace } = useWorkspace();
  const { pipelines, doFetch: fetchPipelines } = usePipeline();
  const { workspaceName, pipelineName } = useParams();
  const { setIsOpen: setIsNewWorksapceDialogOpen } = useNewWorkspace();
  const [isToolboxOpen, setIsToolboxOpen] = useState(true);

  const initialState = {
    topic: false,
    source: false,
    sink: false,
    streamApp: false,
  };
  const [toolboxExpanded, setToolboxExpanded] = useState(initialState);

  const handleToolboxClick = panel => {
    setToolboxExpanded(prevState => {
      return {
        ...prevState,
        [panel]: !prevState[panel],
      };
    });
  };

  useEffect(() => {
    fetchPipelines(workspaceName);
  }, [fetchPipelines, workspaceName]);

  const hasPipeline = pipelines.length > 0;
  let currentPipeline;

  if (pipelineName) {
    const current = pipelines.find(pipeline => pipeline.name === pipelineName);

    // If the `current` pipeline is found in the pipeline list
    if (current) currentPipeline = current;

    // If the `current` pipeline is not found in the list but the
    // list is not empty, let's display the first pipeline
    if (current === undefined && hasPipeline) {
      history.push(`/${workspaceName}/${pipelines[0].name}`);
    }
  } else if (hasPipeline) {
    history.push(`/${workspaceName}/${pipelines[0].name}`);
  }

  const hasWorkspace = workspaces.length > 0;

  let currentWorkspace;
  if (workspaceName) {
    const current = workspaces.find(
      workspace => workspace.settings.name === workspaceName,
    );

    // If the `current` workspace is found in the workspace list
    if (current) currentWorkspace = current;

    // If the `current` workspace is not found in the list but the
    // list is not empty, let's display the first workspace
    if (current === undefined && hasWorkspace) {
      history.push(`/${workspaces[0].settings.name}`);
    }
  } else if (hasWorkspace) {
    // Display the first workspace as the fallback
    history.push(`/${workspaces[0].settings.name}`);
  }

  useEffect(() => {
    if (hasWorkspace) {
      setIsNewWorksapceDialogOpen(false);
    } else {
      setIsNewWorksapceDialogOpen(true);
    }
  }, [hasWorkspace, setIsNewWorksapceDialogOpen]);

  return (
    <>
      {currentWorkspace && (
        <>
          {currentPipeline && (
            <>
              <Toolbar
                isToolboxOpen={isToolboxOpen}
                handleToolboxOpen={() => setIsToolboxOpen(true)}
                handleToolboxClick={handleToolboxClick}
              />
              <GraphWrapper>
                <Graph />
                <Toolbox
                  isOpen={isToolboxOpen}
                  expanded={toolboxExpanded}
                  handleClick={handleToolboxClick}
                  handleClose={() => {
                    setIsToolboxOpen(false);
                    setToolboxExpanded(initialState);
                  }}
                />
              </GraphWrapper>
            </>
          )}
        </>
      )}

      {!isFetchingWorkspace && <IntroDialog />}
      <NodeDialog />
    </>
  );
};

export default Pipeline;
