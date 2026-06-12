import { Fragment, useMemo, useRef, type CSSProperties } from 'react';
import {
  booleanOrNull,
  floatOrNull,
  parseColorOrNull,
  resolveDropdownOptions,
  resolveDynamicWidgets,
  resolveStyleRule,
  stringOrNull
} from './layoutUtils';
import type { JsonValue, StyleSheet, Widget } from './types';

interface RenderProps {
  widget: Widget;
  values: Record<string, JsonValue>;
  styleSheet?: StyleSheet | null;
  onValueChange: (id: string, value: string) => void;
  onAction: (action: string, args: JsonValue[]) => void;
  onModuleCommand: (moduleType: string, target: string, command: string, args: JsonValue[]) => void;
  parentDirection?: 'column' | 'row';
  focusRegistry?: Record<string, HTMLInputElement | null>;
}

function widgetBaseStyle(widget: Widget, style: StyleRule): CSSProperties {
  const textAlign = style.textAlign?.toLowerCase();
  const alignment = style.alignment?.toLowerCase();

  let alignSelf = undefined;
  if (alignment === 'start' || alignment === 'left') {
    alignSelf = 'flex-start';
  } else if (alignment === 'end' || alignment === 'right') {
    alignSelf = 'flex-end';
  } else if (alignment === 'center') {
    alignSelf = 'center';
  }

  return {
    width: widget.fillMaxWidth ? '100%' : undefined,
    height: widget.fillMaxSize ? '100%' : undefined,
    flex: widget.weight != null ? `${widget.weight} 1 0` : undefined,
    alignSelf,
    color: parseColorOrNull(style.textColor),
    background: parseColorOrNull(style.backgroundColor),
    borderRadius: style.cornerRadius ?? undefined,
    fontSize: style.fontSize ?? undefined,
    fontWeight: style.fontWeight ?? undefined,
    textAlign:
      textAlign === 'center' || textAlign === 'right' || textAlign === 'left' || textAlign === 'justify'
        ? textAlign
        : undefined,
    padding:
      style.padding != null
        ? style.padding
        : style.paddingVertical != null || style.paddingHorizontal != null
          ? `${style.paddingVertical ?? 0}px ${style.paddingHorizontal ?? 0}px`
          : undefined,
    boxSizing: 'border-box'
  };
}

function textFieldActionKey(widget: Widget): string {
  if (widget.type !== 'TextField') return 'default';
  return (widget.imeAction ?? 'default').trim().toLowerCase();
}

