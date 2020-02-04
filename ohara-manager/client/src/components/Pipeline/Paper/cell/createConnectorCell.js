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
import PlayArrowIcon from '@material-ui/icons/PlayArrow';
import StopIcon from '@material-ui/icons/Stop';
import BuildIcon from '@material-ui/icons/Build';
import CancelIcon from '@material-ui/icons/Cancel';
import TrendingUpIcon from '@material-ui/icons/TrendingUp';
import FlightTakeoffIcon from '@material-ui/icons/FlightTakeoff';
import FlightLandIcon from '@material-ui/icons/FlightLand';
import WavesIcon from '@material-ui/icons/Waves';
import { renderToString } from 'react-dom/server';
import $ from 'jquery';
import _ from 'lodash';
import * as joint from 'jointjs';

import { KIND, CELL_STATUS } from 'const';

const NORMAL_HEIGHT = 100;
const HEIGHT_WITH_METRICS = 160;

const createConnectorCell = options => {
  const {
    id,
    displayName,
    isTemporary = false,
    isMetricsOn: areMetricsDisplayed = false,
    name,
    kind,
    className,
    position,
    status = CELL_STATUS.stopped,
    paperApi,
    onCellStart,
    onCellStop,
    onCellConfig,
    onCellRemove,
    metrics = {
      meters: [],
    },
    jarKey,
  } = options;

  joint.shapes.html = {};
  joint.shapes.html.Element = joint.shapes.basic.Rect.extend({
    defaults: joint.util.deepSupplement(
      {
        type: 'html.Element',
        attrs: {
          rect: { stroke: 'none', 'fill-opacity': 0 },
        },
      },
      joint.shapes.basic.Rect.prototype.defaults,
    ),
  });

  const linkIcon = renderToString(<TrendingUpIcon />);
  const startIcon = renderToString(<PlayArrowIcon />);
  const stopIcon = renderToString(<StopIcon />);
  const configIcon = renderToString(<BuildIcon viewBox="-4 -5 32 32" />);
  const removeIcon = renderToString(<CancelIcon viewBox="-4 -5 32 32" />);

  const displayClassName = className.split('.').pop();
  const iconState = getIconState(status);

  // Things that don't need to update can be placed here, otherwise, put them
  // in the `updateBox()` method
  joint.shapes.html.ElementView = joint.dia.ElementView.extend({
    template: `
      <div class="connector">
        <div class="header">
          <div class="icon ${iconState}">${getIcon(kind)}</div>
          <div class="display-name-wrapper">
            <div class="display-name">${displayName}</div>
            <div class="type">${displayClassName}</div>
          </div>
        </div>
        <div class="metrics"></div>
        <div class="status">
          <span class="status-name">Status</span>
          <span class="status-value">${status}</span>
        </div>

        <div class="connector-menu">
          ${
            // Sink cannot create connection form itself to others
            kind !== KIND.sink
              ? `<Button class="connector-link">${linkIcon}</Button>`
              : ''
          }
          <Button class="connector-start">${startIcon}</Button>
          <Button class="connector-stop">${stopIcon}</Button>
          <Button class="connector-config">${configIcon}</Button>
          <Button class="connector-remove">${removeIcon}</Button>
        </div>
    </div>`,

    init() {
      this.listenTo(this.model, 'change', this.updateBox);
    },
    onRender() {
      const boxMarkup = joint.util.template(this.template)();
      const $box = (this.$box = $(boxMarkup));
      this.listenTo(this.paper, 'scale translate', this.updateBox);
      $box.appendTo(this.paper.el);

      const $linkButton = $box.find('.connector-link');
      const $startButton = $box.find('.connector-start');
      const $stopButton = $box.find('.connector-stop');
      const $configButton = $box.find('.connector-config');
      const $removeButton = $box.find('.connector-remove');
      const { id } = this.model;
      const cellData = paperApi.getCell(id);

      // Menu actions
      $linkButton.on('click', () => paperApi.addLink(id));
      $startButton.on('click', () => onCellStart(cellData, paperApi));
      $stopButton.on('click', () => onCellStop(cellData, paperApi));
      $configButton.on('click', () => onCellConfig(cellData, paperApi));
      $removeButton.on('click', () => onCellRemove(cellData, paperApi));

      this.updateBox();
      return this;
    },
    updateBox(
      cell,
      options,
      customOptions = {
        metrics: {
          meters: [],
        },
      },
    ) {
      const {
        metrics: { meters },
      } = customOptions;

      const displayMetrics = meters.length > 0 ? customOptions.meters : metrics;

      // Set the position and dimension of the box so that it covers the JointJS element.
      const bBox = this.getBBox({ useModelGeometry: true });
      const {
        status,
        areMetricsDisplayed,
        isMenuDisplayed,
      } = this.model.attributes;

      const scale = paperApi.getScale();
      const $box = this.$box;

      // Update width size. Remember that we have both SVG and HTML elements, so
      // we first update the SVG, than HTML
      this.model.resize(
        bBox.width,
        areMetricsDisplayed ? HEIGHT_WITH_METRICS : NORMAL_HEIGHT,
      );

      // Only updates the height
      bBox.height = areMetricsDisplayed ? HEIGHT_WITH_METRICS : NORMAL_HEIGHT;

      $box.css({
        transform: 'scale(' + scale.sx + ',' + scale.sy + ')',
        transformOrigin: '0 0',
        width: bBox.width / scale.sx,
        height: bBox.height / scale.sy,
        left: bBox.x,
        top: bBox.y,
      });

      const iconState = getIconState(status);
      const metricsData = areMetricsDisplayed ? getMetrics(displayMetrics) : '';

      const menuDisplayValue = isMenuDisplayed ? 'block' : 'none';

      $box.find('.status-value').text(status);
      $box.find('.display-name').text(displayName);

      $box
        .find('.connector-menu')
        .attr('style', `display: ${menuDisplayValue}`);

      $box.find('.metrics').html(metricsData);

      $box
        .find('.icon')
        .removeClass()
        .addClass(`icon ${iconState}`);
    },

    // Keeping this handler here since when calling `cell.remove()` somehow
    // triggers this method
    onRemove() {
      this.$box.remove();
    },
  });

  return new joint.shapes.html.Element({
    id: id ? id : undefined, // undefined -> id is controlled by JointJS
    name,
    kind,
    className,
    displayName,
    position,
    status,
    isTemporary,
    size: {
      width: 240,
      height: areMetricsDisplayed ? HEIGHT_WITH_METRICS : NORMAL_HEIGHT,
    },
    isMenuDisplayed: false,
    areMetricsDisplayed,
    jarKey,
  });
};

