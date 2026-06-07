export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

export interface StyleRule {
  textColor?: string | null;
  backgroundColor?: string | null;
  containerColor?: string | null;
  padding?: number | null;
  paddingHorizontal?: number | null;
  paddingVertical?: number | null;
  cornerRadius?: number | null;
  fontSize?: number | null;
  textAlign?: string | null;
}

export interface StyleTokens {
  accentColor?: string | null;
  destructiveColor?: string | null;
  values: Record<string, string>;
}

export interface StyleSheet {
  name?: string | null;
  scheme: string;
  tokens: StyleTokens;
  defaults: Record<string, StyleRule>;
  classes: Record<string, StyleRule>;
}

interface WidgetBase {
  type: string;
  id?: string | null;
  fillMaxWidth?: boolean;
  fillMaxSize?: boolean;
  weight?: number | null;
  classes?: string[];
}

export interface ColumnWidget extends WidgetBase {
  type: 'Column';
  children?: Widget[];
  dynamicChildrenId?: string | null;
  isScrollable?: boolean;
}

export interface RowWidget extends WidgetBase {
  type: 'Row';
  children?: Widget[];
  isScrollable?: boolean;
}

export interface TextWidget extends WidgetBase {
  type: 'Text';
  text?: string;
}

export interface TextFieldWidget extends WidgetBase {
  type: 'TextField';
  hint?: string;
  singleLine?: boolean;
  password?: boolean;
  imeAction?: string | null;
  onImeAction?: string | null;
  nextFocusId?: string | null;
}

export interface ButtonWidget extends WidgetBase {
  type: 'Button';
  text?: string;
  action?: string;
  actionArgs?: JsonValue[];
}

export interface ImageWidget extends WidgetBase {
  type: 'Image';
  url?: string;
  data?: string | null;
  description?: string;
}

export interface AudioPlayerWidget extends WidgetBase {
  type: 'AudioPlayer';
  player: string;
  title?: string;
}

export interface DropdownOption {
  value: string;
  label?: string;
}

export interface DropdownWidget extends WidgetBase {
  type: 'Dropdown';
  hint?: string;
  options?: DropdownOption[];
  optionsValueId?: string | null;
  onChangeAction?: string | null;
}

export interface SwitchWidget extends WidgetBase {
  type: 'Switch';
  text?: string;
  checked?: boolean;
}

export interface SpinnerWidget extends WidgetBase {
  type: 'Spinner';
  visibilityId?: string | null;
}

export interface ProgressWidget extends WidgetBase {
  type: 'Progress';
  progress?: number | null;
  progressId?: string | null;
  indeterminate?: boolean;
}

export type Widget =
  | ColumnWidget
  | RowWidget
  | TextWidget
  | TextFieldWidget
  | ButtonWidget
  | ImageWidget
  | AudioPlayerWidget
  | DropdownWidget
  | SwitchWidget
  | SpinnerWidget
  | ProgressWidget;

export interface LayoutRoot {
  layout: Widget;
}

export interface HeadlessSessionSnapshot {
  sessionId: string;
  reason: string;
  featureId?: string;
  values: Record<string, JsonValue>;
  layout?: LayoutRoot | null;
  styleSheet?: StyleSheet | null;
  framework?: string;
}

export interface CreateSessionResponse {
  id: string;
  manifestPath: string;
  state: HeadlessSessionSnapshot;
}