export function WidgetRenderer({
  widget,
  values,
  styleSheet,
  onValueChange,
  onAction,
  onModuleCommand,
  parentDirection = 'column',
  focusRegistry
}: RenderProps): JSX.Element | null {
  const style = resolveStyleRule(widget, styleSheet);
  const baseStyle = widgetBaseStyle(widget, style);
  const focusMap = focusRegistry ?? {};

  switch (widget.type) {
    case 'Column': {
      const children = widget.children ?? [];
      const dynamic = widget.dynamicChildrenId ? resolveDynamicWidgets(values[widget.dynamicChildrenId]) : [];
      const allChildren = [...children, ...dynamic];
      return (
        <div
          style={{
            ...baseStyle,
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            overflowY: widget.isScrollable ? 'auto' : undefined
          }}
        >
          {allChildren.map((child, index) => (
            <Fragment key={`${child.id ?? child.type}-${index}`}>
              <WidgetRenderer
                widget={child}
                values={values}
                styleSheet={styleSheet}
                onValueChange={onValueChange}
                onAction={onAction}
                onModuleCommand={onModuleCommand}
                parentDirection="column"
                focusRegistry={focusMap}
              />
            </Fragment>
          ))}
        </div>
      );
    }

    case 'Row': {
      const children = widget.children ?? [];
      const dynamic = widget.dynamicChildrenId ? resolveDynamicWidgets(values[widget.dynamicChildrenId]) : [];
      const allChildren = [...children, ...dynamic];
      return (
        <div
          style={{
            ...baseStyle,
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            gap: 8,
            overflowX: widget.isScrollable ? 'auto' : undefined
          }}
        >
          {allChildren.map((child, index) => (
            <Fragment key={`${child.id ?? child.type}-${index}`}>
              <WidgetRenderer
                widget={child}
                values={values}
                styleSheet={styleSheet}
                onValueChange={onValueChange}
                onAction={onAction}
                onModuleCommand={onModuleCommand}
                parentDirection="row"
                focusRegistry={focusMap}
              />
            </Fragment>
          ))}
        </div>
      );
    }

    case 'Text': {
      const widgetId = widget.id ?? undefined;
      const displayText = widgetId ? stringOrNull(values[widgetId]) ?? widget.text ?? '' : widget.text ?? '';

      const alignRaw = widget.align ?? style.textAlign ?? '';
      const textAlignMap: Record<string, any> = {
        'center': 'center',
        'right': 'right',
        'end': 'right',
        'start': 'left',
        'left': 'left',
        'justify': 'justify'
      };
      if (alignRaw) {
         baseStyle.textAlign = textAlignMap[alignRaw.toLowerCase()] ?? baseStyle.textAlign;
      }

      return <div style={baseStyle}>{displayText}</div>;
    }

    case 'TextField': {
      const widgetId = widget.id ?? undefined;
      const value = widgetId ? stringOrNull(values[widgetId]) ?? '' : '';
      const imeAction = textFieldActionKey(widget);
      return (
        <input
          type={widget.password ? 'password' : 'text'}
          ref={(el) => {
            if (widgetId) {
              focusMap[widgetId] = el;
            }
          }}
          style={{
            ...baseStyle,
            width: '100%',
            minHeight: 36,
            border: '1px solid #334155',
            borderRadius: 8,
            background: parseColorOrNull(style.containerColor) ?? '#111827',
            color: baseStyle.color ?? '#e5e7eb'
          }}
          value={value}
          placeholder={widget.hint ?? ''}
          onChange={(event) => {
            if (widgetId) {
              onValueChange(widgetId, event.target.value);
            }
          }}
          onKeyDown={(event) => {
            if (event.key !== 'Enter') return;
            if (imeAction === 'next' && widget.nextFocusId) {
              focusMap[widget.nextFocusId]?.focus();
              return;
            }
            const actionName = widget.onImeAction ?? widget.imeAction;
            if (actionName) {
              onAction(actionName, []);
            }
          }}
        />
      );
    }

    case 'Button': {
      return (
        <button
          style={{
            ...baseStyle,
            border: 'none',
            cursor: 'pointer',
            minHeight: 36,
            borderRadius: style.cornerRadius ?? 8,
            background: parseColorOrNull(style.containerColor ?? style.backgroundColor) ?? '#2563eb',
            color: parseColorOrNull(style.textColor) ?? '#ffffff',
            padding: '8px 12px'
          }}
          onClick={() => onAction(widget.action ?? '', widget.actionArgs ?? [])}
        >
          {widget.text ?? 'Button'}
        </button>
      );
    }

    case 'Image': {
      const imageModel = widget.data?.trim() ? widget.data : widget.url ?? '';
      if (!imageModel) return null;
      return (
        <img
          src={imageModel}
          alt={widget.description ?? 'image'}
          style={{
            ...baseStyle,
            width: widget.fillMaxWidth ? '100%' : 64,
            height: widget.fillMaxWidth ? 200 : 64,
            objectFit: 'contain'
          }}
        />
      );
    }

    case 'AudioPlayer': {
      const prefix = `${widget.player}.media`;
      const phase = stringOrNull(values[`${prefix}.state`]) ?? 'idle';
      const position = stringOrNull(values[`${prefix}.positionMs`]) ?? '0';
      return (
        <div style={{ ...baseStyle, display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div>{`${widget.title ?? 'Audio Player'}: ${phase}`}</div>
          <div>{`Position: ${position}ms`}</div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={() => onModuleCommand('audioPlayer', widget.player, 'play', [])}>Play</button>
            <button onClick={() => onModuleCommand('audioPlayer', widget.player, 'pause', [])}>Pause</button>
            <button onClick={() => onModuleCommand('audioPlayer', widget.player, 'stop', [])}>Stop</button>
          </div>
        </div>
      );
    }

    case 'Dropdown': {
      const options = resolveDropdownOptions(widget, values);
      const widgetId = widget.id ?? undefined;
      const selectedValue = widgetId ? stringOrNull(values[widgetId]) ?? '' : '';
      return (
        <select
          style={{
            ...baseStyle,
            minHeight: 36,
            borderRadius: 8,
            border: '1px solid #334155',
            background: parseColorOrNull(style.containerColor) ?? '#111827',
            color: baseStyle.color ?? '#e5e7eb'
          }}
          value={selectedValue}
          onChange={(event) => {
            const newValue = event.target.value;
            if (widgetId) {
              onValueChange(widgetId, newValue);
            }
            if (widget.onChangeAction) {
              onAction(widget.onChangeAction, [newValue]);
            }
          }}
        >
          <option value="">{widget.hint ?? 'Select'}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label ?? option.value}
            </option>
          ))}
        </select>
      );
    }

    case 'Switch': {
      const widgetId = widget.id ?? undefined;
      const checked = widgetId ? booleanOrNull(values[widgetId]) ?? (widget.checked ?? false) : widget.checked ?? false;
      const shouldGrow = widget.weight != null || widget.fillMaxWidth;
      return (
        <label
          style={{
            ...baseStyle,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
            flex: widget.weight != null ? `${widget.weight} 1 0` : baseStyle.flex
          }}
        >
          <span style={{ flex: shouldGrow ? 1 : 'none' }}>{widget.text ?? ''}</span>
          <input
            type="checkbox"
            checked={checked}
            onChange={(event) => {
              if (widgetId) {
                onValueChange(widgetId, String(event.target.checked));
              }
            }}
          />
        </label>
      );
    }

    case 'Spinner': {
      const visible = widget.visibilityId ? booleanOrNull(values[widget.visibilityId]) ?? true : true;
      if (!visible) return null;
      return <div className="spinner" style={baseStyle} />;
    }

    case 'Progress': {
      const progress = widget.progressId ? floatOrNull(values[widget.progressId]) ?? widget.progress : widget.progress;
      const clamped = typeof progress === 'number' ? Math.max(0, Math.min(1, progress)) : undefined;
      if (widget.indeterminate || clamped == null) {
        return <div className="progress-indeterminate" style={baseStyle} />;
      }
      return <progress style={{ ...baseStyle, width: '100%' }} value={clamped} max={1} />;
    }

    default:
      return parentDirection === 'row' ? <span /> : null;
  }
}

export function useFocusRegistry(): Record<string, HTMLInputElement | null> {
  return useRef<Record<string, HTMLInputElement | null>>({}).current;
}

export function useFrameworkLabel(framework: string | undefined): string {
  return useMemo(() => (framework?.trim() ? framework : 'Rikka'), [framework]);
}

