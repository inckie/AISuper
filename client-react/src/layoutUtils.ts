import type {
  DropdownOption,
  DropdownWidget,
  JsonValue,
  StyleRule,
  StyleSheet,
  Widget
} from './types';

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isWidget(value: unknown): value is Widget {
  return isObject(value) && typeof value.type === 'string';
}

export function stringOrNull(value: JsonValue | undefined): string | null {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return null;
}

export function booleanOrNull(value: JsonValue | undefined): boolean | null {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
  }
  return null;
}

export function floatOrNull(value: JsonValue | undefined): number | null {
  if (typeof value === 'number') return value;
  if (typeof value === 'string') {
    const parsed = Number(value.trim());
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export function parseColorOrNull(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  if (raw.trim().toLowerCase() === 'transparent') return 'transparent';
  const hex = raw.startsWith('#') ? raw.slice(1) : raw;
  if (!/^[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/.test(hex)) return undefined;
  if (hex.length === 6) return `#${hex}`;
  const a = parseInt(hex.slice(0, 2), 16) / 255;
  const r = parseInt(hex.slice(2, 4), 16);
  const g = parseInt(hex.slice(4, 6), 16);
  const b = parseInt(hex.slice(6, 8), 16);
  return `rgba(${r}, ${g}, ${b}, ${a.toFixed(3)})`;
}

function mergedWith(base: StyleRule, other?: StyleRule | null): StyleRule {
  if (!other) return base;
  return {
    textColor: other.textColor ?? base.textColor,
    backgroundColor: other.backgroundColor ?? base.backgroundColor,
    containerColor: other.containerColor ?? base.containerColor,
    padding: other.padding ?? base.padding,
    paddingHorizontal: other.paddingHorizontal ?? base.paddingHorizontal,
    paddingVertical: other.paddingVertical ?? base.paddingVertical,
    cornerRadius: other.cornerRadius ?? base.cornerRadius,
    fontSize: other.fontSize ?? base.fontSize,
    textAlign: other.textAlign ?? base.textAlign
  };
}

function applyTokenFallbacks(widget: Widget, rule: StyleRule, sheet: StyleSheet): StyleRule {
  const needsActionColors = widget.type === 'Button' || widget.type === 'Dropdown' || widget.type === 'Switch';
  if (!needsActionColors) return rule;
  if (rule.containerColor && rule.textColor) return rule;

  const classes = widget.classes ?? [];
  const hasDestructiveClass = classes.some((name) => {
    const normalized = name.toLowerCase();
    return normalized === 'destructive' || normalized === 'danger' || normalized === 'delete';
  });

  const token = hasDestructiveClass
    ? sheet.tokens.destructiveColor ?? sheet.tokens.values.destructiveColor
    : sheet.tokens.accentColor ?? sheet.tokens.values.accentColor;

  if (!token) return rule;
  return { ...rule, containerColor: rule.containerColor ?? token };
}

export function resolveStyleRule(widget: Widget, styleSheet?: StyleSheet | null): StyleRule {
  if (!styleSheet) return {};
  let merged: StyleRule = {};
  merged = mergedWith(merged, styleSheet.defaults[widget.type]);
  for (const clazz of widget.classes ?? []) {
    merged = mergedWith(merged, styleSheet.classes[clazz]);
  }
  return applyTokenFallbacks(widget, merged, styleSheet);
}

export function resolveDynamicWidgets(value: JsonValue | undefined): Widget[] {
  if (!value) return [];
  if (Array.isArray(value)) {
    return (value as unknown[]).filter(isWidget);
  }
  if (typeof value === 'string') {
    try {
      const parsed: unknown = JSON.parse(value);
      if (Array.isArray(parsed)) {
        return (parsed as unknown[]).filter(isWidget);
      }
    } catch {
      return [];
    }
  }
  return [];
}

function parseDropdownOptions(array: JsonValue[]): DropdownOption[] {
  const options: DropdownOption[] = [];
  for (const item of array) {
    if (typeof item === 'string' && item.trim()) {
      options.push({ value: item, label: item });
      continue;
    }
    if (isObject(item) && typeof item.value === 'string' && item.value.trim()) {
      options.push({ value: item.value, label: typeof item.label === 'string' ? item.label : item.value });
    }
  }
  return options;
}

export function resolveDropdownOptions(widget: DropdownWidget, values: Record<string, JsonValue>): DropdownOption[] {
  const valueId = widget.optionsValueId;
  if (!valueId) return widget.options ?? [];

  const raw = values[valueId];
  if (Array.isArray(raw)) {
    const fromValues = parseDropdownOptions(raw);
    return fromValues.length ? fromValues : widget.options ?? [];
  }
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw) as JsonValue;
      if (Array.isArray(parsed)) {
        const fromValues = parseDropdownOptions(parsed);
        return fromValues.length ? fromValues : widget.options ?? [];
      }
    } catch {
      return widget.options ?? [];
    }
  }
  return widget.options ?? [];
}

export function parseJsonInput(input: string): JsonValue {
  const trimmed = input.trim();
  if (!trimmed) return '';
  try {
    return JSON.parse(trimmed) as JsonValue;
  } catch {
    return input;
  }
}