function getIcon(kind) {
  const sourceIcon = renderToString(<FlightTakeoffIcon color="action" />);
  const sinkIcon = renderToString(<FlightLandIcon color="action" />);
  const streamIcon = renderToString(<WavesIcon color="action" />);
  const { source, sink, stream } = KIND;

  if (kind === source) return sourceIcon;
  if (kind === sink) return sinkIcon;
  if (kind === stream) return streamIcon;
}

function getMetrics(metrics) {
  // Make sure we're getting
  // 1. Same metrics data every time by sorting
  // 2. And removing duplicate items
  // 3. Finally, just pick the values that need to be displayed
  const results = _.map(
    _.sortBy(_.uniqBy(metrics.meters, 'name'), 'name'),
    _.partialRight(_.pick, ['document', 'value']),
  );

  // The user will be able to choose two metrics items with our UI
  // in the future, but for now, we're picking the first two items
  // from the list
  const firstFieldName = _.get(results, '[0].document', '');
  const firstFieldValue = _.get(results, '[0].value', 0);
  const secondFieldName = _.get(results, '[1].document', '');
  const secondFieldValue = _.get(results, '[1].value', 0);

  return `
  <div class="field">
    <span class="field-name">${firstFieldName}</span>
    <span class="field-value">${firstFieldValue.toLocaleString()}</span>
  </div>
  <div class="field">
    <span class="field-name">${secondFieldName}</span>    
    <span class="field-value">${secondFieldValue.toLocaleString()}</span>
  `;
}

function getIconState(status) {
  const { stopped, pending, running, failed } = CELL_STATUS;
  const _status = status.toLowerCase();

  if (_status === stopped) return stopped;
  if (_status === pending) return pending;
  if (_status === running) return running;
  if (_status === failed) return failed;

  return stopped;
}

export default createConnectorCell;
