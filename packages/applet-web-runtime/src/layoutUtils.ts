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
  if (!/^[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/.test(hex)) {
    return raw; // Allow css named colors or rgb/rgba
  }
  if (hex.length === 6) return `#${hex}`;
  const a = parseInt(hex.slice(0, 2), 16) / 255;
  const r = parseInt(hex.slice(2, 4), 16);
  const g = parseInt(hex.slice(4, 6), 16);
  const b = parseInt(hex.slice(6, 8), 16);
  return `rgba(${r}, ${g}, ${b}, ${a.toFixed(3)})`;
}

function mergedWith(base: StyleRule, other?: StyleRule | null): StyleRule {
  if (!other) return base;
  const effectiveBg = other.backgroundColor ?? other.containerColor ?? base.backgroundColor ?? base.containerColor;
  return {
    textColor: other.textColor ?? base.textColor,
    backgroundColor: effectiveBg,
    containerColor: other.containerColor ?? base.containerColor,
    padding: other.padding ?? base.padding,
    paddingHorizontal: other.paddingHorizontal ?? base.paddingHorizontal,
    paddingVertical: other.paddingVertical ?? base.paddingVertical,
    cornerRadius: other.cornerRadius ?? base.cornerRadius,
    fontSize: other.fontSize ?? base.fontSize,
    fontWeight: other.fontWeight ?? base.fontWeight,
    textAlign: other.textAlign ?? base.textAlign,
    alignment: other.alignment ?? base.alignment,
    iconPosition: other.iconPosition ?? base.iconPosition,
    iconOnly: other.iconOnly ?? base.iconOnly,
    noBreak: other.noBreak ?? base.noBreak,
    noWrap: other.noWrap ?? base.noWrap,
    whiteSpace: other.whiteSpace ?? base.whiteSpace,
    maxLines: other.maxLines ?? base.maxLines,
    overflow: other.overflow ?? base.overflow
  };
}

export function resolveStyleRule(widget: Widget, styleSheet?: StyleSheet | null): StyleRule {
  if (!styleSheet) return {};
  let merged: StyleRule = {};
  if (styleSheet.defaults && styleSheet.defaults[widget.type]) {
    merged = mergedWith(merged, styleSheet.defaults[widget.type]);
  }
  for (const clazz of widget.classes ?? []) {
    if (styleSheet.classes && styleSheet.classes[clazz]) {
      merged = mergedWith(merged, styleSheet.classes[clazz]);
    }
  }
  return merged;
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

export function resolveDropdownOptions(widget: DropdownWidget, values: Record<string, JsonValue>): DropdownOption[] {
  const valueId = widget.optionsValueId;
  if (!valueId) return widget.options ?? [];

  const raw = values[valueId];
  if (Array.isArray(raw)) {
    return raw.map((item) => {
      if (typeof item === 'string') return { value: item, label: item };
      if (isObject(item) && typeof item.value === 'string') {
        return { value: item.value, label: typeof item.label === 'string' ? item.label : item.value };
      }
      return { value: String(item), label: String(item) };
    });
  }
  return widget.options ?? [];
}
