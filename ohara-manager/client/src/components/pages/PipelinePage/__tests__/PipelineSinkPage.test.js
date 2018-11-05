import React from 'react';
import { shallow } from 'enzyme';

import PipelineSinkPage from '../PipelineSinkPage';
import { getTestById } from 'utils/testHelpers';
import { CONFIGURATION } from 'constants/urls';

const props = {
  hasChanges: false,
  updateHasChanges: jest.fn(),
  updateGraph: jest.fn(),
  loadGraph: jest.fn(),
  match: {},
  schema: [],
  isRedirect: false,
};

describe('<PipelineSinkPage />', () => {
  let wrapper;

  beforeEach(() => {
    wrapper = shallow(<PipelineSinkPage {...props} />);
  });

  it('renders self', () => {
    expect(wrapper.length).toBe(1);
    // console.log(wrapper.debug());
  });

  it('renders <H5 /> ', () => {
    const h5 = wrapper.find('H5');
    expect(h5.length).toBe(1);
    expect(h5.children().text()).toBe('HDFS');
  });

  it('renders read from topic <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('read-from-topic'));
    const label = fromGroup.find('Label');
    const select = fromGroup.find('Select');
    const _props = select.props();

    expect(label.children().text()).toBe('Read from topic');
    expect(select.length).toBe(1);
    expect(select.name()).toBe('Select');
    expect(_props).toHaveProperty('isObject');
    expect(_props.name).toBe('topics');
    expect(_props.selected).toBe(wrapper.state().currTopic);
    expect(_props.width).toBe('250px');
    expect(_props.handleChange).toBeDefined();
  });

  it('renders hdfses <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('hdfses'));
    const label = fromGroup.find('Label');
    const select = fromGroup.find('Select');
    const _props = select.props();

    expect(label.children().text()).toBe('HDFS');
    expect(select.length).toBe(1);
    expect(select.name()).toBe('Select');
    expect(_props).toHaveProperty('isObject');
    expect(_props.name).toBe('hdfses');
    expect(_props.list).toEqual(wrapper.state().topics);
    expect(_props.selected).toEqual(wrapper.state().currTopic);
    expect(_props.width).toBe('250px');
    expect(_props.handleChange).toBeDefined();
  });

  it('renders write path <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('write-path'));
    const label = fromGroup.find('Label');
    const input = fromGroup.find('Input');
    const _props = input.props();

    expect(label.children().text()).toBe('Write path');
    expect(input.length).toBe(1);
    expect(input.name()).toBe('Input');
    expect(_props.name).toBe('writePath');
    expect(_props.width).toBe('250px');
    expect(_props.placeholder).toBe('file://');
    expect(_props.value).toEqual(wrapper.state().writePath);
    expect(_props.handleChange).toBeDefined();
  });

  it('renders temp directory <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('temp-directory'));
    const label = fromGroup.find('Label');
    const input = fromGroup.find('Input');
    const _props = input.props();

    expect(label.children().text()).toBe('Temp directory');
    expect(input.length).toBe(1);
    expect(input.name()).toBe('Input');
    expect(_props.name).toBe('tempDirectory');
    expect(_props.width).toBe('250px');
    expect(_props.placeholder).toBe('/tmp');
    expect(_props.value).toEqual(wrapper.state().tempDirectory);
    expect(_props.handleChange).toBeDefined();
  });

  it('renders include header <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('need-header'));
    const checkbox = fromGroup.find('Checkbox');
    const _props = checkbox.props();

    expect(checkbox.length).toBe(1);
    expect(checkbox.name()).toBe('Checkbox');
    expect(_props.name).toBe('needHeader');
    expect(_props.width).toBe('25px');
    expect(_props.value).toEqual('');
    expect(_props.handleChange).toBeDefined();
  });

  it('renders file encoding <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('file-encoding'));
    const label = fromGroup.find('Label');
    const select = fromGroup.find('Select');
    const _props = select.props();

    expect(label.children().text()).toBe('File encoding');
    expect(select.length).toBe(1);
    expect(select.name()).toBe('Select');
    expect(_props.name).toBe('fileEncoding');
    expect(_props.width).toBe('250px');
    expect(_props.list).toEqual(wrapper.state().fileEncodings);
    expect(_props.selected).toBe(wrapper.state().currFileEncoding);
    expect(_props.handleChange).toBeDefined();
  });

  it('renders rotate interval <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('rotate-interval'));
    const label = fromGroup.find('Label');
    const input = fromGroup.find('Input');
    const _props = input.props();

    expect(label.children().text()).toBe('Rotate interval (ms)');
    expect(input.length).toBe(1);
    expect(input.name()).toBe('Input');
    expect(_props.name).toBe('rotateInterval');
    expect(_props.width).toBe('250px');
    expect(_props.placeholder).toBe('60000');
    expect(_props.value).toEqual(wrapper.state().rotateInterval);
    expect(_props.handleChange).toBeDefined();
  });

  it('renders flush line count <FromGroup>', () => {
    const fromGroup = wrapper.find(getTestById('flush-line-count'));
    const label = fromGroup.find('Label');
    const input = fromGroup.find('Input');
    const _props = input.props();

    expect(label.children().text()).toBe('Flush line count');
    expect(input.length).toBe(1);
    expect(input.name()).toBe('Input');
    expect(_props.name).toBe('flushLineCount');
    expect(_props.width).toBe('250px');
    expect(_props.placeholder).toBe('10');
    expect(_props.value).toEqual(wrapper.state().flushLineCount);
    expect(_props.handleChange).toBeDefined();
  });

  it('should render <Redirect /> when this.state.isRedirect is true', () => {
    wrapper.setState({ isRedirect: true });

    expect(wrapper.name()).toBe('Redirect');
    expect(wrapper.props().to).toBe(CONFIGURATION);
  });
});
