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
  fontWeight?: string | null;
  textAlign?: string | null;
  alignment?: string | null;
  iconPosition?: string | null;
  iconOnly?: boolean | null;
  noBreak?: boolean | null;
  noWrap?: boolean | null;
  whiteSpace?: string | null;
  maxLines?: number | null;
  overflow?: string | null;
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
  visibilityId?: string | null;
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
  dynamicChildrenId?: string | null;
  isScrollable?: boolean;
}

export interface TextWidget extends WidgetBase {
  type: 'Text';
  text?: string;
  align?: string | null;
  noBreak?: boolean | null;
  maxLines?: number | null;
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
  icon?: string | null;
  action?: string;
  actionArgs?: JsonValue[];
  noBreak?: boolean | null;
  maxLines?: number | null;
}

export interface ImageWidget extends WidgetBase {
  type: 'Image';
  url?: string;
  data?: string | null;
  description?: string;
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
  onChangeAction?: string | null;
  actionArgs?: JsonValue[];
}

export interface SpinnerWidget extends WidgetBase {
  type: 'Spinner';
}

export interface ProgressWidget extends WidgetBase {
  type: 'Progress';
  progress?: number | null;
  progressId?: string | null;
  indeterminate?: boolean;
}

export interface SliderWidget extends WidgetBase {
  type: 'Slider';
  value?: number | null;
  min?: number;
  max?: number;
  step?: number | null;
  onChangeAction?: string | null;
  actionArgs?: JsonValue[];
}

export interface TableColumn {
  weight?: number | null;
  width?: number | null;
}

export interface TableWidget extends WidgetBase {
  type: 'Table';
  children?: Widget[];
  rows?: Widget[];
  dynamicChildrenId?: string | null;
  dynamicRowsId?: string | null;
  isScrollable?: boolean;
  scrollDirection?: string | null;
  columns?: TableColumn[];
}

export interface TableRowWidget extends WidgetBase {
  type: 'TableRow';
  children?: Widget[];
}

export type Widget =
  | ColumnWidget
  | RowWidget
  | TextWidget
  | TextFieldWidget
  | ButtonWidget
  | ImageWidget
  | DropdownWidget
  | SwitchWidget
  | SpinnerWidget
  | ProgressWidget
  | SliderWidget
  | TableWidget
  | TableRowWidget;

export interface LayoutRoot {
  layout: Widget;
}

export interface AppletFeatureDef {
  name?: string;
  layout?: string;
  layouts?: Record<string, string>;
  script?: string;
  modules?: Array<{
    type: string;
    name: string;
    script?: string;
  }>;
}

export interface AppletManifest {
  id: string;
  name: string;
  description?: string;
  version?: string;
  entryFeature: string;
  defaultStyle?: string;
  features: Record<string, AppletFeatureDef>;
  styles?: Record<string, {
    name?: string;
    file: string;
  }>;
}

export interface AppletBundle {
  manifest: AppletManifest;
  styles?: Record<string, StyleSheet>;
  layouts?: Record<string, LayoutRoot>;
  scripts?: Record<string, string>;
  modules?: Record<string, string>;
}

export interface AppletSessionSnapshot {
  sessionId: string;
  appletId: string;
  appletName: string;
  featureId: string;
  layout: LayoutRoot | null;
  values: Record<string, JsonValue>;
  styleSheet?: StyleSheet | null;
  isLoading: boolean;
  error?: string | null;
}
